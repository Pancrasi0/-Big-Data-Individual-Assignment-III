package bigdata.matmul.cli;

import bigdata.matmul.benchmark.BenchmarkSettings;
import bigdata.matmul.kernel.Variant;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class CommandLineParser {

    private static final String SIZES = "--sizes";
    private static final String THREADS = "--threads";
    private static final String VARIANTS = "--variants";
    private static final String WARMUP = "--warmup";
    private static final String REPS = "--reps";
    private static final String SEED = "--seed";
    private static final String MAX_SECONDS = "--max-seconds";
    private static final String MEMORY_BUDGET = "--mem-budget-mb";
    private static final String OUT_DIR = "--out-dir";
    private static final String OUT_FILE = "--out-file";
    private static final Set<String> KNOWN_OPTIONS = Set.of(SIZES, THREADS, VARIANTS, WARMUP, REPS, SEED,
            MAX_SECONDS, MEMORY_BUDGET, OUT_DIR, OUT_FILE);
    private static final String LIST_SEPARATOR = ",";
    private static final int MODE_ARGUMENT_COUNT = 1;
    private static final int OPTION_TOKEN_COUNT = 2;

    private CommandLineParser() {
    }

    public static CommandLine parse(String[] args) {
        if (args.length == 0) {
            return new CommandLine(Mode.HELP, BenchmarkSettings.defaults());
        }
        final Mode mode = Mode.fromText(args[0]);
        return new CommandLine(mode, buildSettings(readOptions(args)));
    }

    private static Map<String, String> readOptions(String[] args) {
        final Map<String, String> options = new HashMap<>();
        for (int index = MODE_ARGUMENT_COUNT; index < args.length; index += OPTION_TOKEN_COUNT) {
            final String key = args[index];
            if (!KNOWN_OPTIONS.contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            options.put(key, args[index + 1]);
        }
        return options;
    }

    private static BenchmarkSettings buildSettings(Map<String, String> options) {
        final BenchmarkSettings defaults = BenchmarkSettings.defaults();
        return new BenchmarkSettings(
                option(options, SIZES, CommandLineParser::parseIntegers, defaults.sizes()),
                option(options, THREADS, CommandLineParser::parseIntegers, defaults.threadCounts()),
                option(options, VARIANTS, CommandLineParser::parseVariants, defaults.variants()),
                option(options, WARMUP, Integer::parseInt, defaults.warmupRuns()),
                option(options, REPS, Integer::parseInt, defaults.repetitions()),
                option(options, SEED, Long::parseLong, defaults.seed()),
                option(options, MAX_SECONDS, Double::parseDouble, defaults.maxSecondsPerMultiplication()),
                option(options, MEMORY_BUDGET, Long::parseLong, defaults.memoryBudgetMegabytes()),
                option(options, OUT_DIR, Path::of, defaults.outputDirectory()),
                option(options, OUT_FILE, Function.identity(), defaults.outputFileName()));
    }

    private static <T> T option(Map<String, String> options, String key, Function<String, T> parser, T fallback) {
        final String text = options.get(key);
        return text == null ? fallback : parser.apply(text);
    }

    private static List<Integer> parseIntegers(String text) {
        return Arrays.stream(text.split(LIST_SEPARATOR))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static Set<Variant> parseVariants(String text) {
        final Set<Variant> variants = EnumSet.noneOf(Variant.class);
        for (String token : text.split(LIST_SEPARATOR)) {
            variants.add(Variant.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        }
        return variants;
    }
}
