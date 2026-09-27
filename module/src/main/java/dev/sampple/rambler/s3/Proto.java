package dev.sampple.rambler.s3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal protobuf wire format; unknown extension fields are safely skipped. */
final class Proto {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    Proto var(int field, long value) { tag(field, 0); writeVar(value); return this; }
    Proto bytes(int field, byte[] data) { tag(field, 2); writeVar(data.length); out.write(data, 0, data.length); return this; }
    Proto string(int field, String s) { return s == null ? this : bytes(field, s.getBytes(StandardCharsets.UTF_8)); }
    Proto msg(int field, Proto p) { return bytes(field, p.build()); }
    Proto fixed32(int field, int value) {
        tag(field, 5); for (int i = 0; i < 4; i++) out.write(value >>> (i * 8)); return this;
    }
    byte[] build() { return out.toByteArray(); }
    private void tag(int field, int wire) {
        if (field <= 0 || field > 0x1fffffff) throw new IllegalArgumentException("field");
        writeVar(((long) field << 3) | wire);
    }
    private void writeVar(long value) {
        while ((value & ~127L) != 0) { out.write(((int)value & 127) | 128); value >>>= 7; }
        out.write((int)value);
    }
    static final class Field {
        final int number, wire; final long value; final byte[] data;
        Field(int n, int w, long v, byte[] b) { number=n; wire=w; value=v; data=b; }
        String text() { return new String(data, StandardCharsets.UTF_8); }
        List<Field> fields() throws IOException { return parse(data); }
    }
    static List<Field> parse(byte[] b) throws IOException {
        if (b.length > 2_000_000) throw new IOException("protobuf message too large");
        List<Field> fields = new ArrayList<>();
        int[] p = {0};
        while (p[0] < b.length) {
            long tag = readVar(b, p); int wire = (int)(tag & 7), number = (int)(tag >>> 3);
            if (number == 0 || number > 0x1fffffff) throw new IOException("invalid protobuf tag");
            long value = 0; byte[] data = null;
            if (wire == 0) value = readVar(b, p);
            else if (wire == 1 || wire == 5) {
                int n = wire == 1 ? 8 : 4;
                if (n > b.length - p[0]) throw new IOException("truncated protobuf fixed value");
                for (int i = 0; i < n; i++) value |= (b[p[0]++] & 255L) << (8 * i);
            } else if (wire == 2) {
                long n = readVar(b, p);
                if (n < 0 || n > b.length - p[0]) throw new IOException("invalid protobuf length");
                data = new byte[(int)n]; System.arraycopy(b, p[0], data, 0, (int)n); p[0] += (int)n;
            } else throw new IOException("unsupported protobuf wire type " + wire);
            fields.add(new Field(number, wire, value, data));
            if (fields.size() > 20000) throw new IOException("too many protobuf fields");
        }
        return fields;
    }
    static Field first(List<Field> fields, int n) {
        for (Field f : fields) if (f.number == n) return f;
        return null;
    }
    private static long readVar(byte[] b, int[] p) throws IOException {
        long v = 0;
        for (int i = 0; i < 10; i++) {
            if (p[0] >= b.length) throw new IOException("truncated protobuf varint");
            int next = b[p[0]++] & 255;
            if (i == 9 && (next & 254) != 0) throw new IOException("protobuf varint overflow");
            v |= (long)(next & 127) << (i * 7);
            if ((next & 128) == 0) return v;
        }
        throw new IOException("protobuf varint overflow");
    }
}
