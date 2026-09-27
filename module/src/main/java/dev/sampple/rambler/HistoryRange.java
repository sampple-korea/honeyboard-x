package dev.sampple.rambler;

/** A frozen UTF-16 range ending at the caret; never a guessed full document. */
final class HistoryRange {
    static final int LIMIT = 2000;
    final int start, end;
    final String original;

    private HistoryRange(int start, int end, String original) {
        this.start = start; this.end = end; this.original = original;
    }

    static HistoryRange capture(String before, int caret, boolean collapsed) {
        if (!collapsed || caret < 0 || before == null || caret < before.length()) return null;
        int from = Math.max(0, before.length() - LIMIT);
        // Do not send earlier paragraphs. A trailing newline starts a new paragraph.
        from = Math.max(from, before.lastIndexOf('\n') + 1);
        if (from < before.length() && Character.isLowSurrogate(before.charAt(from))) from++;
        return new HistoryRange(caret - (before.length() - from), caret, before.substring(from));
    }

    boolean matches(String before, int caret, boolean collapsed) {
        return collapsed && caret == end && before != null && before.endsWith(original)
                && before.length() >= original.length();
    }
}
