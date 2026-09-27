package dev.sampple.rambler.s3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** HPACK RFC 7541, including Huffman strings and the bounded dynamic table. */
final class Hpack {
    private static final String[][] STATIC = {
        {"", ""}, {":authority", ""}, {":method", "GET"}, {":method", "POST"},
        {":path", "/"}, {":path", "/index.html"}, {":scheme", "http"}, {":scheme", "https"},
        {":status", "200"}, {":status", "204"}, {":status", "206"}, {":status", "304"},
        {":status", "400"}, {":status", "404"}, {":status", "500"}, {"accept-charset", ""},
        {"accept-encoding", "gzip, deflate"}, {"accept-language", ""}, {"accept-ranges", ""},
        {"accept", ""}, {"access-control-allow-origin", ""}, {"age", ""}, {"allow", ""},
        {"authorization", ""}, {"cache-control", ""}, {"content-disposition", ""},
        {"content-encoding", ""}, {"content-language", ""}, {"content-length", ""},
        {"content-location", ""}, {"content-range", ""}, {"content-type", ""},
        {"cookie", ""}, {"date", ""}, {"etag", ""}, {"expect", ""}, {"expires", ""},
        {"from", ""}, {"host", ""}, {"if-match", ""}, {"if-modified-since", ""},
        {"if-none-match", ""}, {"if-range", ""}, {"if-unmodified-since", ""},
        {"last-modified", ""}, {"link", ""}, {"location", ""}, {"max-forwards", ""},
        {"proxy-authenticate", ""}, {"proxy-authorization", ""}, {"range", ""},
        {"referer", ""}, {"refresh", ""}, {"retry-after", ""}, {"server", ""},
        {"set-cookie", ""}, {"strict-transport-security", ""}, {"transfer-encoding", ""},
        {"user-agent", ""}, {"vary", ""}, {"via", ""}, {"www-authenticate", ""}
    };
    private static final int[] CODES = {8184,8388568,268435426,268435427,268435428,268435429,268435430,268435431,268435432,16777194,1073741820,268435433,268435434,1073741821,268435435,268435436,268435437,268435438,268435439,268435440,268435441,268435442,1073741822,268435443,268435444,268435445,268435446,268435447,268435448,268435449,268435450,268435451,20,1016,1017,4090,8185,21,248,2042,1018,1019,249,2043,250,22,23,24,0,1,2,25,26,27,28,29,30,31,92,251,32764,32,4091,1020,8186,33,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,252,115,253,8187,524272,8188,16380,34,32765,3,35,4,36,5,37,38,39,6,116,117,40,41,42,7,43,118,44,8,9,45,119,120,121,122,123,32766,2044,16381,8189,268435452,1048550,4194258,1048551,1048552,4194259,4194260,4194261,8388569,4194262,8388570,8388571,8388572,8388573,8388574,16777195,8388575,16777196,16777197,4194263,8388576,16777198,8388577,8388578,8388579,8388580,2097116,4194264,8388581,4194265,8388582,8388583,16777199,4194266,2097117,1048553,4194267,4194268,8388584,8388585,2097118,8388586,4194269,4194270,16777200,2097119,4194271,8388587,8388588,2097120,2097121,4194272,2097122,8388589,4194273,8388590,8388591,1048554,4194274,4194275,4194276,8388592,4194277,4194278,8388593,67108832,67108833,1048555,524273,4194279,8388594,4194280,33554412,67108834,67108835,67108836,134217694,134217695,67108837,16777201,33554413,524274,2097123,67108838,134217696,134217697,67108839,134217698,16777202,2097124,2097125,67108840,67108841,268435453,134217699,134217700,134217701,1048556,16777203,1048557,2097126,4194281,2097127,2097128,8388595,4194282,4194283,33554414,33554415,16777204,16777205,67108842,8388596,67108843,134217702,67108844,67108845,134217703,134217704,134217705,134217706,134217707,268435454,134217708,134217709,134217710,134217711,134217712,67108846,1073741823};
    private static final byte[] LENGTHS = {13,23,28,28,28,28,28,28,28,24,30,28,28,30,28,28,28,28,28,28,28,28,30,28,28,28,28,28,28,28,28,28,6,10,10,12,13,6,8,11,10,10,8,11,8,6,6,6,5,5,5,6,6,6,6,6,6,6,7,8,15,6,12,10,13,6,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,8,7,8,13,19,13,14,6,15,5,6,5,6,5,6,6,6,5,7,7,6,6,6,5,6,7,6,5,5,6,7,7,7,7,7,15,11,14,13,28,20,22,20,20,22,22,22,23,22,23,23,23,23,23,24,23,24,24,22,23,24,23,23,23,23,21,22,23,22,23,23,24,22,21,20,22,22,23,23,21,23,22,22,24,21,22,23,23,21,21,22,21,23,22,23,23,20,22,22,22,23,22,22,23,26,26,20,19,22,23,22,25,26,26,26,27,27,26,24,25,19,21,26,27,27,26,27,24,21,21,26,26,28,27,27,27,20,24,20,21,22,21,21,23,22,22,25,25,24,24,26,23,26,27,26,26,27,27,27,27,27,28,27,27,27,27,27,26,30};
    static byte[] encode(String[][] headers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String[] h : headers) {
            // Literal without indexing; no sensitive metadata enters an HPACK table.
            out.write(0);
            raw(out, h[0]); raw(out, h[1]);
        }
        return out.toByteArray();
    }
    private static void raw(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        integer(out, b.length, 7, 0);
        out.write(b, 0, b.length);
    }
    private static void integer(ByteArrayOutputStream out, int value, int bits, int prefix) {
        int max = (1 << bits) - 1;
        if (value < max) { out.write(prefix | value); return; }
        out.write(prefix | max); value -= max;
        while (value >= 128) { out.write((value & 127) | 128); value >>>= 7; }
        out.write(value);
    }
    private final List<String[]> dynamic = new ArrayList<>();
    private int size, maxSize = 4096, allowedMax = 4096;
    void limit(int n) { allowedMax = n; if (maxSize > n) { maxSize = n; evict(); } }
    List<String[]> decode(byte[] b) throws IOException {
        Cursor c = new Cursor(b);
        List<String[]> result = new ArrayList<>();
        boolean beforeFields = true;
        while (c.pos < b.length) {
            int first = c.read();
            if ((first & 128) != 0) {
                result.add(lookup(number(c, first, 7))); beforeFields = false;
            } else if ((first & 64) != 0) {
                int index = number(c, first, 6);
                String name = index == 0 ? string(c) : lookup(index)[0];
                String value = string(c);
                result.add(new String[]{name, value}); insert(name, value); beforeFields = false;
            } else if ((first & 32) != 0) {
                if (!beforeFields) throw new IOException("HPACK table size update after headers");
                int n = number(c, first, 5);
                if (n > allowedMax) throw new IOException("HPACK table size exceeds limit");
                maxSize = n; evict();
            } else {
                int index = number(c, first, 4);
                String name = index == 0 ? string(c) : lookup(index)[0];
                result.add(new String[]{name, string(c)}); beforeFields = false;
            }
            if (result.size() > 256) throw new IOException("too many headers");
        }
        return result;
    }
    private void insert(String name, String value) {
        int n = name.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length + 32;
        if (n > maxSize) { dynamic.clear(); size = 0; return; }
        dynamic.add(0, new String[]{name, value}); size += n; evict();
    }
    private void evict() {
        while (size > maxSize && !dynamic.isEmpty()) {
            String[] e = dynamic.remove(dynamic.size()-1);
            size -= e[0].getBytes(StandardCharsets.UTF_8).length + e[1].getBytes(StandardCharsets.UTF_8).length + 32;
        }
    }
    private String[] lookup(int index) throws IOException {
        if (index <= 0) throw new IOException("invalid HPACK index");
        if (index < STATIC.length) return STATIC[index];
        int d = index - STATIC.length;
        if (d >= dynamic.size()) throw new IOException("invalid dynamic HPACK index " + index);
        return dynamic.get(d);
    }
    private static int number(Cursor c, int first, int bits) throws IOException {
        int mask = (1 << bits) - 1, n = first & mask;
        if (n != mask) return n;
        int shift = 0, x;
        do {
            x = c.read();
            if (shift > 24 || (long) (x & 127) << shift > Integer.MAX_VALUE - (long)n)
                throw new IOException("HPACK integer overflow");
            n += (x & 127) << shift; shift += 7;
        } while ((x & 128) != 0);
        return n;
    }
    private static String string(Cursor c) throws IOException {
        int first = c.read(), n = number(c, first, 7);
        if (n > 65536 || n > c.b.length - c.pos) throw new IOException("HPACK string exceeds limit");
        byte[] src = new byte[n]; System.arraycopy(c.b, c.pos, src, 0, n); c.pos += n;
        return new String((first & 128) == 0 ? src : huffman(src), StandardCharsets.UTF_8);
    }
    private static byte[] huffman(byte[] src) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        long bits = 0; int count = 0;
        for (byte datum : src) {
            bits = (bits << 8) | (datum & 255L); count += 8;
            boolean matched;
            do {
                matched = false;
                for (int i = 0; i < CODES.length; i++) {
                    int len = LENGTHS[i] & 255;
                    if (len <= count && (bits >>> (count - len) & ((1L << len) - 1)) == CODES[i]) {
                        if (i == 256) throw new IOException("HPACK EOS symbol");
                        result.write(i); count -= len;
                        bits &= (1L << count) - 1; matched = true;
                        if (result.size() > 65536) throw new IOException("HPACK decoded string exceeds limit");
                        break;
                    }
                }
            } while (matched);
        }
        if (count > 7 || bits != (1L << count) - 1) throw new IOException("invalid HPACK padding");
        return result.toByteArray();
    }
    private static final class Cursor {
        final byte[] b; int pos;
        Cursor(byte[] b) { this.b = b; }
        int read() throws IOException { if (pos >= b.length) throw new IOException("truncated HPACK"); return b[pos++] & 255; }
    }
}
