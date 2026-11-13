package bigdata.matmul.kernel;

public final class MultiplierFactory {

    public MatrixMultiplier create(MultiplierSpec spec) {
        return switch (spec.variant()) {
            case SEQUENTIAL -> new SequentialMultiplier();
            case PARALLEL -> new ParallelMultiplier(spec.threadCount());
        };
    }
}
