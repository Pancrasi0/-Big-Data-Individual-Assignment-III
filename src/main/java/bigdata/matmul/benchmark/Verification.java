package bigdata.matmul.benchmark;

public enum Verification {
    NOT_APPLICABLE("NA"),
    PASS("PASS"),
    FAIL("FAIL");

    private final String csvName;

    Verification(String csvName) {
        this.csvName = csvName;
    }

    public String csvName() {
        return csvName;
    }

    public static Verification of(boolean passed) {
        return passed ? PASS : FAIL;
    }
}
