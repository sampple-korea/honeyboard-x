package dev.sampple.rambler.s3;

import java.util.ArrayList;
import java.util.List;

/** Offline regression: interim wire response is decoded without changing the public callbacks. */
public final class S3ResponseTest {
    public static void main(String[] args) throws Exception {
        byte[] sample = new Proto().msg(1253625, new Proto().msg(1,
                new Proto().var(1, 0).msg(4, new Proto().msg(1,
                        new Proto().string(1, "안녕하세요"))))).build();
        List<String> partials = new ArrayList<>();
        S3Messages.response(sample, new S3Session.Listener() {
            @Override public void onPartial(String text, boolean stable) {
                partials.add(text + ":" + stable);
            }
        });
        if (!partials.equals(List.of("안녕하세요:false"))) throw new AssertionError(partials);
        String tree = S3Debug.tree(sample);
        if (!tree.contains("1253625.1.4.1.1 wire=2") || !tree.contains("utf8=\"안녕하세요\""))
            throw new AssertionError(tree);
        S3Session.Config config = new S3Session.Config();
        config.language = "ko-KR"; config.additionalLanguages = new String[]{"en-US", "en-US", "ko-KR", ""};
        config.surroundingText = "최근 문단";
        List<Proto.Field> header = Proto.parse(S3Messages.header(config));
        List<Proto.Field> user = Proto.first(header, 293000).fields();
        List<String> languages = new ArrayList<>();
        for (Proto.Field f : user) if (f.number == 4) {
            languages.add(Proto.first(f.fields(), 1).text());
            if (Proto.first(f.fields(), 2).value != 1) throw new AssertionError("language type");
        }
        if (!languages.equals(List.of("en-US"))) throw new AssertionError(languages);
        if (!Proto.first(Proto.first(header, 1203).fields(), 1).text().equals("최근 문단"))
            throw new AssertionError("history header");
        List<String> edits = new ArrayList<>();
        byte[] deletion = new Proto().msg(1253625, new Proto().msg(1, new Proto().var(1, 1)
                .msg(3, new Proto().msg(3, new Proto().string(1, "")
                        .msg(74956224, new Proto().var(4, 2).string(3, "")))))).build();
        S3Messages.response(deletion, new S3Session.Listener() {
            @Override public void onEdit(int intent, String full, String history) { edits.add(intent + ":" + history); }
        });
        if (!edits.equals(List.of("2:"))) throw new AssertionError("empty deletion lost: " + edits);
        System.out.println("PASS: partials, raw tree, additional languages, bounded history header, empty deletion");
    }
}
