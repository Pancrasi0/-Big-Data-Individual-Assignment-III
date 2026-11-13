package bigdata.matmul.kernel;

public record MultiplierSpec(Variant variant, int threadCount) {

    private static final int SINGLE_THREAD = 1;

    public MultiplierSpec {
        if (threadCount < SINGLE_THREAD) {
            throw new IllegalArgumentException("threadCount must be >= " + SINGLE_THREAD);
        }
    }

    public static MultiplierSpec sequential() {
        return new MultiplierSpec(Variant.SEQUENTIAL, SINGLE_THREAD);
    }

    public static MultiplierSpec parallel(int threadCount) {
        return new MultiplierSpec(Variant.PARALLEL, threadCount);
    }

    public String label() {
        return variant.methodName() + "(threads=" + threadCount + ")";
    }
}
