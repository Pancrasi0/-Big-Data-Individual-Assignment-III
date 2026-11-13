package bigdata.matmul.cli;

import java.util.Locale;

public enum Mode {
    HELP,
    TEST,
    BENCHMARK;

    private static final String HELP_FLAG = "--help";
    private static final String SHORT_HELP_FLAG = "-h";

    public static Mode fromText(String text) {
        if (text.equals(HELP_FLAG) || text.equals(SHORT_HELP_FLAG)) {
            return HELP;
        }
        try {
            return valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown mode: " + text);
        }
    }
}
