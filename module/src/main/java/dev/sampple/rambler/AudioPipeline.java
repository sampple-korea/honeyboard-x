package dev.sampple.rambler;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Single-use capture/encoder. Native resources belong exclusively to its worker thread. */
final class AudioPipeline {
    interface Listener {
        // Worker callbacks: the controller must marshal these to its main-thread state machine.
        void onPacket(byte[] packet, int preSkip);
        void onLevel(float rms);
        void onError(Throwable error);
        void onStopped(boolean drained);
    }

    private static final int RATE = 16000, SAMPLES = 320, BYTES = SAMPLES * 2;
    // A new controller may be shown before the previous worker has released AudioRecord.
    private static final java.util.concurrent.ExecutorService WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "rambler-opus"); t.setDaemon(true); return t;
            });
    private final Listener listener;
    private volatile boolean stopRequested, abortRequested;
    private boolean started;
    // All fields below are worker-only.
    private MediaCodec codec;
    private AudioRecord record;
    private boolean codecStarted, recordStarted;
    private Integer preSkip;
    private boolean emittedPacket;
    private boolean outputEos;

    AudioPipeline(Listener listener) { this.listener = listener; }

    /** Call on main. Never waits for an AudioRecord or codec operation. */
    void start() {
        if (started) throw new IllegalStateException("audio pipeline is single use");
        started = true;
        WORKER.execute(this::run);
    }

    /** Drain on pause; abort on close, stale editor, transport end or error. */
    void stop(boolean drain) {
        if (!drain) abortRequested = true;
        stopRequested = true;
    }

    private void run() {
        boolean drained = false;
        try {
            if (abortRequested) return;
            int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("16 kHz microphone unavailable");
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum, BYTES * 8));
            if (record.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("microphone initialization failed");
            codec = MediaCodec.createEncoderByType("audio/opus");
            MediaFormat format = MediaFormat.createAudioFormat("audio/opus", RATE, 1);
            // Gboard's native encoder uses 24 kbps; the platform encoder gets a little more
            // headroom and maximum complexity to offset its weaker defaults.
            format.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
            format.setInteger(MediaFormat.KEY_COMPLEXITY, 10);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BYTES);
            // Android's Opus encoder uses 20 ms. Check the actual packet TOC as well:
            // OggOpusWriter advances exactly 960 samples in its 48 kHz granule clock.
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start(); codecStarted = true;
            if (abortRequested) return;
            record.startRecording(); recordStarted = true;
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("microphone did not start");

            short[] pcm = new short[SAMPLES];
            int filled = 0;
            long frame = 0, lastProgress = SystemClock.elapsedRealtime();
            while (!stopRequested) {
                int n = record.read(pcm, filled, SAMPLES - filled, AudioRecord.READ_NON_BLOCKING);
                if (n < 0) throw new IllegalStateException("microphone read failed: " + n);
                if (n > 0) { filled += n; lastProgress = SystemClock.elapsedRealtime(); }
                drainOutput();
                if (filled == SAMPLES) {
                    queuePcm(pcm, frame++ * 20000L);
                    if (frame % 3 == 0) {
                        double power = 0;
                        for (short value : pcm) power += (double)value * value;
                        float level = (float)Math.min(1, Math.sqrt(power / SAMPLES) / 32768.0);
                        listener.onLevel(level);
                    }
                    filled = 0;
                } else {
                    if (SystemClock.elapsedRealtime() - lastProgress > 5000)
                        throw new IllegalStateException("microphone stalled");
                    Thread.sleep(4);
                }
            }
            stopRecord();
            if (abortRequested) return;
            if (filled > 0) {
                Arrays.fill(pcm, filled, SAMPLES, (short)0);
                queuePcm(pcm, frame++ * 20000L);
            }
            // Silence supplies encoder lookahead; padding is limited to one 20 ms frame.
            // The existing writer has no last-granule trimming API.
            Arrays.fill(pcm, (short)0);
            queuePcm(pcm, frame++ * 20000L);
            int input = awaitInput();
            if (abortRequested) return;
            codec.queueInputBuffer(input, 0, 0, frame * 20000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            long deadline = SystemClock.elapsedRealtime() + 3000;
            while (!outputEos && !abortRequested) {
                drainOutput();
                if (SystemClock.elapsedRealtime() > deadline)
                    throw new IllegalStateException("Opus EOS timeout");
                if (!outputEos) Thread.sleep(2);
            }
            if (!abortRequested) {
                if (!emittedPacket)
                    throw new IllegalStateException("Opus encoder produced no audio");
                drained = true;
            }
        } catch (Throwable error) {
            log("capture/encode", error);
            if (!abortRequested) {
                try { listener.onError(error); } catch (Throwable nested) { log("error callback", nested); }
            }
        } finally {
            stopRecord();
            if (record != null) {
                try { record.release(); } catch (Throwable error) { log("release microphone", error); }
                record = null;
            }
            if (codec != null) {
                if (codecStarted) {
                    try { codec.stop(); } catch (Throwable error) { log("stop encoder", error); }
                }
                try { codec.release(); } catch (Throwable error) { log("release encoder", error); }
                codec = null;
            }
            try { listener.onStopped(drained); } catch (Throwable error) { log("stopped callback", error); }
        }
    }

    private void stopRecord() {
        if (record != null && recordStarted) {
            recordStarted = false;
            try { record.stop(); } catch (Throwable error) { log("stop microphone", error); }
        }
    }

    private int awaitInput() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 3000;
        while (!abortRequested) {
            int input = codec.dequeueInputBuffer(10000);
            if (input >= 0) return input;
            drainOutput();
            if (SystemClock.elapsedRealtime() > deadline)
                throw new IllegalStateException("Opus input timeout");
        }
        return -1;
    }

    private void queuePcm(short[] pcm, long pts) throws Exception {
        int input = awaitInput();
        if (abortRequested) return;
        ByteBuffer b = codec.getInputBuffer(input);
        if (b == null || b.capacity() < BYTES) throw new IllegalStateException("short Opus input buffer");
        b.clear(); b.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : pcm) b.putShort(sample);
        codec.queueInputBuffer(input, 0, BYTES, pts, 0);
        drainOutput();
    }

    private void drainOutput() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        // Bounded even for an encoder with unexpected output behavior.
        for (int count = 0; count < 64 && !abortRequested; count++) {
            int output = codec.dequeueOutputBuffer(info, 0);
            if (output == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = codec.getOutputFormat();
                ByteBuffer csd = format.getByteBuffer("csd-0");
                if (csd != null) readCsd(copy(csd));
                continue;
            }
            if (output < 0) continue;
            try {
                if (info.size > 0) {
                    ByteBuffer b = codec.getOutputBuffer(output);
                    if (b == null) throw new IllegalStateException("missing Opus output");
                    b = b.duplicate(); b.position(info.offset); b.limit(info.offset + info.size);
                    byte[] bytes = copy(b);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) readCsd(bytes);
                    else acceptPacket(bytes);
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
            } finally { codec.releaseOutputBuffer(output, false); }
            if (outputEos) return;
        }
    }

    private void readCsd(byte[] bytes) {
        // csd-0 can be raw OpusHead or Android's AOPUSHDR/AOPUSDLY/AOPUSPRL container.
        int offset = 0;
        if (magic(bytes, 0, "AOPUSHDR")) {
            if (bytes.length < 16) throw new IllegalStateException("truncated Android Opus CSD");
            long size = ByteBuffer.wrap(bytes, 8, 8).order(ByteOrder.nativeOrder()).getLong();
            if (size < 19 || size > bytes.length - 16)
                throw new IllegalStateException("invalid Android Opus header size");
            offset = 16;
        }
        if (!magic(bytes, offset, "OpusHead") || bytes.length - offset < 19)
            throw new IllegalStateException("missing OpusHead in encoder CSD");
        if ((bytes[offset + 8] & 255) != 1 || bytes[offset + 9] != 1 || bytes[offset + 18] != 0)
            throw new IllegalStateException("unsupported Opus header");
        int delay = (bytes[offset + 10] & 255) | ((bytes[offset + 11] & 255) << 8);
        if (preSkip != null && preSkip != delay)
            throw new IllegalStateException("Opus encoder delay changed midstream");
        preSkip = delay;
    }

    private void acceptPacket(byte[] packet) {
        if (packetSamples(packet) != 960) throw new IllegalStateException("Opus output is not 20 ms");
        if (preSkip == null) throw new IllegalStateException("Opus encoder omitted CSD");
        // Each transport rollover starts a new Ogg logical stream over this encoder.
        // Its OpusHead needs the encoder delay even if this is not the first packet.
        listener.onPacket(packet, preSkip);
        emittedPacket = true;
    }

    private static int packetSamples(byte[] p) {
        if (p.length == 0) return 0;
        int toc = p[0] & 255, config = toc >>> 3;
        int samples = config >= 16 ? 120 << (config & 3)
                : config >= 12 ? 480 << (config & 1)
                : (config & 3) == 3 ? 2880 : 480 << (config & 3);
        int code = toc & 3;
        int frames = code == 0 ? 1 : code < 3 ? 2 : p.length > 1 ? p[1] & 63 : 0;
        return samples * frames;
    }

    private static boolean magic(byte[] bytes, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || bytes.length - offset < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[offset + i] != expected[i]) return false;
        return true;
    }

    private static byte[] copy(ByteBuffer source) {
        ByteBuffer b = source.duplicate();
        byte[] result = new byte[b.remaining()]; b.get(result); return result;
    }

    private static void log(String operation, Throwable error) {
        try { Log.e("RamblerAudio", operation, error); } catch (Throwable ignored) { }
    }
}
