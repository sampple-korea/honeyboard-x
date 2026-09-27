package dev.sampple.rambler.s3;

import java.io.IOException;
import java.util.List;

/** Gboard S3Bidi protobuf extensions (see docs/S3_PROTOCOL.md). */
final class S3Messages {
    static byte[] header(S3Session.Config c) { return header(c, c.recognizerId); }
    static byte[] header(S3Session.Config c, String recognizerId) {
        Proto audio = new Proto().fixed32(2, Float.floatToIntBits(16000f)).var(3, 10).var(4, 1).var(9, 0);
        Proto recognizer = new Proto().msg(1, new Proto().var(4, 0))
                .var(5, 1).var(7, 0).var(13, 1).var(20, 1).var(40, 0).var(41, 1)
                .string(50, c.routingKey).msg(91, new Proto().var(2, 1));
        Proto client = new Proto().string(2, "gboard").string(8, "Android")
                .string(9, c.buildDisplay).string(10, c.appVersion).string(11, c.model)
                .string(16, c.triggerPackage).var(26, 1);
        Proto user = new Proto().msg(2, new Proto().string(1, c.language).var(2, 1))
                .var(20, 1);
        java.util.Set<String> languages = new java.util.HashSet<>();
        languages.add(c.language.toLowerCase(java.util.Locale.ROOT));
        if (c.additionalLanguages != null) for (String tag : c.additionalLanguages) {
            if (tag == null || tag.trim().isEmpty()) continue;
            tag = tag.trim();
            if (languages.add(tag.toLowerCase(java.util.Locale.ROOT)))
                user.msg(4, new Proto().string(1, tag).var(2, 1));
        }
        Proto request = new Proto().string(1, recognizerId).var(2, 1)
                .msg(293100, audio).msg(294500, recognizer).msg(294000, client)
                .msg(293000, user).msg(29735924, new Proto().var(1, 500));
        if (c.surroundingText != null && !c.surroundingText.isEmpty())
            request.msg(1203, new Proto().string(1, c.surroundingText));
        return request.build();
    }
    static byte[] audio(byte[] b, long elapsedMs) {
        return new Proto().msg(293100, new Proto().var(9, elapsedMs))
                .msg(293101, new Proto().bytes(1, b)).build();
    }
    static byte[] end() { return new Proto().var(2, 1).var(3, 1).build(); }

    static void response(byte[] b, S3Session.Listener listener) throws IOException {
        List<Proto.Field> root = Proto.parse(b);
        Proto.Field status = Proto.first(root, 1);
        int state = status == null ? 0 : (int)status.value;
        if (state == 2 || state == 3) {
            Proto.Field code = Proto.first(root, 2), error = Proto.first(root, 3);
            throw new IOException("S3 status=" + state + " error_code=" + (code == null ? 0 : code.value)
                    + " error_message=" + (error == null ? "" : error.text()));
        }
        for (Proto.Field ext : root) {
            if (ext.number != 1253625 || ext.data == null) continue;
            for (Proto.Field e : ext.fields()) {
                if (e.number != 1 || e.data == null) continue;
                List<Proto.Field> recognition = e.fields();
                Proto.Field partial = Proto.first(recognition, 4);
                if (partial != null && partial.data != null) {
                    StringBuilder text = new StringBuilder(); boolean stable = true;
                    for (Proto.Field piece : partial.fields()) {
                        if (piece.number != 1 || piece.data == null) continue;
                        List<Proto.Field> p = piece.fields();
                        Proto.Field t = Proto.first(p, 1), s = Proto.first(p, 2);
                        if (t == null) continue;
                        text.append(t.text());
                        if (s == null || s.wire != 1 || Double.longBitsToDouble(s.value) < .9) stable = false;
                    }
                    if (text.length() > 0) listener.onPartial(text.toString(), stable);
                }
                Proto.Field finals = Proto.first(recognition, 3);
                if (finals == null || finals.data == null) continue;
                for (Proto.Field alt : finals.fields()) {
                    if (alt.number != 3 || alt.data == null) continue;
                    List<Proto.Field> alternative = alt.fields();
                    Proto.Field transcript = Proto.first(alternative, 1);
                    Proto.Field edit = Proto.first(alternative, 74956224);
                    if (edit != null && edit.data != null) {
                        List<Proto.Field> editing = edit.fields();
                        Proto.Field intent = Proto.first(editing, 4), history = Proto.first(editing, 3), full = Proto.first(editing, 1);
                        Proto.Field cursor = Proto.first(editing, 7);
                        String edited = history == null ? null : history.text();
                        if (cursor != null && cursor.data != null) {
                            StringBuilder cursorText = new StringBuilder();
                            for (Proto.Field part : cursor.fields()) if (part.data != null && part.number <= 3) cursorText.append(part.text());
                            edited = cursorText.toString();
                        }
                        // proto oneof edited_history is field 3 (string) OR field 7 (CursorText).
                        if (intent != null || history != null || cursor != null)
                            listener.onEdit(intent == null ? 0 : (int)intent.value,
                                    full == null ? "" : full.text().replace("<cursor>", "").replace("</cursor>", ""), edited);
                    }
                    if (transcript != null && !transcript.text().isEmpty()) listener.onFinal(transcript.text());
                    break;
                }
            }
        }
    }
}
