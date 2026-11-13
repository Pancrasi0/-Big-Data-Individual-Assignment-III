package bigdata.matmul.benchmark;

public final class Checksum {

    private static final double ZERO = 0.0;

    private static volatile double sink;

    private Checksum() {
    }

    public static double of(double[] values) {
        double sum = ZERO;
        for (double value : values) {
            sum += value;
        }
        sink += sum;
        return sum;
    }

    public static double sinkValue() {
        return sink;
    }
}
