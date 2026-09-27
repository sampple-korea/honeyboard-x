package dev.sampple.rambler;

/** Pure JVM tests; Android InputConnection/AudioRecord still need device verification. */
public final class VoiceLogicTest {
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
    public static void main(String[] args) {
        String prefix = "이전 문단입니다.\n".repeat(1000);
        String original = "오늘 회의는 오후 세 시입니다. 장소는 서울입니다.";
        String suffix = "\n이후 문단은 보존합니다.";
        String document = prefix + original + suffix;
        int caret = prefix.length() + original.length();
        HistoryRange range = HistoryRange.capture(document.substring(caret - 2001, caret), caret, true);
        check(range.original.equals(original) && range.start == prefix.length(), "recent paragraph, absolute offset");
        String replacement = original.replace("서울", "부산");
        check((document.substring(0, range.start) + replacement + document.substring(range.end))
                .equals(prefix + replacement + suffix), "outside range preserved");
        check(range.matches(original, caret, true), "unchanged original");
        check(!range.matches(replacement, caret, true), "changed original rejected");
        check(!range.matches(original, caret + 1, true), "moved caret rejected");
        check(!range.matches(original, caret, false), "selection rejected");
        check(HistoryRange.capture(original, -1, true) == null, "unknown offset rejected");
        check(HistoryRange.capture(original, caret, false) == null, "selection capture rejected");
        check(HistoryRange.capture("a".repeat(2500), 5000, true).start == 3000, "2000 UTF16 cap");
        HistoryRange unicode = HistoryRange.capture("😀" + "a".repeat(1999), 2001, true);
        check(unicode.original.length() == 1999 && unicode.start == 2, "no split surrogate");
        check(HistoryRange.capture("text\n", 5, true).original.isEmpty(), "new paragraph empty history");
        SilenceDetector silent = new SilenceDetector();
        for (int t = 0; t < 10000; t += 60) check(!silent.update(.002f, t, 3), "unarmed silence");
        SilenceDetector speech = new SilenceDetector();
        for (int t = 0; t <= 600; t += 60) check(!speech.update(.05f, t, 3), "speaking");
        for (int t = 660; t < 3600; t += 60) check(!speech.update(.002f, t, 3), "before deadline");
        check(speech.update(.002f, 3600, 3), "three second timeout");
        check(!speech.update(.002f, 9000, 0), "disabled timeout");
        SilenceDetector click = new SilenceDetector(); click.update(.8f, 0, 3);
        for (int t = 60; t < 6000; t += 60) check(!click.update(.002f, t, 3), "single click must not arm");
        VoiceOptions defaults = VoiceOptions.defaults();
        check(defaults.primary.isEmpty() && defaults.additional.isEmpty() && defaults.silenceSeconds == 3
                && defaults.aurora && defaults.haptic && !defaults.sound, "defaults");
        check(new VoiceOptions("ko-kr", "en-us,en-US", 0, false, false, false).additional.equals("en-US"), "language normalization");
        try { new VoiceOptions("invalid tag", "", 3, true, true, false); throw new AssertionError("invalid tag accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: bounded history, edits preserve prefix/suffix, stale rejection, unicode, silence and settings");
    }
}
