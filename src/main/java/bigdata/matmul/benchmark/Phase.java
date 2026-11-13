package bigdata.matmul.benchmark;

public enum Phase {
    WARMUP("warmup"),
    MULTIPLY("multiply"),
    VERIFY("verify"),
    MEMORY("memory");

    private final String csvName;

    Phase(String csvName) {
        this.csvName = csvName;
    }

    public String csvName() {
        return csvName;
    }
}
