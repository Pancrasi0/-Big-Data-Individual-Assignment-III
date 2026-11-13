package bigdata.matmul.kernel;

public final class SequentialMultiplier implements MatrixMultiplier {

    private static final int FIRST_ROW = 0;

    @Override
    public void multiply(double[] a, double[] b, double[] c, int size) {
        IkjRowKernel.multiplyRows(a, b, c, size, new RowRange(FIRST_ROW, size));
    }
}
