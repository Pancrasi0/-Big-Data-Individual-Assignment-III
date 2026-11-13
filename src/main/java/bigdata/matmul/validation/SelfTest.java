package bigdata.matmul.validation;

import bigdata.matmul.data.MatrixGenerator;
import bigdata.matmul.kernel.MatrixMultiplier;
import bigdata.matmul.kernel.MultiplierFactory;
import bigdata.matmul.kernel.MultiplierSpec;
import bigdata.matmul.kernel.RowPartitioner;
import bigdata.matmul.kernel.RowRange;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SelfTest {

    private static final double[] KNOWN_A = {1, 2, 3, 4};
    private static final double[] KNOWN_B = {5, 6, 7, 8};
    private static final double[] KNOWN_PRODUCT = {19, 22, 43, 50};
    private static final int KNOWN_SIZE = 2;
    private static final int[] MATRIX_SIZES = {1, 2, 5, 17, 64, 70, 129};
    private static final List<Integer> PARALLEL_THREAD_COUNTS = List.of(1, 2, 3, 8);
    private static final int[] PARTITION_ROW_COUNTS = {0, 1, 7, 100};
    private static final int[] PARTITION_PART_COUNTS = {1, 3, 8, 12};
    private static final int MAX_PARTITION_LENGTH_SPREAD = 1;
    private static final double SENTINEL = 123.456;
    private static final double CORRUPTION = 1.0;
    private static final int CORRUPTED_INDEX = 0;
    private static final long SEED_MULTIPLIER = 11L;
    private static final long SECOND_SEED_OFFSET = 1L;
    private static final long FREIVALDS_SEED = 5L;

    private final PrintStream out;
    private final MultiplierFactory factory;
    private int totalChecks;
    private int failedChecks;

    public SelfTest(PrintStream out, MultiplierFactory factory) {
        this.out = out;
        this.factory = factory;
    }

    public boolean run() {
        totalChecks = 0;
        failedChecks = 0;
        out.printf("Running self-tests (tolerance: abs %s, rel %s)...%n", Tolerance.ABSOLUTE, Tolerance.RELATIVE);
        checkPartitioning();
        for (MultiplierSpec spec : specs()) {
            try (MatrixMultiplier multiplier = factory.create(spec)) {
                checkKnownProduct(spec, multiplier);
                for (int size : MATRIX_SIZES) {
                    checkIdentityAndZero(spec, multiplier, size);
                    checkAgainstReferences(spec, multiplier, size);
                }
            }
        }
        for (int size : MATRIX_SIZES) {
            checkFreivalds(size);
        }
        out.printf("Self-tests: %d/%d passed.%n", totalChecks - failedChecks, totalChecks);
        return failedChecks == 0;
    }

    private List<MultiplierSpec> specs() {
        final List<MultiplierSpec> specs = new ArrayList<>();
        specs.add(MultiplierSpec.sequential());
        for (int threadCount : PARALLEL_THREAD_COUNTS) {
            specs.add(MultiplierSpec.parallel(threadCount));
        }
        return specs;
    }

    private void checkPartitioning() {
        for (int rowCount : PARTITION_ROW_COUNTS) {
            for (int partCount : PARTITION_PART_COUNTS) {
                final List<RowRange> ranges = RowPartitioner.partition(rowCount, partCount);
                final String name = "partition rows=" + rowCount + " parts=" + partCount;
                check(name + " count", ranges.size() == partCount);
                check(name + " contiguous cover", coversContiguously(ranges, rowCount));
                check(name + " balanced", isBalanced(ranges));
            }
        }
    }

    private boolean coversContiguously(List<RowRange> ranges, int rowCount) {
        int expectedStart = 0;
        for (RowRange range : ranges) {
            if (range.start() != expectedStart) {
                return false;
            }
            expectedStart = range.endExclusive();
        }
        return expectedStart == rowCount;
    }

    private boolean isBalanced(List<RowRange> ranges) {
        final int shortest = ranges.stream().mapToInt(RowRange::length).min().orElse(0);
        final int longest = ranges.stream().mapToInt(RowRange::length).max().orElse(0);
        return longest - shortest <= MAX_PARTITION_LENGTH_SPREAD;
    }

    private void checkKnownProduct(MultiplierSpec spec, MatrixMultiplier multiplier) {
        final double[] actual = product(multiplier, KNOWN_A, KNOWN_B, KNOWN_SIZE);
        check("2x2 known answer " + spec.label(), Tolerance.areClose(actual, KNOWN_PRODUCT));
    }

    private void checkIdentityAndZero(MultiplierSpec spec, MatrixMultiplier multiplier, int size) {
        final double[] matrix = MatrixGenerator.random(size, SEED_MULTIPLIER * size);
        final double[] identity = MatrixGenerator.identity(size);
        final double[] zero = new double[size * size];
        final String tag = " " + spec.label() + " n=" + size;
        check("A*I == A" + tag, Tolerance.areClose(product(multiplier, matrix, identity, size), matrix));
        check("I*A == A" + tag, Tolerance.areClose(product(multiplier, identity, matrix, size), matrix));
        check("A*0 == 0" + tag, Tolerance.areClose(product(multiplier, matrix, zero, size), zero));
    }

    private void checkAgainstReferences(MultiplierSpec spec, MatrixMultiplier multiplier, int size) {
        final double[] a = MatrixGenerator.random(size, SEED_MULTIPLIER * size);
        final double[] b = MatrixGenerator.random(size, SEED_MULTIPLIER * size + SECOND_SEED_OFFSET);
        final double[] actual = product(multiplier, a, b, size);
        final double[] reference = ReferenceMultiplier.multiply(a, b, size);
        final double[] sequential = sequentialProduct(a, b, size);
        final String tag = " " + spec.label() + " n=" + size;
        check("random vs reference" + tag, Tolerance.areClose(actual, reference));
        check("bit-identical to sequential" + tag, Arrays.equals(actual, sequential));
    }

    private void checkFreivalds(int size) {
        final double[] a = MatrixGenerator.random(size, SEED_MULTIPLIER * size);
        final double[] b = MatrixGenerator.random(size, SEED_MULTIPLIER * size + SECOND_SEED_OFFSET);
        final double[] correct = ReferenceMultiplier.multiply(a, b, size);
        final double[] corrupted = correct.clone();
        corrupted[CORRUPTED_INDEX] += CORRUPTION;
        check("freivalds accepts correct product n=" + size, FreivaldsVerifier.accepts(a, b, correct, size, FREIVALDS_SEED));
        check("freivalds rejects wrong product n=" + size, !FreivaldsVerifier.accepts(a, b, corrupted, size, FREIVALDS_SEED));
    }

    private double[] sequentialProduct(double[] a, double[] b, int size) {
        try (MatrixMultiplier sequential = factory.create(MultiplierSpec.sequential())) {
            return product(sequential, a, b, size);
        }
    }

    private double[] product(MatrixMultiplier multiplier, double[] a, double[] b, int size) {
        final double[] c = new double[size * size];
        Arrays.fill(c, SENTINEL);
        multiplier.multiply(a, b, c, size);
        return c;
    }

    private void check(String name, boolean passed) {
        totalChecks++;
        if (!passed) {
            failedChecks++;
            out.println("  FAIL: " + name);
        }
    }
}
