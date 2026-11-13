package bigdata.matmul.benchmark;

public enum RunStatus {
    OK,
    TIME_BUDGET_EXCEEDED,
    SKIPPED_TIME_BUDGET,
    SKIPPED_MEMORY_BUDGET,
    OOM
}
