package bigdata.matmul.cli;

public final class Usage {

    private static final String TEXT = """
            Usage: java -cp out bigdata.matmul.Main <mode> [options]

            Modes:
              test        run the correctness self-tests only
              benchmark   self-tests, then the sequential and parallel multiplication study

            Options (comma-separated lists have no spaces):
              --sizes 250,500,1000,2000    matrix sizes n                     (default 250,500,1000,2000)
              --variants sequential,parallel  variants to run                 (default both)
              --threads 1,2,4,...          thread counts for the parallel variant
                                           (default: powers of two below the logical processor count,
                                           the logical processor count, and twice that count)
              --warmup 2                   warm-up runs per configuration, excluded from samples (default 2)
              --reps 5                     measured repetitions per configuration (default 5)
              --seed 42                    seed of matrix A; matrix B uses seed+1 (default 42)
              --max-seconds 120            time budget for ONE multiplication (default 120)
              --mem-budget-mb 3072         estimated memory budget per configuration (default 3072)
              --out-dir results            output directory (default results)
              --out-file raw_measurements.csv  CSV file name, appended to if it exists
            """;

    private Usage() {
    }

    public static String text() {
        return TEXT;
    }
}
