#!/usr/bin/env python3
"""Combine the main study (launch 1, results/) with the extra launches (results_rigor/).

For every configuration: median and IQR inside each launch, then the median of the
launch medians as the summary and the range of launch medians as the spread between
launches. Writes CSV summaries, a Markdown summary and figures to results_rigor/analysis/.

Usage: python3 scripts/analyze_rigor.py [results] [results_rigor]
"""
import glob, os, sys
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

MAIN = sys.argv[1] if len(sys.argv) > 1 else "results"
RIG = sys.argv[2] if len(sys.argv) > 2 else "results_rigor"
OUT = os.path.join(RIG, "analysis")
os.makedirs(OUT, exist_ok=True)
SLOW = 1.10          # a launch is "slow" if its median exceeds the median of launch medians by > 10 %
PHYS, LOGI = 6, 12   # marked in the figures


def iqr(s): return s.quantile(0.75) - s.quantile(0.25)


def md(df, index=True):
    """Markdown table without the optional 'tabulate' dependency."""
    df = df.reset_index() if index else df
    cols = [str(c) for c in df.columns]
    lines = ["| " + " | ".join(cols) + " |", "|" + "---|" * len(cols)]
    for row in df.itertuples(index=False):
        lines.append("| " + " | ".join(str(v) for v in row) + " |")
    return "\n".join(lines)


# ---------- load ----------
frames = [pd.read_csv(os.path.join(MAIN, "raw_measurements.csv")).assign(launch=1)]
for f in sorted(glob.glob(os.path.join(RIG, "launch*", "raw_measurements.csv"))):
    frames.append(pd.read_csv(f).assign(launch=int(f.split(os.sep)[-2].replace("launch", ""))))
D = pd.concat(frames, ignore_index=True)
key = ["method", "n", "threads"]

# ---------- correctness ----------
ver = D[D.phase == "verify"]
bad_status = D[D.status != "OK"]
fails = (ver.verified != "PASS").sum()
cs = ver.groupby("n").checksum.nunique()
corr = dict(launches=sorted(D.launch.unique().tolist()), verify_rows=len(ver), verify_failures=int(fails),
            non_ok_rows=len(bad_status), sizes_with_differing_checksums=cs[cs > 1].index.tolist())

# ---------- runtime ----------
m = D[D.phase == "multiply"].copy()
m["ms"] = m.time_ns / 1e6
pl = m.groupby(key + ["launch"]).ms.agg(median="median", iqr=iqr, reps="count").reset_index()
pl.to_csv(os.path.join(OUT, "per_launch_runtime.csv"), index=False)

def summarise(g):
    med = g["median"].median()
    return pd.Series(dict(launches=len(g), median_ms=med, min_launch_ms=g["median"].min(),
                          max_launch_ms=g["median"].max(), max_within_iqr_ms=g["iqr"].max(),
                          slow_launches=int((g["median"] > SLOW * med).sum()),
                          slow_launch_ids=",".join(str(x) for x in g.loc[g["median"] > SLOW * med, "launch"])))
R = pl.groupby(key).apply(summarise, include_groups=False).reset_index()
orig = pl[pl.launch == 1].set_index(key)["median"].rename("launch1_ms")
R = R.join(orig, on=key)
R["change_vs_launch1_pct"] = 100 * (R.median_ms / R.launch1_ms - 1)
R.to_csv(os.path.join(OUT, "summary_runtime.csv"), index=False)

# ---------- scaling ----------
seq = R[R.method == "ikj_sequential"].set_index("n")
par = R[R.method == "ikj_parallel"].copy()
par["T_seq_ms"] = par.n.map(seq.median_ms)
par["speedup"] = par.T_seq_ms / par.median_ms
par["efficiency"] = par.speedup / par.threads
par["speedup_low"] = par.T_seq_ms / par.max_launch_ms
par["speedup_high"] = par.T_seq_ms / par.min_launch_ms
seq1 = pl[(pl.launch == 1) & (pl.method == "ikj_sequential")].set_index("n")["median"]
par["speedup_launch1"] = par.n.map(seq1) / par.launch1_ms
par.to_csv(os.path.join(OUT, "summary_scaling.csv"), index=False)

# ---------- memory ----------
mem = D[D.phase == "memory"].groupby(key).agg(launches=("launch", "nunique"),
        heap_used_MB_median=("heap_used_bytes", lambda s: s.median() / 1e6),
        peak_rss_MiB_median=("peak_rss_kb", lambda s: s.median() / 1024),
        peak_rss_MiB_min=("peak_rss_kb", lambda s: s.min() / 1024),
        peak_rss_MiB_max=("peak_rss_kb", lambda s: s.max() / 1024)).reset_index()
mem.to_csv(os.path.join(OUT, "summary_memory.csv"), index=False)

# ---------- Hyper-Threading test ----------
ht_rows = []
for f in sorted(glob.glob(os.path.join(RIG, "ht_n*", "*", "launch*", "raw_measurements.csv"))):
    parts = f.split(os.sep)
    d = pd.read_csv(f)
    d = d[d.phase == "multiply"]
    ht_rows.append(dict(placement=parts[-3], launch=int(parts[-2].replace("launch", "")),
                        n=int(d.n.iloc[0]), threads=int(d.threads.iloc[0]),
                        median_ms=d.time_ns.median() / 1e6, iqr_ms=iqr(d.time_ns) / 1e6))
HT = pd.DataFrame(ht_rows)
if len(HT):
    HT.to_csv(os.path.join(OUT, "ht_per_launch.csv"), index=False)
    hts = HT.groupby(["placement", "n", "threads"]).median_ms.agg(["count", "median", "min", "max"]).reset_index()
    n_ht = int(hts.n.iloc[0])
    t_seq = seq.median_ms.get(n_ht)
    hts["speedup_vs_seq"] = t_seq / hts["median"] if t_seq else float("nan")
    single = hts.loc[hts.placement == "single", "median"]
    hts["throughput_vs_single"] = (single.iloc[0] / hts["median"]) if len(single) else float("nan")
    hts.to_csv(os.path.join(OUT, "ht_summary.csv"), index=False)

# ---------- Markdown summary ----------
with open(os.path.join(OUT, "summary.md"), "w") as f:
    f.write("# Summary over launches\n\n")
    f.write(f"Correctness: {corr}\n\n")
    f.write("## Median of launch medians (ms) [range of launch medians] {launches}\n\n")
    R["cell"] = R.apply(lambda r: f"{r.median_ms:.2f} [{r.min_launch_ms:.2f}-{r.max_launch_ms:.2f}] {{{r.launches}}}", axis=1)
    f.write(md(R.pivot(index=["method", "threads"], columns="n", values="cell")) + "\n\n")
    f.write("## Change of the summary vs launch 1 (%)\n\n")
    f.write(md(R.pivot(index=["method", "threads"], columns="n", values="change_vs_launch1_pct").round(1)) + "\n\n")
    f.write("## Slow launches (> 10 % above the median of launch medians)\n\n")
    sl = R[R.slow_launches > 0][["method", "n", "threads", "slow_launch_ids", "median_ms", "max_launch_ms"]]
    f.write((md(sl.round(2), index=False) if len(sl) else "none") + "\n\n")
    par["cell"] = par.apply(lambda r: f"{r.speedup:.2f} ({r.efficiency:.2f})", axis=1)
    f.write("## Speedup (efficiency), medians of launch medians\n\n")
    f.write(md(par.pivot(index="threads", columns="n", values="cell")) + "\n\n")
    f.write("## Speedup in launch 1 only (original report)\n\n")
    f.write(md(par.pivot(index="threads", columns="n", values="speedup_launch1").round(2)) + "\n\n")
    f.write("## Memory (medians over launches)\n\n" + md(mem.round(2), index=False) + "\n\n")
    if len(HT):
        f.write("## Hyper-Threading test\n\n" + md(hts.round(3), index=False) + "\n\n")
        f.write(md(HT.round(2), index=False) + "\n")

# ---------- figures ----------
colors = {n: f"C{i}" for i, n in enumerate(sorted(R.n.unique()))}
def mark(ax):
    for x, lab in ((PHYS, "6 physical"), (LOGI, "12 logical")):
        ax.axvline(x, color="grey", ls=":", lw=0.8)
fig, ax = plt.subplots(figsize=(7, 4.5))
for n, g in par.groupby("n"):
    g = g.sort_values("threads")
    ax.errorbar(g.threads, g.median_ms / 1e3, yerr=[(g.median_ms - g.min_launch_ms) / 1e3, (g.max_launch_ms - g.median_ms) / 1e3],
                marker="o", ms=4, capsize=3, color=colors[n], label=f"n = {n}")
    ax.axhline(seq.median_ms[n] / 1e3, color=colors[n], ls="--", lw=0.8)
ax.set_xscale("log", base=2); ax.set_yscale("log"); mark(ax)
ax.set_xticks(sorted(par.threads.unique()), [str(t) for t in sorted(par.threads.unique())])
ax.set_xlabel("Threads"); ax.set_ylabel("Median of launch medians (s)")
ax.set_title("Runtime vs threads (bars: range of launch medians; dashed: sequential)", fontsize=9)
ax.legend(fontsize=8); ax.grid(alpha=0.3); fig.tight_layout()
for e in ("png", "pdf"): fig.savefig(os.path.join(OUT, f"fig_runtime_vs_threads.{e}"), dpi=200)

for col, fname, ideal, ylab in (("speedup", "fig_speedup", lambda t: t, "Speedup S(p)"),
                                ("efficiency", "fig_efficiency", lambda t: 1 + 0 * t, "Efficiency E(p)")):
    fig, ax = plt.subplots(figsize=(7, 4.5))
    ts = sorted(par.threads.unique())
    ax.plot(ts, [ideal(t) for t in ts], "k--", lw=0.8, label="ideal")
    for n, g in par.groupby("n"):
        g = g.sort_values("threads")
        if col == "speedup":
            ax.errorbar(g.threads, g.speedup, yerr=[g.speedup - g.speedup_low, g.speedup_high - g.speedup],
                        marker="o", ms=4, capsize=3, color=colors[n], label=f"n = {n}")
        else:
            ax.plot(g.threads, g.efficiency, marker="o", ms=4, color=colors[n], label=f"n = {n}")
    mark(ax); ax.set_xlabel("Threads"); ax.set_ylabel(ylab); ax.legend(fontsize=8); ax.grid(alpha=0.3)
    ax.set_title(f"{ylab} from medians of launch medians" + (" (bars: range over launches)" if col == "speedup" else ""), fontsize=9)
    fig.tight_layout()
    for e in ("png", "pdf"): fig.savefig(os.path.join(OUT, f"{fname}.{e}"), dpi=200)

print("Correctness:", corr)
print(f"Wrote CSV summaries, summary.md and figures to {OUT}/")
