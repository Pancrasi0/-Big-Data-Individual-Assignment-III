package bigdata.matmul.validation;

public final class ReferenceMultiplier {

    private static final double ZERO = 0.0;

    private ReferenceMultiplier() {
    }

    public static double[] multiply(double[] a, double[] b, int size) {
        final double[][] left = toRows(a, size);
        final double[][] right = toRows(b, size);
        final double[] product = new double[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double sum = ZERO;
                for (int k = 0; k < size; k++) {
                    sum += left[i][k] * right[k][j];
                }
                product[i * size + j] = sum;
            }
        }
        return product;
    }

    private static double[][] toRows(double[] flat, int size) {
        final double[][] rows = new double[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(flat, i * size, rows[i], 0, size);
        }
        return rows;
    }
}
