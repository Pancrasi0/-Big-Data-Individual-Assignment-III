package bigdata.matmul.benchmark;

public final class StorageEstimate {

    private static final int MATRICES_IN_PRODUCT = 3;

    private StorageEstimate() {
    }

    public static long bytes(int size) {
        return (long) MATRICES_IN_PRODUCT * Double.BYTES * size * size;
    }
}
