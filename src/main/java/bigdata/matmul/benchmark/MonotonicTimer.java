package bigdata.matmul.benchmark;

public final class MonotonicTimer {

    private MonotonicTimer() {
    }

    public static long measureNanos(Runnable task) {
        final long start = System.nanoTime();
        task.run();
        return System.nanoTime() - start;
    }
}
