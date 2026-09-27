package dev.sampple.rambler.s3;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;

/** One asynchronous, single-use dictation session. Callbacks run on the relevant S3 reader thread. */
public final class S3Session {
    public interface Listener {
        default void onPartial(String text, boolean stable) { }
        default void onFinal(String text) { }
        /** intent: 0=dictation, 2=edit history, 3=rewrite; editedHistory may be null. */
        default void onEdit(int intent, String fullTranscript, String editedHistory) { }
        default void onError(Throwable error) { }
        default void onClosed() { }
        /** Every complete response protobuf, before decoding; diagnostics may use S3Debug.tree(data). */
        default void onRawResponse(byte[] data) { }
        /** Source is "jetson" or "interim"; defaults to the original raw callback. */
        default void onRawResponse(String source, byte[] data) { onRawResponse(data); }
        /** Optional interim stream failed; Jetson may still deliver an edit/final. */
        default void onInterimError(Throwable error) { }
    }
    public static final class Config {
        public String host = "speechs3proto2-pa.googleapis.com";
        public int port = 443;
        public String language = "ko-KR";
        /** UserInfo.secondary_languages, field 4; shared by Jetson and interim. */
        public String[] additionalLanguages = new String[0];
        public String surroundingText;
        public String routingKey = "gboard_gemini_v3_streaming_voice_edit_mul";
        /** Jetson edits at utterance end. Standard "recognizer" emits interim ASR text. */
        public String recognizerId = "beyond-a2a-recognizer";
        /** Also run a standard S3 recognizer over the same audio for live onPartial callbacks. */
        public boolean livePartials = true;
        public String buildDisplay = "unknown";
        public String appVersion = "18.3";
        public String model = "unknown";
        /** Package of the app being typed into (ClientInfo.trigger_package, as Gboard sends). */
        public String triggerPackage;
        /** Gboard's X-Goog-Api-Key. Not shipped in source: injected at build time or entered in settings. */
        public String apiKey = "";
        public String androidPackage = "com.google.android.googlequicksearchbox";
        public String androidCert = "58E1C4133F7441EC3D2C270270A14802DA47BA0E";
    }
    private final Config config;
    private final Listener listener;
    private final Object lock = new Object();
    private final ArrayDeque<byte[]> queued = new ArrayDeque<>();
    private boolean started, finishing, terminated;
    private volatile boolean cancelled;
    private int queuedBytes;
    private volatile Http2Grpc transport;
    private volatile Http2Grpc interimTransport;
    private final long createdAt = System.currentTimeMillis();
    public S3Session(Config config, Listener listener) {
        this.config = Objects.requireNonNull(config); this.listener = Objects.requireNonNull(listener);
    }
    public void start() {
        synchronized (lock) {
            if (started) throw new IllegalStateException("session already started");
            started = true;
        }
        Thread thread = new Thread(this::run, "rambler-s3-session"); thread.setDaemon(true); thread.start();
    }
    /** Enqueues arbitrary chunks of the continuous Ogg stream; bounded to 2 MiB. */
    public void sendAudio(byte[] oggBytes) {
        Objects.requireNonNull(oggBytes);
        synchronized (lock) {
            if (!started || finishing || cancelled || terminated) throw new IllegalStateException("session not accepting audio");
            if (queuedBytes + oggBytes.length > 2_000_000) throw new IllegalStateException("audio queue full");
            byte[] copy = oggBytes.clone(); queued.add(copy); queuedBytes += copy.length; lock.notifyAll();
        }
    }
    public void finish() {
        synchronized (lock) {
            if (!started || cancelled || terminated) return;
            finishing = true; lock.notifyAll();
        }
    }
    public void cancel() {
        synchronized (lock) {
            if (terminated) return;
            cancelled = true; queued.clear(); queuedBytes = 0; lock.notifyAll();
        }
        Http2Grpc t = transport; if (t != null) t.close();
        Http2Grpc interim = interimTransport; if (interim != null) interim.close();
    }
    private void run() {
        try (Http2Grpc t = new Http2Grpc(config, data -> {
            listener.onRawResponse("jetson", data);
            S3Messages.response(data, listener);
        })) {
            transport = t;
            if (cancelled) return;
            t.connect();
            if (cancelled) return;
            t.send(S3Messages.header(config), false);
            if (config.livePartials && "beyond-a2a-recognizer".equals(config.recognizerId)) {
                // This is a second independent S3Bidi stream, not a Jetson header flag.
                // Its partials are provisional; Jetson remains authoritative for edits/finals.
                Http2Grpc interim = new Http2Grpc(config, data -> {
                    listener.onRawResponse("interim", data);
                    S3Messages.response(data, new Listener() {
                        @Override public void onPartial(String text, boolean stable) {
                            if (!cancelled) listener.onPartial(text, stable);
                        }
                    });
                });
                interimTransport = interim;
                try {
                    interim.connect();
                    if (cancelled) return;
                    interim.send(S3Messages.header(config, "recognizer"), false);
                    Thread interimReader = new Thread(() -> readInterim(interim), "rambler-s3-interim");
                    interimReader.setDaemon(true); interimReader.start();
                } catch (Exception e) {
                    interim.close(); interimTransport = null;
                    if (!cancelled) listener.onInterimError(new IOException("interim S3 stream unavailable (Jetson continues)", e));
                }
            }
            Thread sender = new Thread(() -> sendLoop(t), "rambler-s3-audio"); sender.setDaemon(true); sender.start();
            t.read();
        } catch (Exception e) {
            if (!cancelled) listener.onError(e);
        } finally {
            synchronized (lock) { terminated = true; cancelled = true; queued.clear(); queuedBytes = 0; lock.notifyAll(); }
            if (transport != null) transport.close();
            Http2Grpc interim = interimTransport; if (interim != null) interim.close();
            listener.onClosed();
        }
    }
    private void readInterim(Http2Grpc interim) {
        try { interim.read(); }
        catch (Exception e) {
            if (!cancelled) listener.onInterimError(new IOException("interim S3 stream failed (Jetson continues)", e));
        } finally {
            interim.close();
            if (interimTransport == interim) interimTransport = null;
        }
    }
    private void sendLoop(Http2Grpc t) {
        try {
            while (true) {
                byte[] audio;
                synchronized (lock) {
                    while (queued.isEmpty() && !finishing && !cancelled)
                        lock.wait();
                    if (cancelled) return;
                    audio = queued.poll();
                    if (audio != null) queuedBytes -= audio.length;
                    else if (finishing) break;
                }
                if (audio != null) {
                    int p = 0;
                    while (p < audio.length) {
                        int n = Math.min(224, audio.length - p);
                        byte[] chunk = new byte[n]; System.arraycopy(audio, p, chunk, 0, n); p += n;
                        byte[] request = S3Messages.audio(chunk, System.currentTimeMillis() - createdAt);
                        t.send(request, false);
                        Http2Grpc interim = interimTransport;
                        if (interim != null) {
                            try { interim.send(request, false); }
                            catch (IOException e) {
                                interim.close(); interimTransport = null;
                                if (!cancelled) listener.onInterimError(e);
                            }
                        }
                    }
                }
            }
            t.send(S3Messages.end(), true);
            Http2Grpc interim = interimTransport;
            if (interim != null) {
                try { interim.send(S3Messages.end(), true); }
                catch (IOException e) {
                                interim.close(); interimTransport = null;
                                if (!cancelled) listener.onInterimError(e);
                            }
            }
        } catch (Exception e) {
            if (!cancelled) { listener.onError(e); t.close(); }
        }
    }
}
