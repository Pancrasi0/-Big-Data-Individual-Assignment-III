package bigdata.matmul.benchmark;

import java.util.concurrent.TimeUnit;

public final class BudgetPolicy {

    private static final double HEAP_USAGE_FRACTION = 0.85;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final double NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final long usableBytes;
    private final long maxNanos;

    public BudgetPolicy(BenchmarkSettings settings, long maxHeapBytes) {
        final long configuredBytes = settings.memoryBudgetMegabytes() * BYTES_PER_MEGABYTE;
        final long heapBytes = (long) (maxHeapBytes * HEAP_USAGE_FRACTION);
        this.usableBytes = Math.min(configuredBytes, heapBytes);
        this.maxNanos = (long) (settings.maxSecondsPerMultiplication() * NANOS_PER_SECOND);
    }

    public boolean allowsStorage(long bytes) {
        return bytes <= usableBytes;
    }

    public RunStatus statusFor(long elapsedNanos) {
        return elapsedNanos > maxNanos ? RunStatus.TIME_BUDGET_EXCEEDED : RunStatus.OK;
    }
}
