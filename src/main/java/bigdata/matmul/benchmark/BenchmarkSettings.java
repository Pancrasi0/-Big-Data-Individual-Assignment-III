package bigdata.matmul.benchmark;

import bigdata.matmul.kernel.MultiplierSpec;
import bigdata.matmul.kernel.Variant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record BenchmarkSettings(
        List<Integer> sizes,
        List<Integer> threadCounts,
        Set<Variant> variants,
        int warmupRuns,
        int repetitions,
        long seed,
        double maxSecondsPerMultiplication,
        long memoryBudgetMegabytes,
        Path outputDirectory,
        String outputFileName) {

    public static final int MAX_MATRIX_SIZE = (int) Math.sqrt(Integer.MAX_VALUE);

    private static final List<Integer> DEFAULT_SIZES = List.of(250, 500, 1000, 2000);
    private static final int DEFAULT_WARMUP_RUNS = 2;
    private static final int DEFAULT_REPETITIONS = 5;
    private static final long DEFAULT_SEED = 42L;
    private static final double DEFAULT_MAX_SECONDS = 120.0;
    private static final long DEFAULT_MEMORY_BUDGET_MEGABYTES = 3072L;
    private static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("results");
    private static final String DEFAULT_OUTPUT_FILE_NAME = "raw_measurements.csv";
    private static final String ENVIRONMENT_FILE_NAME = "environment.txt";
    private static final long SECOND_MATRIX_SEED_OFFSET = 1L;
    private static final long VERIFICATION_SEED_OFFSET = 7L;
    private static final int THREAD_COUNT_GROWTH_FACTOR = 2;
    private static final int OVERSUBSCRIPTION_FACTOR = 2;
    private static final int MIN_SIZE = 1;
    private static final int MIN_THREADS = 1;
    private static final int MIN_REPETITIONS = 1;
    private static final int MIN_WARMUP_RUNS = 0;

    public BenchmarkSettings {
        sizes = sortedDistinct(sizes);
        threadCounts = sortedDistinct(threadCounts);
        require(!sizes.isEmpty(), "at least one matrix size is required");
        require(sizes.stream().allMatch(size -> size >= MIN_SIZE && size <= MAX_MATRIX_SIZE),
                "matrix sizes must be in [" + MIN_SIZE + ", " + MAX_MATRIX_SIZE + "]");
        require(!variants.isEmpty(), "at least one variant is required");
        variants = EnumSet.copyOf(variants);
        require(!variants.contains(Variant.PARALLEL) || !threadCounts.isEmpty(),
                "the parallel variant needs at least one thread count");
        require(threadCounts.stream().allMatch(count -> count >= MIN_THREADS),
                "thread counts must be >= " + MIN_THREADS);
        require(warmupRuns >= MIN_WARMUP_RUNS, "warm-up runs must be >= " + MIN_WARMUP_RUNS);
        require(repetitions >= MIN_REPETITIONS, "repetitions must be >= " + MIN_REPETITIONS);
        require(maxSecondsPerMultiplication > 0, "the time budget must be positive");
        require(memoryBudgetMegabytes > 0, "the memory budget must be positive");
    }

    public static BenchmarkSettings defaults() {
        return new BenchmarkSettings(
                DEFAULT_SIZES,
                defaultThreadCounts(Runtime.getRuntime().availableProcessors()),
                EnumSet.allOf(Variant.class),
                DEFAULT_WARMUP_RUNS,
                DEFAULT_REPETITIONS,
                DEFAULT_SEED,
                DEFAULT_MAX_SECONDS,
                DEFAULT_MEMORY_BUDGET_MEGABYTES,
                DEFAULT_OUTPUT_DIRECTORY,
                DEFAULT_OUTPUT_FILE_NAME);
    }

    public static List<Integer> defaultThreadCounts(int logicalProcessors) {
        final Set<Integer> counts = new TreeSet<>();
        for (int count = MIN_THREADS; count < logicalProcessors; count *= THREAD_COUNT_GROWTH_FACTOR) {
            counts.add(count);
        }
        counts.add(logicalProcessors);
        counts.add(logicalProcessors * OVERSUBSCRIPTION_FACTOR);
        return List.copyOf(counts);
    }

    public List<MultiplierSpec> multiplierSpecs() {
        final List<MultiplierSpec> specs = new ArrayList<>();
        for (Variant variant : variants) {
            switch (variant) {
                case SEQUENTIAL -> specs.add(MultiplierSpec.sequential());
                case PARALLEL -> threadCounts.forEach(count -> specs.add(MultiplierSpec.parallel(count)));
            }
        }
        return specs;
    }

    public long secondMatrixSeed() {
        return seed + SECOND_MATRIX_SEED_OFFSET;
    }

    public long verificationSeed() {
        return seed + VERIFICATION_SEED_OFFSET;
    }

    public Path resultsFile() {
        return outputDirectory.resolve(outputFileName);
    }

    public Path environmentFile() {
        return outputDirectory.resolve(ENVIRONMENT_FILE_NAME);
    }

    private static List<Integer> sortedDistinct(List<Integer> values) {
        return values.stream().sorted().distinct().toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
