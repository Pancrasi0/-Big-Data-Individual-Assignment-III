package bigdata.matmul.kernel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ParallelMultiplier implements MatrixMultiplier {

    private static final long KEEP_ALIVE_MILLIS = 0L;
    private static final int MIN_THREAD_COUNT = 1;

    private final int threadCount;
    private final ThreadPoolExecutor executor;

    public ParallelMultiplier(int threadCount) {
        if (threadCount < MIN_THREAD_COUNT) {
            throw new IllegalArgumentException("threadCount must be >= " + MIN_THREAD_COUNT);
        }
        this.threadCount = threadCount;
        this.executor = new ThreadPoolExecutor(threadCount, threadCount, KEEP_ALIVE_MILLIS,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        this.executor.prestartAllCoreThreads();
    }

    @Override
    public void multiply(double[] a, double[] b, double[] c, int size) {
        final List<Callable<Void>> tasks = new ArrayList<>(threadCount);
        for (RowRange rows : RowPartitioner.partition(size, threadCount)) {
            tasks.add(() -> {
                IkjRowKernel.multiplyRows(a, b, c, size, rows);
                return null;
            });
        }
        awaitAll(tasks);
    }

    private void awaitAll(List<Callable<Void>> tasks) {
        try {
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for worker threads", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A worker thread failed", e.getCause());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
