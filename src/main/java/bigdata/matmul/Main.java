package bigdata.matmul;

import bigdata.matmul.benchmark.BenchmarkSession;
import bigdata.matmul.benchmark.BenchmarkSettings;
import bigdata.matmul.cli.CommandLine;
import bigdata.matmul.cli.CommandLineParser;
import bigdata.matmul.cli.Usage;
import bigdata.matmul.kernel.MultiplierFactory;
import bigdata.matmul.validation.SelfTest;
import java.io.IOException;

public final class Main {

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        final CommandLine commandLine;
        try {
            commandLine = CommandLineParser.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.print(Usage.text());
            return EXIT_USAGE;
        }
        return switch (commandLine.mode()) {
            case HELP -> {
                System.out.print(Usage.text());
                yield EXIT_OK;
            }
            case TEST -> exitCode(new SelfTest(System.out, new MultiplierFactory()).run());
            case BENCHMARK -> runBenchmark(commandLine.settings());
        };
    }

    private static int runBenchmark(BenchmarkSettings settings) {
        try {
            if (new BenchmarkSession(settings, System.out).run()) {
                return EXIT_OK;
            }
            System.err.println("Self-tests failed: refusing to benchmark.");
            return EXIT_FAILURE;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    private static int exitCode(boolean success) {
        return success ? EXIT_OK : EXIT_FAILURE;
    }
}
