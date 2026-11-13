package bigdata.matmul.kernel;

public interface MatrixMultiplier extends AutoCloseable {

    void multiply(double[] a, double[] b, double[] c, int size);

    @Override
    default void close() {
    }
}
