package bigdata.matmul.benchmark;

import bigdata.matmul.data.MatrixGenerator;
import bigdata.matmul.kernel.MultiplierSpec;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BenchmarkRunner {

    private final BenchmarkSettings settings;
    private final BudgetPolicy budget;
    private final ResultSink sink;
    private final ConfigurationBenchmark configurationBenchmark;
    private final PrintStream progress;

    BenchmarkRunner(BenchmarkSettings settings, BudgetPolicy budget, ResultSink sink,
            ConfigurationBenchmark configurationBenchmark, PrintStream progress) {
        this.settings = settings;
        this.budget = budget;
        this.sink = sink;
        this.configurationBenchmark = configurationBenchmark;
        this.progress = progress;
    }

    void run() {
        final List<MultiplierSpec> specs = settings.multiplierSpecs();
        final Set<MultiplierSpec> stopped = new HashSet<>();
        for (int size : settings.sizes()) {
            if (!budget.allowsStorage(StorageEstimate.bytes(size))) {
                specs.forEach(spec -> recordSkipped(spec, size, RunStatus.SKIPPED_MEMORY_BUDGET));
                continue;
            }
            final double[] a = MatrixGenerator.random(size, settings.seed());
            final double[] b = MatrixGenerator.random(size, settings.secondMatrixSeed());
            for (MultiplierSpec spec : specs) {
                if (stopped.contains(spec)) {
                    recordSkipped(spec, size, RunStatus.SKIPPED_TIME_BUDGET);
                } else if (configurationBenchmark.run(spec, a, b, size)) {
                    progress.printf("  n=%d %s done%n", size, spec.label());
                } else {
                    stopped.add(spec);
                    progress.printf("  n=%d %s stopped (time budget or memory failure)%n", size, spec.label());
                }
            }
        }
    }

    private void recordSkipped(MultiplierSpec spec, int size, RunStatus status) {
        sink.write(MeasurementRow.skipped(spec, size, settings.seed(), status));
        progress.printf("  n=%d %s skipped (%s)%n", size, spec.label(), status);
    }
}
