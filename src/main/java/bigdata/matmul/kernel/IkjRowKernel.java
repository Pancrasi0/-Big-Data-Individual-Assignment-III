package bigdata.matmul.kernel;

import java.util.Arrays;

public final class IkjRowKernel {

    private static final double ZERO = 0.0;

    private IkjRowKernel() {
    }

    public static void multiplyRows(double[] a, double[] b, double[] c, int size, RowRange rows) {
        Arrays.fill(c, rows.start() * size, rows.endExclusive() * size, ZERO);
        for (int i = rows.start(); i < rows.endExclusive(); i++) {
            final int rowOffset = i * size;
            for (int k = 0; k < size; k++) {
                final double aik = a[rowOffset + k];
                final int bRowOffset = k * size;
                for (int j = 0; j < size; j++) {
                    c[rowOffset + j] += aik * b[bRowOffset + j];
                }
            }
        }
    }
}
