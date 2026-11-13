package bigdata.matmul.benchmark;

import bigdata.matmul.kernel.MatrixMultiplier;
import bigdata.matmul.kernel.MultiplierFactory;
import bigdata.matmul.kernel.MultiplierSpec;
import bigdata.matmul.validation.FreivaldsVerifier;
import java.lang.ref.Reference;

final class ConfigurationBenchmark {

    private final BenchmarkSettings settings;
    private final MultiplierFactory factory;
    private final BudgetPolicy budget;
    private final ResultSink sink;
    private final MemoryProbe memoryProbe;

    ConfigurationBenchmark(BenchmarkSettings settings, MultiplierFactory factory, BudgetPolicy budget,
            ResultSink sink, MemoryProbe memoryProbe) {
        this.settings = settings;
        this.factory = factory;
        this.budget = budget;
        this.sink = sink;
        this.memoryProbe = memoryProbe;
    }

    boolean run(MultiplierSpec spec, double[] a, double[] b, int size) {
        try (MatrixMultiplier multiplier = factory.create(spec)) {
            final double[] c = new double[size * size];
            final Runnable task = () -> multiplier.multiply(a, b, c, size);
            final boolean withinBudget = runWarmup(spec, size, task) && runMeasured(spec, size, task, c);
            recordVerification(spec, size, a, b, c);
            recordMemory(spec, size);
            Reference.reachabilityFence(a);
            Reference.reachabilityFence(b);
            Reference.reachabilityFence(c);
            return withinBudget;
        } catch (OutOfMemoryError error) {
            sink.write(MeasurementRow.skipped(spec, size, settings.seed(), RunStatus.OOM));
            return false;
        }
    }

    private boolean runWarmup(MultiplierSpec spec, int size, Runnable task) {
        for (int run = 1; run <= settings.warmupRuns(); run++) {
            final long nanos = MonotonicTimer.measureNanos(task);
            final RunStatus status = budget.statusFor(nanos);
            sink.write(MeasurementRow.timed(spec, size, settings.seed(), Phase.WARMUP, run, nanos, Double.NaN, status));
            if (status != RunStatus.OK) {
                return false;
            }
        }
        return true;
    }

    private boolean runMeasured(MultiplierSpec spec, int size, Runnable task, double[] c) {
        for (int repetition = 1; repetition <= settings.repetitions(); repetition++) {
            final long nanos = MonotonicTimer.measureNanos(task);
            final double checksum = Checksum.of(c);
            final RunStatus status = budget.statusFor(nanos);
            sink.write(MeasurementRow.timed(spec, size, settings.seed(), Phase.MULTIPLY, repetition, nanos,
                    checksum, status));
            if (status != RunStatus.OK) {
                return false;
            }
        }
        return true;
    }

    private void recordVerification(MultiplierSpec spec, int size, double[] a, double[] b, double[] c) {
        final boolean passed = FreivaldsVerifier.accepts(a, b, c, size, settings.verificationSeed());
        if (!passed) {
            System.err.println("WARNING: verification FAILED for " + spec.label() + " n=" + size);
        }
        sink.write(MeasurementRow.verification(spec, size, settings.seed(), Verification.of(passed), Checksum.of(c)));
    }

    private void recordMemory(MultiplierSpec spec, int size) {
        sink.write(MeasurementRow.memory(spec, size, settings.seed(), StorageEstimate.bytes(size),
                memoryProbe.liveHeapBytesAfterGc(), memoryProbe.peakResidentKilobytes()));
    }
}
