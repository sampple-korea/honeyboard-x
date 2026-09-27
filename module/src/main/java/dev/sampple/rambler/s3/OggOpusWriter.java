package dev.sampple.rambler.s3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Each addPacket emits a complete Ogg page for one 20 ms Opus packet (960 ticks at 48 kHz). */
public final class OggOpusWriter {
    private static final int[] CRC = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int j = 0; j < 8; j++) r = (r << 1) ^ ((r & 0x80000000) != 0 ? 0x04c11db7 : 0);
            CRC[i] = r;
        }
    }
    private final int serial;
    private final int preSkip;
    private int sequence;
    private long granule;
    private boolean closed;
    /** preSkip is the Opus encoder delay, in 48 kHz samples (typically 312). */
    public OggOpusWriter(int preSkip) {
        if (preSkip < 0 || preSkip > 65535) throw new IllegalArgumentException("preSkip");
        this.preSkip = preSkip;
        serial = new SecureRandom().nextInt();
    }
    public byte[] headers() {
        if (sequence != 0) throw new IllegalStateException("headers already emitted");
        byte[] head = new byte[19];
        System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, head, 0, 8);
        head[8] = 1; head[9] = 1; head[10] = (byte)preSkip; head[11] = (byte)(preSkip >>> 8);
        int rate = 16000;
        for (int i = 0; i < 4; i++) head[12+i] = (byte)(rate >>> (8*i));
        byte[] vendor = "Rambler OggOpusWriter".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        append(tags, "OpusTags".getBytes(StandardCharsets.US_ASCII));
        le(tags, vendor.length, 4); append(tags, vendor); le(tags, 0, 4);
        ByteArrayOutputStream pages = new ByteArrayOutputStream();
        append(pages, page(head, 2, 0)); append(pages, page(tags.toByteArray(), 0, 0));
        return pages.toByteArray();
    }
    /** Emits audio page; last page should be marked eos=true. */
    public byte[] addPacket(byte[] packet, boolean eos) {
        if (sequence < 2 || closed) throw new IllegalStateException("headers missing or writer closed");
        if (packet == null || packet.length == 0 || packet.length > 65024) throw new IllegalArgumentException("Opus packet length");
        granule += 960;
        if (eos) closed = true;
        return page(packet, eos ? 4 : 0, granule);
    }
    private byte[] page(byte[] packet, int flags, long gp) {
        int segments = packet.length / 255 + 1;
        if (segments > 255) throw new IllegalArgumentException("packet exceeds one Ogg page");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        append(b, "OggS".getBytes(StandardCharsets.US_ASCII)); b.write(0); b.write(flags);
        le(b, gp, 8); le(b, serial, 4); le(b, sequence++, 4); le(b, 0, 4);
        b.write(segments);
        for (int i = 0; i < packet.length / 255; i++) b.write(255);
        b.write(packet.length % 255);
        append(b, packet);
        byte[] result = b.toByteArray();
        int crc = 0;
        for (byte value : result) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ (value & 255)) & 255];
        for (int i = 0; i < 4; i++) result[22+i] = (byte)(crc >>> (8*i));
        return result;
    }
    private static void append(ByteArrayOutputStream out, byte[] b) { out.write(b, 0, b.length); }
    private static void le(ByteArrayOutputStream b, long n, int count) {
        for (int i = 0; i < count; i++) b.write((int)(n >>> (i*8)) & 255);
    }
}
