#!/usr/bin/env python3
import re
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib.ticker import ScalarFormatter

NANOSECONDS_PER_SECOND = 1e9
LOWER_QUARTILE = 0.25
UPPER_QUARTILE = 0.75
PERCENT = 100.0
SEQUENTIAL_METHOD = "ikj_sequential"
PARALLEL_METHOD = "ikj_parallel"
RAW_FILE = "raw_measurements.csv"
ENVIRONMENT_FILE = "environment.txt"
STATUS_OK = "OK"
PHASE_MULTIPLY = "multiply"
PHASE_WARMUP = "warmup"
PHASE_VERIFY = "verify"
PHASE_MEMORY = "memory"
VERIFICATION_FAIL = "FAIL"
PHYSICAL_CORES_KEY = "physical_cores_per_socket"
LOGICAL_PROCESSORS_KEY = "logical_processors"
FIGURE_SIZE = (7.5, 4.8)
FIGURE_DPI = 150
GRID_ALPHA = 0.3
LOG_BASE = 2
CAP_SIZE = 2
LEGEND_FONT_SIZE = 8
IDEAL_LINE_STYLE = "--"
PHYSICAL_LINE_STYLE = ":"
LOGICAL_LINE_STYLE = "-."
REFERENCE_LINE_COLOR = "gray"
IDEAL_EFFICIENCY = 1.0
FIGURE_FORMATS = ("png", "pdf")
RUNTIME_KEYS = ["method", "n", "threads"]
SCALING_COLUMNS = [
    "n", "threads", "reps", "median_s", "q1_s", "q3_s", "iqr_s", "seq_median_s",
    "speedup", "speedup_low", "speedup_high", "efficiency", "overhead_vs_sequential_pct",
]


def load_raw(results_dir):
    frame = pd.read_csv(results_dir / RAW_FILE)
    frame["time_s"] = frame["time_ns"] / NANOSECONDS_PER_SECOND
    return frame


def read_environment_counts(results_dir):
    counts = {}
    path = results_dir / ENVIRONMENT_FILE
    if not path.exists():
        return counts
    text = path.read_text()
    for key in (PHYSICAL_CORES_KEY, LOGICAL_PROCESSORS_KEY):
        match = re.search(rf"^{key}\s*=\s*(\d+)", text, re.MULTILINE)
        if match:
            counts[key] = int(match.group(1))
    return counts


def summarise(frame, keys):
    grouped = frame.groupby(keys)["time_s"]
    summary = grouped.agg(reps="count", median_s="median", min_s="min", max_s="max")
    summary["q1_s"] = grouped.quantile(LOWER_QUARTILE)
    summary["q3_s"] = grouped.quantile(UPPER_QUARTILE)
    summary["iqr_s"] = summary["q3_s"] - summary["q1_s"]
    return summary.reset_index()


def build_scaling(runtime):
    sequential = runtime[runtime["method"] == SEQUENTIAL_METHOD].set_index("n")
    parallel = runtime[runtime["method"] == PARALLEL_METHOD].copy()
    parallel = parallel[parallel["n"].isin(sequential.index)]
    parallel["seq_median_s"] = parallel["n"].map(sequential["median_s"])
    seq_q1 = parallel["n"].map(sequential["q1_s"])
    seq_q3 = parallel["n"].map(sequential["q3_s"])
    parallel["speedup"] = parallel["seq_median_s"] / parallel["median_s"]
    parallel["speedup_low"] = seq_q1 / parallel["q3_s"]
    parallel["speedup_high"] = seq_q3 / parallel["q1_s"]
    parallel["efficiency"] = parallel["speedup"] / parallel["threads"]
    parallel["overhead_vs_sequential_pct"] = (parallel["median_s"] / parallel["seq_median_s"] - 1) * PERCENT
    return parallel.sort_values(["n", "threads"])[SCALING_COLUMNS]


def draw_hardware_lines(axis, counts):
    if PHYSICAL_CORES_KEY in counts:
        axis.axvline(counts[PHYSICAL_CORES_KEY], color=REFERENCE_LINE_COLOR, linestyle=PHYSICAL_LINE_STYLE,
                     label="physical cores")
    if LOGICAL_PROCESSORS_KEY in counts:
        axis.axvline(counts[LOGICAL_PROCESSORS_KEY], color=REFERENCE_LINE_COLOR, linestyle=LOGICAL_LINE_STYLE,
                     label="logical processors")


def finish_axis(axis, thread_values, ylabel, title):
    axis.set_xscale("log", base=LOG_BASE)
    axis.set_xticks(sorted(thread_values))
    axis.xaxis.set_major_formatter(ScalarFormatter())
    axis.set(xlabel="threads", ylabel=ylabel, title=title)
    axis.grid(True, which="both", alpha=GRID_ALPHA)
    axis.legend(fontsize=LEGEND_FONT_SIZE)


def save_figure(figure, results_dir, name):
    figure.tight_layout()
    for extension in FIGURE_FORMATS:
        figure.savefig(results_dir / f"{name}.{extension}", dpi=FIGURE_DPI)
    plt.close(figure)


def asymmetric_error(centre, low, high):
    return [(centre - low).clip(lower=0), (high - centre).clip(lower=0)]


def plot_runtime(scaling, results_dir, counts):
    figure, axis = plt.subplots(figsize=FIGURE_SIZE)
    for size, group in scaling.groupby("n"):
        line = axis.errorbar(group["threads"], group["median_s"],
                             yerr=asymmetric_error(group["median_s"], group["q1_s"], group["q3_s"]),
                             marker="o", capsize=CAP_SIZE, label=f"n = {size}")
        axis.axhline(group["seq_median_s"].iloc[0], color=line[0].get_color(), linestyle=IDEAL_LINE_STYLE,
                     alpha=GRID_ALPHA)
    axis.set_yscale("log")
    draw_hardware_lines(axis, counts)
    finish_axis(axis, scaling["threads"].unique(), "median time per multiplication (s)",
                "Runtime vs threads (bars: IQR, dashed: sequential)")
    save_figure(figure, results_dir, "fig_runtime_vs_threads")


def plot_speedup(scaling, results_dir, counts):
    figure, axis = plt.subplots(figsize=FIGURE_SIZE)
    for size, group in scaling.groupby("n"):
        axis.errorbar(group["threads"], group["speedup"],
                      yerr=asymmetric_error(group["speedup"], group["speedup_low"], group["speedup_high"]),
                      marker="o", capsize=CAP_SIZE, label=f"n = {size}")
    threads = sorted(scaling["threads"].unique())
    axis.plot(threads, threads, IDEAL_LINE_STYLE, color=REFERENCE_LINE_COLOR, label="ideal S(p) = p")
    axis.set_yscale("log", base=LOG_BASE)
    axis.yaxis.set_major_formatter(ScalarFormatter())
    draw_hardware_lines(axis, counts)
    finish_axis(axis, threads, "speedup S(p) = Tseq / Tp", "Speedup vs threads (bars: IQR-based range)")
    save_figure(figure, results_dir, "fig_speedup")


def plot_efficiency(scaling, results_dir, counts):
    figure, axis = plt.subplots(figsize=FIGURE_SIZE)
    for size, group in scaling.groupby("n"):
        axis.plot(group["threads"], group["efficiency"], marker="o", label=f"n = {size}")
    axis.axhline(IDEAL_EFFICIENCY, color=REFERENCE_LINE_COLOR, linestyle=IDEAL_LINE_STYLE, label="ideal E(p) = 1")
    draw_hardware_lines(axis, counts)
    finish_axis(axis, scaling["threads"].unique(), "efficiency E(p) = S(p) / p", "Parallel efficiency vs threads")
    save_figure(figure, results_dir, "fig_efficiency")


def write_memory_summary(frame, results_dir):
    memory = frame[frame["phase"] == PHASE_MEMORY]
    columns = ["method", "n", "threads", "est_storage_bytes", "heap_used_bytes", "peak_rss_kb"]
    memory[columns].sort_values(["n", "method", "threads"]).to_csv(results_dir / "summary_memory.csv", index=False)


def write_correctness_summary(frame, results_dir):
    verify = frame[frame["phase"] == PHASE_VERIFY]
    columns = ["method", "n", "threads", "verified", "checksum"]
    verify[columns].sort_values(["n", "method", "threads"]).to_csv(results_dir / "summary_correctness.csv",
                                                                   index=False)
    distinct_checksums = verify.groupby("n")["checksum"].nunique()
    inconsistent = distinct_checksums[distinct_checksums > 1]
    return len(verify[verify["verified"] == VERIFICATION_FAIL]), list(inconsistent.index)


def write_limits_summary(frame, results_dir):
    limits = frame[frame["phase"].isin([PHASE_MULTIPLY, PHASE_WARMUP]) & (frame["status"] != STATUS_OK)]
    columns = ["method", "n", "threads", "phase", "status", "time_s"]
    limits[columns].drop_duplicates().to_csv(results_dir / "summary_limits.csv", index=False)


def main():
    results_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "results")
    frame = load_raw(results_dir)
    counts = read_environment_counts(results_dir)

    measured = frame[(frame["phase"] == PHASE_MULTIPLY) & frame["time_ns"].notna() & (frame["status"] == STATUS_OK)]
    runtime = summarise(measured, RUNTIME_KEYS)
    runtime.to_csv(results_dir / "summary_runtime.csv", index=False)

    scaling = build_scaling(runtime)
    if len(scaling):
        scaling.to_csv(results_dir / "summary_scaling.csv", index=False)
        plot_runtime(scaling, results_dir, counts)
        plot_speedup(scaling, results_dir, counts)
        plot_efficiency(scaling, results_dir, counts)

    write_memory_summary(frame, results_dir)
    write_limits_summary(frame, results_dir)
    failures, inconsistent_sizes = write_correctness_summary(frame, results_dir)
    print(f"Wrote summaries and figures to {results_dir}.")
    print(f"Verification failures: {failures}")
    print(f"Sizes with differing checksums across configurations: {inconsistent_sizes}")


if __name__ == "__main__":
    main()
