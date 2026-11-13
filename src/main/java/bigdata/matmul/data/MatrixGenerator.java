package bigdata.matmul.data;

import java.util.Random;

public final class MatrixGenerator {

    private static final double VALUE_OFFSET = 1.0;
    private static final double IDENTITY_VALUE = 1.0;

    private MatrixGenerator() {
    }

    public static double[] random(int size, long seed) {
        final Random random = new Random(seed);
        final double[] matrix = new double[size * size];
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] = VALUE_OFFSET + random.nextDouble();
        }
        return matrix;
    }

    public static double[] identity(int size) {
        final double[] matrix = new double[size * size];
        for (int i = 0; i < size; i++) {
            matrix[i * size + i] = IDENTITY_VALUE;
        }
        return matrix;
    }
}
