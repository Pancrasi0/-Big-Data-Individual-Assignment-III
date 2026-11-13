package bigdata.matmul.benchmark;

import bigdata.matmul.kernel.MultiplierFactory;
import bigdata.matmul.validation.SelfTest;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

public final class BenchmarkSession {

    private final BenchmarkSettings settings;
    private final PrintStream out;

    public BenchmarkSession(BenchmarkSettings settings, PrintStream out) {
        this.settings = settings;
        this.out = out;
    }

    public boolean run() throws IOException {
        Files.createDirectories(settings.outputDirectory());
        final MultiplierFactory factory = new MultiplierFactory();
        if (!new SelfTest(out, factory).run()) {
            return false;
        }
        final MemoryProbe memoryProbe = new MemoryProbe();
        new EnvironmentReport(new SystemInfo())
                .writeIfAbsent(settings.environmentFile(), memoryProbe.liveHeapBytesAfterGc());
        try (CsvResultWriter writer = new CsvResultWriter(settings.resultsFile())) {
            final BudgetPolicy budget = new BudgetPolicy(settings, Runtime.getRuntime().maxMemory());
            final ConfigurationBenchmark configurationBenchmark =
                    new ConfigurationBenchmark(settings, factory, budget, writer, memoryProbe);
            new BenchmarkRunner(settings, budget, writer, configurationBenchmark, out).run();
        }
        out.printf("Raw measurements: %s (sink=%s)%n", settings.resultsFile(), Checksum.sinkValue());
        return true;
    }
}
