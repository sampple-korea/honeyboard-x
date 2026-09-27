package dev.sampple.rambler.s3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Lossless field/wire/value dump of an S3 protobuf response (unknown extensions included). */
public final class S3Debug {
    private S3Debug() { }

    public static String tree(byte[] message) throws IOException {
        StringBuilder out = new StringBuilder();
        append(out, Proto.parse(message), "", 0);
        return out.toString();
    }

    private static void append(StringBuilder out, List<Proto.Field> fields, String prefix, int depth) {
        for (Proto.Field field : fields) {
            String name = prefix + field.number;
            out.append(name).append(" wire=").append(field.wire);
            if (field.wire != 2) {
                out.append(" value=").append(Long.toUnsignedString(field.value));
                if (field.wire == 1) out.append(" double=").append(Double.longBitsToDouble(field.value));
                out.append('\n');
                continue;
            }
            out.append(" len=").append(field.data.length);
            // A length-delimited value has no wire-level type. Show exact hex even for strings.
            out.append(" hex=").append(hex(field.data));
            String text = text(field.data);
            if (text != null) out.append(" utf8=").append(text);
            out.append('\n');
            if (depth < 12 && field.data.length > 0) {
                try {
                    List<Proto.Field> nested = Proto.parse(field.data);
                    append(out, nested, name + ".", depth + 1);
                } catch (IOException ignored) { /* A string or arbitrary bytes, not a submessage. */ }
            }
        }
    }

    private static String hex(byte[] data) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            result[2 * i] = digits[(data[i] & 255) >>> 4];
            result[2 * i + 1] = digits[data[i] & 15];
        }
        return new String(result);
    }

    private static String text(byte[] data) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') return null;
            }
            return s.isEmpty() ? null : '"' + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"") + '"';
        } catch (CharacterCodingException e) { return null; }
    }
}
