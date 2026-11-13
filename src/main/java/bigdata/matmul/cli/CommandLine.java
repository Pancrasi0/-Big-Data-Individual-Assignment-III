package bigdata.matmul.cli;

import bigdata.matmul.benchmark.BenchmarkSettings;

public record CommandLine(Mode mode, BenchmarkSettings settings) {
}
