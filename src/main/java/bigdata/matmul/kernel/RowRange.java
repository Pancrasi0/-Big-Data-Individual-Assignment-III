package bigdata.matmul.kernel;

public record RowRange(int start, int endExclusive) {

    public RowRange {
        if (start < 0 || endExclusive < start) {
            throw new IllegalArgumentException("Invalid row range [" + start + ", " + endExclusive + ")");
        }
    }

    public int length() {
        return endExclusive - start;
    }
}
