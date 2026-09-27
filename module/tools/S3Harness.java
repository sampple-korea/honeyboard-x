import dev.sampple.rambler.s3.OggOpusWriter;
import dev.sampple.rambler.s3.S3Debug;
import dev.sampple.rambler.s3.S3Session;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** JVM integration test: repacketize opusenc Ogg frames and send them at audio speed. */
public final class S3Harness {
    static final class Audio { int preSkip; List<byte[]> packets = new ArrayList<>(); }
    static Audio readOpus(Path path) throws IOException {
        byte[] b = Files.readAllBytes(path);
        Audio result = new Audio(); int p = 0; ByteArrayOutputStream packet = new ByteArrayOutputStream();
        List<byte[]> all = new ArrayList<>();
        while (p + 27 <= b.length) {
            if (b[p] != 'O' || b[p+1] != 'g' || b[p+2] != 'g' || b[p+3] != 'S') throw new IOException("bad Ogg page");
            int segments = b[p+26] & 255; if (p+27+segments > b.length) throw new IOException("bad lacing table");
            int body = p+27+segments;
            for (int i = 0; i < segments; i++) {
                int size = b[p+27+i] & 255;
                if (body+size > b.length) throw new IOException("truncated Ogg packet");
                packet.write(b, body, size); body += size;
                if (size < 255) { all.add(packet.toByteArray()); packet.reset(); }
            }
            p = body;
        }
        if (p != b.length || packet.size() != 0 || all.size() < 3) throw new IOException("incomplete Ogg stream");
        byte[] head = all.get(0);
        if (!new String(head, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).equals("OpusHead")) throw new IOException("no OpusHead");
        result.preSkip = (head[10] & 255) | ((head[11] & 255) << 8);
        result.packets.addAll(all.subList(2, all.size())); return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("S3Harness <en-US|ko-KR> <audio.opus>");
        Audio audio = readOpus(Path.of(args[1]));
        System.out.println("Opus packets=" + audio.packets.size() + " preSkip=" + audio.preSkip);
        S3Session.Config config = new S3Session.Config(); config.apiKey = System.getenv().getOrDefault("GOOGLE_API_KEY", ""); if (config.apiKey.isEmpty()) throw new IllegalStateException("set GOOGLE_API_KEY"); config.language = args[0]; String trigger = System.getProperty("s3.trigger", ""); config.triggerPackage = trigger.isEmpty() ? null : trigger;
        if (System.getProperty("s3.recognizer") != null) config.recognizerId = System.getProperty("s3.recognizer");
        if (System.getProperty("s3.routing") != null) config.routingKey = System.getProperty("s3.routing");
        if (System.getProperty("s3.livePartials") != null) config.livePartials = Boolean.parseBoolean(System.getProperty("s3.livePartials"));
        config.additionalLanguages = System.getProperty("s3.additional", "").split(",");
        String historyFile = System.getProperty("s3.historyFile", "");
        if (!historyFile.isEmpty()) config.surroundingText = Files.readString(Path.of(historyFile));
        System.out.println("Primary=" + config.language + " additional=" + java.util.Arrays.toString(config.additionalLanguages)
                + " historyChars=" + (config.surroundingText == null ? 0 : config.surroundingText.length()));
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean received = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        long started = System.nanoTime();
        boolean dump = Boolean.getBoolean("s3.dump");
        S3Session session = new S3Session(config, new S3Session.Listener() {
            private String time() { return String.format("+%.3fs ", (System.nanoTime() - started) / 1e9); }
            @Override public void onRawResponse(String source, byte[] data) {
                if (!dump) return;
                try { System.out.print(time() + "RAW " + source + " bytes=" + data.length + "\n" + S3Debug.tree(data)); }
                catch (IOException e) { System.err.println(time() + "RAW decode error " + e); }
            }
            @Override public void onPartial(String text, boolean stable) { System.out.println(time() + "PARTIAL stable=" + stable + " " + text); }
            @Override public void onFinal(String text) { received.set(true); System.out.println(time() + "FINAL " + text); }
            @Override public void onEdit(int intent, String full, String history) {
                System.out.println(time() + "EDIT intent=" + intent + " full=" + full + " history=" + history);
            }
            @Override public void onError(Throwable e) { failure.set(e); System.err.println(time() + "ERROR " + e); }
            @Override public void onInterimError(Throwable e) { System.err.println(time() + "INTERIM ERROR " + e); }
            @Override public void onClosed() { System.out.println(time() + "CLOSED"); done.countDown(); }
        });
        session.start();
        OggOpusWriter writer = new OggOpusWriter(audio.preSkip);
        session.sendAudio(writer.headers());
        long start = System.nanoTime();
        for (int i = 0; i < audio.packets.size(); i++) {
            session.sendAudio(writer.addPacket(audio.packets.get(i), i == audio.packets.size() - 1));
            long target = start + (i + 1) * 20_000_000L;
            long delay = target - System.nanoTime();
            if (delay > 0) TimeUnit.NANOSECONDS.sleep(delay);
        }
        long holdMs = Long.getLong("s3.holdMs", 0L);
        if (holdMs > 0) { System.out.println("Holding stream open for " + holdMs + " ms"); Thread.sleep(holdMs); }
        session.finish();
        if (!done.await(45, TimeUnit.SECONDS)) { session.cancel(); throw new IOException("timed out waiting for S3 response"); }
        if (failure.get() != null) throw new IOException("S3 failed", failure.get());
        if (!received.get()) throw new IOException("S3 closed without final result");
    }
}
