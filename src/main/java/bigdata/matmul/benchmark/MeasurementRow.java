package bigdata.matmul.benchmark;

import bigdata.matmul.kernel.MultiplierSpec;

public record MeasurementRow(
        MultiplierSpec spec,
        int size,
        long seed,
        int repetition,
        Phase phase,
        long timeNanos,
        long estimatedStorageBytes,
        long heapUsedBytes,
        long peakRssKilobytes,
        double checksum,
        Verification verification,
        RunStatus status) {

    public static final long ABSENT = -1L;

    public static final String HEADER = String.join(",",
            "method", "n", "threads", "seed", "numeric_type", "rep", "phase", "time_ns",
            "est_storage_bytes", "heap_used_bytes", "peak_rss_kb", "checksum", "verified", "status");

    private static final String NUMERIC_TYPE = "double";
    private static final String EMPTY = "";
    private static final int NO_REPETITION = 0;

    public static MeasurementRow timed(MultiplierSpec spec, int size, long seed, Phase phase, int repetition,
            long timeNanos, double checksum, RunStatus status) {
        return new MeasurementRow(spec, size, seed, repetition, phase, timeNanos, ABSENT, ABSENT, ABSENT,
                checksum, Verification.NOT_APPLICABLE, status);
    }

    public static MeasurementRow verification(MultiplierSpec spec, int size, long seed, Verification verification,
            double checksum) {
        return new MeasurementRow(spec, size, seed, NO_REPETITION, Phase.VERIFY, ABSENT, ABSENT, ABSENT, ABSENT,
                checksum, verification, RunStatus.OK);
    }

    public static MeasurementRow memory(MultiplierSpec spec, int size, long seed, long estimatedStorageBytes,
            long heapUsedBytes, long peakRssKilobytes) {
        return new MeasurementRow(spec, size, seed, NO_REPETITION, Phase.MEMORY, ABSENT, estimatedStorageBytes,
                heapUsedBytes, peakRssKilobytes, Double.NaN, Verification.NOT_APPLICABLE, RunStatus.OK);
    }

    public static MeasurementRow skipped(MultiplierSpec spec, int size, long seed, RunStatus status) {
        return new MeasurementRow(spec, size, seed, NO_REPETITION, Phase.MULTIPLY, ABSENT, ABSENT, ABSENT, ABSENT,
                Double.NaN, Verification.NOT_APPLICABLE, status);
    }

    public String toCsvLine() {
        return String.join(",",
                spec.variant().methodName(),
                Integer.toString(size),
                Integer.toString(spec.threadCount()),
                Long.toString(seed),
                NUMERIC_TYPE,
                repetition > NO_REPETITION ? Integer.toString(repetition) : EMPTY,
                phase.csvName(),
                optional(timeNanos),
                optional(estimatedStorageBytes),
                optional(heapUsedBytes),
                optional(peakRssKilobytes),
                Double.isNaN(checksum) ? EMPTY : Double.toString(checksum),
                verification.csvName(),
                status.name());
    }

    private static String optional(long value) {
        return value < 0 ? EMPTY : Long.toString(value);
    }
}
