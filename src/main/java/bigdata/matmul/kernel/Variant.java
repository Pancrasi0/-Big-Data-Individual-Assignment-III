package bigdata.matmul.kernel;

public enum Variant {
    SEQUENTIAL("ikj_sequential"),
    PARALLEL("ikj_parallel");

    private final String methodName;

    Variant(String methodName) {
        this.methodName = methodName;
    }

    public String methodName() {
        return methodName;
    }
}
