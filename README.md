# Assignment 3 — Parallel Matrix Multiplication (Java)

BIG DATA · Grado en Ciencia e Ingeniería de Datos · ULPGC · 2026-2027
Author: Pablo Martín Perdomo — Assignment 3

Plain Java 17+ (no external libraries) plus Python scripts for tables and plots. The optional vectorisation extension is not part of this submission.

## Research question

How does the number of threads affect the runtime of a shared-memory dense matrix multiplication in Java, and when does parallel execution help?

## What is implemented

| Method | Description |
|---|---|
| `ikj_sequential` | Equivalent sequential baseline: i-k-j loop order over a flat row-major `double[]`, n³ multiply-adds. |
| `ikj_parallel` | Same kernel, same storage layout, same numeric type and same inputs. The rows of `C` are split into contiguous, balanced ranges, one task per thread, executed by a fixed thread pool. The number of threads is configurable (including 1). |

Both variants call the same `IkjRowKernel.multiplyRows`, so the algorithm is identical by construction. The partitioning gives each thread exclusive ownership of its rows of `C`, so no synchronisation on shared data is needed. Every output element is accumulated in the same order as in the sequential run, so results are bit-identical (checked by the self-tests).

## Reuse from Assignment 2

Reused from [Big-Data-Individual-Assignment-II](https://github.com/Pancrasi0/Big-Data-Individual-Assignment-II), commit `3408430bc67a82561e8619e546ce9e351c82dee0` (the Assignment 2 repository itself was not modified):

* The `ikj_reorder` kernel, as the algorithm of both variants. Verified bit-identical to the Assignment 2 kernel on sizes 1, 7, 64, 130 and 300.
* The seeded input generator (`java.util.Random`, values in [1, 2), A uses `seed`, B uses `seed+1`). Verified identical to the Assignment 2 dense generator for seeds 42 and 43.
* The tolerance (`|x−y| ≤ 1e-12 + 1e-9·max(|x|,|y|)`), the Freivalds verification, the memory metrics, the CSV conventions, the one-JVM-per-configuration protocol and the structure of the analysis script.

Everything was re-organised into packages and rewritten; nothing was copied unchanged.

## Layout

```
src/main/java/bigdata/matmul/
  Main.java
  kernel/      IkjRowKernel, SequentialMultiplier, ParallelMultiplier, RowPartitioner, RowRange,
               MatrixMultiplier, MultiplierFactory, MultiplierSpec, Variant
  data/        MatrixGenerator
  validation/  Tolerance, ReferenceMultiplier, FreivaldsVerifier, SelfTest
  benchmark/   BenchmarkSettings, BenchmarkSession, BenchmarkRunner, ConfigurationBenchmark, BudgetPolicy,
               MonotonicTimer, MemoryProbe, StorageEstimate, Checksum, SystemInfo, EnvironmentReport,
               MeasurementRow, CsvResultWriter, ResultSink, Phase, RunStatus, Verification
  cli/         CommandLineParser, CommandLine, Mode, Usage
scripts/  build.sh  run_quick.sh  run_all.sh  analyze.py
results/  (created by the runs)
```

## Build and run

Requirements: JDK 17+ (developed against JDK 21), Python 3 with `pandas` and `matplotlib` (`pip install -r requirements.txt`).

```bash
bash scripts/build.sh                      # compiles into ./out
java -cp out bigdata.matmul.Main test      # correctness self-tests only (242 checks)
bash scripts/run_quick.sh                  # short smoke run -> results_quick/
bash scripts/run_all.sh                    # full study, one fresh JVM per configuration -> results/
python3 scripts/analyze.py results         # summaries (CSV) and figures (PNG, PDF) in results/
```

`scripts/run_all.sh` contains the exact parameters of the study: JVM flags `-Xms4g -Xmx4g -XX:+UseParallelGC`, sizes 250/500/1000/2000/3000, thread counts 1/2/4/6/8/12/16, 3 warm-up runs, 7 measured repetitions, seed 42, 240 s per multiplication, 8192 MB estimated memory budget. All options: `java -cp out bigdata.matmul.Main --help`. A single invocation:

```bash
java -Xms4g -Xmx4g -XX:+UseParallelGC -cp out bigdata.matmul.Main benchmark \
  --sizes 2000 --variants parallel --threads 8 \
  --warmup 3 --reps 7 --seed 42 --max-seconds 240 --mem-budget-mb 8192 --out-dir results
```

Self-tests always run first and abort the benchmark on failure. The CSV is appended to, so delete `results/` before re-running the full study.

To pin the process to a set of logical processors, run the script under `taskset` and record it:

```bash
MATMUL_CPU_AFFINITY="0-11" taskset -c 0-11 bash scripts/run_all.sh
```

By default no affinity is set and the operating system schedules the threads.

## Inputs

* `java.util.Random` (the algorithm is fixed by the Java specification, so the same seed gives the same matrices on any JVM). A uses `seed`, B uses `seed+1` (default 42/43). Values are uniform in [1, 2). Both variants and all thread counts use exactly the same input matrices.
* Nothing needs to be downloaded; re-running with the same seed regenerates the inputs.

## Timing and memory protocol

* Timer: `System.nanoTime()` (monotonic). The timed region is one call to `multiply`: inputs are generated outside it, the output array is allocated outside it, the zero-fill of `C` is inside the kernel for both variants. The fixed thread pool is created and its threads are started before the first timed call; task submission and join are inside the timed region. This is also the case for `ikj_parallel` with one thread, which therefore exposes the framework overhead against `ikj_sequential`.
* Warm-up runs (default 2, 3 in `run_all.sh`) are written to the CSV with `phase=warmup` and excluded from the summaries. All raw samples are kept. The result is consumed through a checksum outside the timed region.
* Summary statistics: median and interquartile range. Speedup is `S(p) = Tseq(n) / Tp(n)` and efficiency `E(p) = S(p) / p`, both computed from medians. The range shown for the speedup is `Tseq_Q1 / Tp_Q3` to `Tseq_Q3 / Tp_Q1`, a rough indication and not a confidence interval.
* Thread counts: the study uses 1, 2, 4, 6, 8, 12 and 16 threads. Counts above the number of logical processors are oversubscription. No numerical library is used, so there are no internal library threads; the JVM's own JIT and GC threads exist but do not run in the timed region in a steady state.
* Memory columns: `est_storage_bytes` (3·8·n² bytes for A, B and C), `heap_used_bytes` (live JVM heap after GC with the matrices still reachable), `peak_rss_kb` (`VmHWM` from `/proc/self/status`, Linux only). These are different quantities and must not be compared as equals. `VmHWM` is the maximum of the whole process, so `run_all.sh` starts one JVM per configuration.

## Correctness

* `Main test`: 2×2 known product, identity and zero matrices, random matrices (n = 1, 2, 5, 17, 64, 70, 129) against an independent `double[][]` reference, bit-identity of every parallel configuration with the sequential result, thread counts 1, 2, 3 and 8 (including more threads than rows), partition coverage and balance, and Freivalds accept/reject checks. The output array is pre-filled with a sentinel to confirm it is fully overwritten.
* Tolerance: `|x−y| ≤ 1e-12 + 1e-9·max(|x|,|y|)`. Different summation orders change rounding only by a few ulps; the relative tolerance leaves a wide margin and still catches indexing errors, which produce O(1) errors.
* Benchmark sizes: every configuration is verified with a Freivalds check (`C·x = A·(B·x)`, two random vectors, O(n²)); the column `verified` is PASS or FAIL. `summary_correctness.csv` also lists the checksums, which must be equal across configurations of the same size.

## Budgets and limits

* `--max-seconds` bounds one multiplication. A running multiplication cannot be interrupted, so a run that exceeds it is recorded with its real time and `status=TIME_BUDGET_EXCEEDED`; larger sizes of that configuration are recorded as `SKIPPED_TIME_BUDGET`.
* `--mem-budget-mb` bounds the estimated memory of a configuration (also capped to 85 % of `-Xmx`); otherwise `SKIPPED_MEMORY_BUDGET`. An `OutOfMemoryError` is recorded as `OOM`. No time is ever invented for these cases; they are listed in `summary_limits.csv`.

## Raw data format (`raw_measurements.csv`)

`method, n, threads, seed, numeric_type, rep, phase, time_ns, est_storage_bytes, heap_used_bytes, peak_rss_kb, checksum, verified, status`

Phases: `warmup`, `multiply`, `verify`, `memory`. Methods: `ikj_sequential`, `ikj_parallel`.

`environment.txt` records CPU model, physical cores per socket, logical processors, RAM, OS, JVM and flags, CPU scaling governor, affinity, the timing boundary and the tolerance. It is written by the first JVM only and does not describe the run parameters, which are those of `scripts/run_all.sh`. The field `other_load_on_machine` must be filled in by hand.

## Analysis outputs (`python3 scripts/analyze.py results`)

`summary_runtime.csv`, `summary_scaling.csv` (median, IQR, speedup, efficiency, overhead of the one-thread parallel run), `summary_memory.csv`, `summary_correctness.csv`, `summary_limits.csv`, and the figures `fig_runtime_vs_threads`, `fig_speedup`, `fig_efficiency` (PNG and PDF, with the physical-core and logical-processor counts marked).

## Generative AI disclosure

Generative AI (Claude, Anthropic) was used to write the benchmark code, the measurement and analysis scripts and this README. The author is responsible for reviewing the code, running all experiments on their own machine, and for the measurements, the interpretation and the conclusions.
