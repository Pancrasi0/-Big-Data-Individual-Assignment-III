package bigdata.matmul.validation;

import java.util.Random;

public final class FreivaldsVerifier {

    private static final int ROUNDS = 2;
    private static final double ZERO = 0.0;

    private FreivaldsVerifier() {
    }

    public static boolean accepts(double[] a, double[] b, double[] c, int size, long seed) {
        final Random random = new Random(seed);
        for (int round = 0; round < ROUNDS; round++) {
            final double[] x = randomVector(size, random);
            final double[] expected = multiplyByVector(a, multiplyByVector(b, x, size), size);
            final double[] actual = multiplyByVector(c, x, size);
            if (!Tolerance.areClose(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    private static double[] randomVector(int size, Random random) {
        final double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = random.nextDouble();
        }
        return vector;
    }

    private static double[] multiplyByVector(double[] matrix, double[] vector, int size) {
        final double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            final int rowOffset = i * size;
            double sum = ZERO;
            for (int j = 0; j < size; j++) {
                sum += matrix[rowOffset + j] * vector[j];
            }
            result[i] = sum;
        }
        return result;
    }
}
