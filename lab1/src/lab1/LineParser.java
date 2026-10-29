package lab1;

/**
 * Parses text of the form "-123\n45\n0\n" byte by byte. It keeps its state between calls,
 * so a number split across two read buffers is still parsed correctly.
 */
final class LineParser {
    private final Counts counts;
    private long value;
    private boolean negative;
    private boolean hasDigits;

    LineParser(Counts counts) {
        this.counts = counts;
    }

    /** Feeds one byte. Returns true when the byte was '\n', i.e. a line has just ended. */
    boolean feed(byte b) {
        if (b >= '0' && b <= '9') {
            value = value * 10 + (b - '0');
            hasDigits = true;
        } else if (b == '-') {
            negative = true;
        } else if (b == '\n') {
            endLine();
            return true;
        }
        // '\r' and any other byte are ignored
        return false;
    }

    /** Ends the current line; also called at end of file when the last line has no '\n'. */
    void endLine() {
        if (hasDigits) {
            counts.count(negative ? -value : value);
        }
        value = 0;
        negative = false;
        hasDigits = false;
    }

    /** Parses one line given as a String (Task 1c). */
    static long parse(String line) {
        long value = 0;
        boolean negative = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= '0' && c <= '9') value = value * 10 + (c - '0');
            else if (c == '-') negative = true;
        }
        return negative ? -value : value;
    }
}
