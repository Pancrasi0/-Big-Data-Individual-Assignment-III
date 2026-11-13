# Assignment 3 — Parallel Matrix Multiplication (Java)

BIG DATA · Grado en Ciencia e Ingeniería de Datos · ULPGC · 2026-2027
Author: Pablo Martín Perdomo — Assignment 3

Plain Java 21 (no external libraries), plus Python scripts for tables and plots. The report is in [`report/`](report/) (PDF and LaTeX source). The optional vectorisation extension is not part of this submission.

## Research question

How does the number of threads affect the runtime of a Java shared-memory dense matrix multiplication on a 6-core, 12-thread desktop CPU, and for which matrix sizes and thread counts does parallel execution help?

## Main results (3 JVM launches per configuration; see the report for details)

* All 120 launches give bit-identical results (Freivalds check passed, equal checksums).
* For n ≥ 500 the speedup is nearly ideal up to the 6 physical cores (efficiency 0.96–0.99).
* 12 threads give the shortest times for n ≥ 1000 (largest speedup 6.79, n = 2000), but only 3–14 % faster than 6 threads.
* Two threads pinned to one physical core give 1.047 (n = 1000) and 1.155 (n = 2000) times the throughput of one thread, which matches the gain from 6 to 12 threads.
* 8 threads are slower than 6, and 16 threads (oversubscription) are 5–35 % slower than 12 for n ≥ 500.
* Some JVM launches run 16–26 % slower for their whole duration (seen for n = 500 and 1000). This explains the anomalous n = 1000, 2-thread result of the first launch (S = 1.63; S = 2.00 over 3 launches).

## What is implemented

| Method | Description |
|---|---|
| `ikj_sequential` | Equivalent sequential baseline: i-k-j loop order over a flat row-major `double[]`, n³ multiply-adds. |
| `ikj_parallel` | Same kernel, same storage layout, same numeric type and same inputs. The rows of `C` are split into contiguous, balanced ranges, one task per thread, executed by a fixed thread pool (static scheduling). The number of threads is configurable (including 1). |

Both variants call the same `IkjRowKernel.multiplyRows`, so the algorithm is identical by construction. The partitioning gives each thread exclusive ownership of its rows of `C`, so no synchronisation on shared data is needed. Every output element is accumulated in the same order as in the sequential run, so results are bit-identical (checked by the self-tests).

## Reuse from Assignment 2

Reused from [Big-Data-Individual-Assignment-II](https://github.com/Pancrasi0/Big-Data-Individual-Assignment-II), commit `3408430bc67a82561e8619e546ce9e351c82dee0` (the Assignment 2 repository itself was not modified):

* The `ikj_reorder` kernel, as the algorithm of both variants. Verified bit-identical to the Assignment 2 kernel on sizes 1, 7, 64, 130 and 300.
* The seeded input generator (`java.util.Random`, values in [1, 2), A uses `seed`, B uses `seed+1`). Verified identical to the Assignment 2 dense generator for seeds 42 and 43.
* The tolerance (`|x−y| ≤ 1e-12 + 1e-9·max(|x|,|y|)`), the Freivalds verification, the memory metrics, the CSV conventions, the one-JVM-per-configuration protocol and the structure of the analysis script.

Everything was re-organised into packages and rewritten; nothing was copied unchanged.

## Repository layout

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
scripts/
  build.sh            compile into ./out
  run_quick.sh        short smoke run (not used in the report)
  run_all.sh          main study, launch 1
  analyze.py          summaries and figures of launch 1 alone
  run_n1000.sh        follow-up of n = 1000 (5 launches, with and without taskset)
  analyze_n1000.py    analysis of that follow-up
  run_rigor.py        main study, launches 2 and 3, plus the n = 2000 Hyper-Threading test
  analyze_rigor.py    combines launches 1-3; tables and Figures 1-3 of the report
results/              launch 1 of the main study
results_n1000/        n = 1000 follow-up (one directory per experiment and launch)
results_rigor/        launches 2 and 3, Hyper-Threading test, and analysis/ (combined summaries)
results_quick/        output of run_quick.sh (smoke test only)
report/               report PDF and LaTeX source
```

## Requirements

* **Java:** JDK 21. The measurements used OpenJDK 64-Bit Server VM 21.0.12.1 (Arch Linux build).
* **Python 3** with `pandas` and `matplotlib` (`pip install -r requirements.txt`).
* **Linux** for the full protocol: the peak resident memory is read from `/proc/self/status` (`VmHWM`), the CPU topology from `/sys/devices/system/cpu`, and the pinned experiments use `taskset` (util-linux). On other systems the benchmark runs, but `peak_rss_kb` is empty and the pinned experiments cannot run.
* About 4.5 GB of free RAM (the JVM is started with `-Xms4g -Xmx4g`).

## Build and test

```bash
bash scripts/build.sh                      # compiles into ./out
java -cp out bigdata.matmul.Main test      # correctness self-tests only (242 checks)
bash scripts/run_quick.sh                  # short smoke run -> results_quick/
```

## Experiments

Run every experiment with the CPU governor set to `performance` (`sudo cpupower frequency-set -g performance`) and no other applications open. Durations are those observed on the test machine.

| Experiment | Run | Analyse | Output | Duration |
|---|---|---|---|---|
| Main study, launch 1 | `bash scripts/run_all.sh` | `python3 scripts/analyze.py results` | `results/` | about 15 min |
| Follow-up of n = 1000 | `bash scripts/run_n1000.sh` | `python3 scripts/analyze_n1000.py results_n1000` | `results_n1000/` | about 3 min |
| Main study, launches 2 and 3 + Hyper-Threading test | `python3 scripts/run_rigor.py` | `python3 scripts/analyze_rigor.py` | `results_rigor/` | about 35 min |

`analyze_rigor.py` reads `results/` (launch 1) and `results_rigor/` (launches 2 and 3). It produces the tables and Figures 1–3 of the report. Before re-running, move or delete the old output. `run_all.sh` appends to `results/raw_measurements.csv`. `run_rigor.py` refuses to start if `results_rigor/` exists. `run_n1000.sh` deletes `results_n1000/` at start.

### Why there are three experiments

1. **Main study, launch 1** (`run_all.sh`): 5 sizes (250, 500, 1000, 2000, 3000) × (sequential + parallel with 1, 2, 4, 6, 8, 12 and 16 threads), one fresh JVM per configuration.
2. **Follow-up of n = 1000** (`run_n1000.sh`): launch 1 gave an anomalous speedup for n = 1000 with 2 threads (S = 1.63). This experiment re-measures n = 1000 in 5 launches per configuration. **A:** sequential and parallel with 1, 2 and 4 threads, without affinity. **B:** 2 threads pinned with `taskset` to two physical cores (CPUs 0 and 1), 2 threads pinned to the two logical processors of one core (CPUs 0 and 6), and 1 thread pinned to CPU 0. It showed that some JVM launches run 16–26 % slower for their whole duration.
3. **Launches 2 and 3 + Hyper-Threading test** (`run_rigor.py`): because of that finding, every configuration of the main study was measured in two more launches. The same script tests the core-sharing hypothesis at n = 2000 with the three pinned placements of experiment B, 3 launches each. Options: `--quick` leaves n = 3000 out of the extra launches, `--launches 2` adds only launch 2, `--skip-ht` skips the Hyper-Threading test.

The script detects which logical processors share a physical core from `/sys`. On the test machine CPUs c and c+6 share core c (see `lscpu_e.txt`).

A single invocation of the benchmark (all options: `java -cp out bigdata.matmul.Main --help`):

```bash
java -Xms4g -Xmx4g -XX:+UseParallelGC -cp out bigdata.matmul.Main benchmark \
  --sizes 2000 --variants parallel --threads 8 \
  --warmup 3 --reps 7 --seed 42 --max-seconds 240 --mem-budget-mb 8192 --out-dir results
```

Self-tests always run first and abort the benchmark on failure.

## Inputs

* `java.util.Random`: the algorithm is fixed by the Java specification, so the same seed gives the same matrices on any JVM. A uses `seed`, B uses `seed+1` (42 and 43). Values are uniform in [1, 2).
* Both variants and all thread counts use exactly the same input matrices.
* Nothing needs to be downloaded; re-running with the same seed regenerates the inputs.

## Timing, statistics and memory

* **Timer:** `System.nanoTime()` (monotonic).
* **Timed region:** one call to `multiply`.
  * Outside it: generating the inputs, allocating the output array, and creating and starting the fixed thread pool.
  * Inside it: the zero-fill of `C` (inside the kernel, for both variants), and task submission and join. This also holds for `ikj_parallel` with one thread, which therefore exposes the framework overhead against `ikj_sequential`.
* **Runs per launch:** 3 warm-up runs, written to the CSV with `phase=warmup` and excluded from the statistics, and 7 measured repetitions. All raw samples are kept. The result is consumed through a checksum computed outside the timed region.
* **Launches:** one fresh JVM per configuration and launch. Every configuration of the main study has 3 launches.
* **Statistics:** median and interquartile range inside each launch. The summary of a configuration is the median of its launch medians, with the range of the launch medians as the spread between launches. Speedup is `S(p) = Tseq(n) / Tp(n)` and efficiency `E(p) = S(p) / p`, both from the summary medians. A launch is called *slow* when its median exceeds the summary by more than 10 %.
* **Thread counts:** 6 equals the physical cores, 12 the logical processors, and 16 oversubscribes the machine. No numerical library is used, so there are no internal library threads. The JVM's own JIT and GC threads exist but do not run in the timed region in a steady state.
* **Memory columns:** these are different quantities and must not be compared as equals.
  * `est_storage_bytes`: 3·8·n² bytes for A, B and C.
  * `heap_used_bytes`: live JVM heap after GC, with the matrices still reachable.
  * `peak_rss_kb`: `VmHWM` of the whole process; this is why each JVM runs only one configuration.

## Correctness

* **Self-tests** (`Main test`, and before every benchmark):
  * a 2×2 known product, identity and zero matrices;
  * random matrices (n = 1, 2, 5, 17, 64, 70, 129) against an independent `double[][]` reference;
  * bit-identity of every parallel configuration with the sequential result, for thread counts 1, 2, 3 and 8 (including more threads than rows);
  * partition coverage and balance, and Freivalds accept/reject checks.

  The output array is pre-filled with a sentinel to confirm that it is fully overwritten.
* **Tolerance:** `|x−y| ≤ 1e-12 + 1e-9·max(|x|,|y|)`. Different summation orders change rounding only by a few ulps; the relative tolerance leaves a wide margin and still catches indexing errors, which produce O(1) errors.
* **Benchmark sizes:** every configuration and launch is verified with a Freivalds check (`C·x = A·(B·x)`, two random vectors, O(n²)); the column `verified` is PASS or FAIL. The checksums must be equal across all configurations and launches of the same size.

## Budgets and limits

* `--max-seconds` bounds one multiplication. A running multiplication cannot be interrupted, so a run that exceeds it is recorded with its real time and `status=TIME_BUDGET_EXCEEDED`; larger sizes of that configuration are recorded as `SKIPPED_TIME_BUDGET`.
* `--mem-budget-mb` bounds the estimated memory of a configuration (also capped to 85 % of `-Xmx`); otherwise the status is `SKIPPED_MEMORY_BUDGET`. An `OutOfMemoryError` is recorded as `OOM`.
* No time is ever invented for these cases. In this study no configuration reached a budget: the longest sample was 18.9 s against the 240 s limit.

## Raw data format

Every output directory holds a `raw_measurements.csv` and an `environment.txt`:

`method, n, threads, seed, numeric_type, rep, phase, time_ns, est_storage_bytes, heap_used_bytes, peak_rss_kb, checksum, verified, status`

* **Phases:** `warmup`, `multiply`, `verify`, `memory`.
* **Methods:** `ikj_sequential`, `ikj_parallel`.

`environment.txt` is written by the first JVM of each directory. It records:
* the hardware and OS: CPU model, physical cores, logical processors, RAM, OS;
* the JVM and its flags;
* the run settings: CPU scaling governor, affinity (`MATMUL_CPU_AFFINITY`), timing boundary and tolerance.

The field `other_load_on_machine` is filled in by hand. The run parameters are those of the scripts. `results_n1000/` and `results_rigor/` contain one sub-directory per experiment and launch, plus:
* `lscpu_e.txt`: the CPU topology;
* the CPU pairs used (`pairs.txt`, `settings.txt`);
* the run logs (`run_n1000.log`, `run_rigor.log`).

## Analysis outputs

* **`results/`** (`analyze.py`, launch 1 only): `summary_runtime.csv`, `summary_scaling.csv`, `summary_memory.csv`, `summary_correctness.csv`, `summary_limits.csv` and `fig_runtime_vs_threads`, `fig_speedup`, `fig_efficiency` (PNG and PDF).
* **`results_n1000/`** (`analyze_n1000.py`): `summary_per_launch.csv`, `summary_n1000.csv` and `fig_n1000_launches` (Figure 4 of the report).
* **`results_rigor/analysis/`** (`analyze_rigor.py`, launches 1–3):
  * `per_launch_runtime.csv`, `summary_runtime.csv` (with the change against launch 1), `summary_scaling.csv`, `summary_memory.csv`;
  * `ht_per_launch.csv`, `ht_summary.csv`;
  * `summary.md`, a readable summary of all tables;
  * the figures `fig_runtime_vs_threads`, `fig_speedup` and `fig_efficiency` (Figures 1–3 of the report).

## Test environment

| Item | Value |
|---|---|
| CPU | Intel Core i5-10400F @ 2.90 GHz, 6 physical cores, 12 logical processors |
| RAM | 16,568,619,008 bytes (15.4 GiB) |
| OS | Linux 7.3.3-1-cachyos (CachyOS), amd64 |
| JVM | OpenJDK 64-Bit Server VM 21.0.12.1 (Arch Linux build), `-Xms4g -Xmx4g -XX:+UseParallelGC` |
| CPU governor | performance |
| Other load | no other applications running (only the terminal) |
| Dates | 13 November 2026, three sessions: launch 1 (from 16:48 UTC), n = 1000 follow-up (22:05 UTC), launches 2–3 and Hyper-Threading test (22:36–23:11 UTC) |

## Generative AI disclosure

Generative AI (Claude, Anthropic) was used for the following:
* writing the benchmark code and the measurement and analysis scripts;
* designing the follow-up experiments;
* drafting the report and this README.

The author reviewed the code and ran all experiments on their own machine. The author is responsible for the measurements, their interpretation and the conclusions.
