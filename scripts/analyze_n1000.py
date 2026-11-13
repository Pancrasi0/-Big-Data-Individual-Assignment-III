#!/usr/bin/env python3
"""Analysis of the n = 1000 follow-up (scripts/run_n1000.sh).
Usage: python3 scripts/analyze_n1000.py results_n1000"""
import glob, os, sys
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker

root = sys.argv[1] if len(sys.argv) > 1 else "results_n1000"
frames = []
for f in sorted(glob.glob(os.path.join(root, "*", "launch*", "raw_measurements.csv"))):
    d = pd.read_csv(f)
    d["experiment"] = f.split(os.sep)[-3]
    d["launch"] = int(f.split(os.sep)[-2].replace("launch", ""))
    frames.append(d)
D = pd.concat(frames, ignore_index=True)

print("status:", D.status.value_counts().to_dict())
print("verified:", D.verified.value_counts().to_dict())
print("checksums:", D.checksum.dropna().unique())

m = D[D.phase == "multiply"].copy()
m["ms"] = m.time_ns / 1e6
m["config"] = m.experiment + "/" + m.method.str.replace("ikj_", "") + "/p" + m.threads.astype(str)

def iqr(s): return s.quantile(0.75) - s.quantile(0.25)
per_launch = m.groupby(["config", "launch"]).ms.agg(median="median", iqr=iqr, n="count").reset_index()
per_launch.to_csv(os.path.join(root, "summary_per_launch.csv"), index=False)

order = ["A_noaffinity/sequential/p1", "A_noaffinity/parallel/p1", "A_noaffinity/parallel/p2",
         "A_noaffinity/parallel/p4", "B_physical/parallel/p2", "B_sibling/parallel/p2", "B_single/parallel/p1"]
rows = []
for c in order:
    pl = per_launch[per_launch.config == c]
    mom = pl["median"].median()
    slow = (pl["median"] > 1.10 * mom).sum()
    rows.append(dict(config=c, launches=len(pl), median_of_launch_medians_ms=round(mom, 2),
                     min_launch_ms=round(pl["median"].min(), 2), max_launch_ms=round(pl["median"].max(), 2),
                     max_within_launch_iqr_ms=round(pl["iqr"].max(), 2), slow_launches=int(slow)))
S = pd.DataFrame(rows)
S.to_csv(os.path.join(root, "summary_n1000.csv"), index=False)
pd.set_option("display.width", 200)
print(S.to_string(index=False))

T = dict(zip(S.config, S.median_of_launch_medians_ms))
seq = T["A_noaffinity/sequential/p1"]
for c in order[1:]:
    p = int(c[-1])
    print(f"{c:32s} S = {seq / T[c]:.3f}  E = {seq / T[c] / p:.3f}")
print("sibling vs single pinned:", round(T["B_single/parallel/p1"] / T["B_sibling/parallel/p2"], 3))

labels = ["seq", "par 1", "par 2", "par 4", "par 2\n2 cores", "par 2\n1 core (HT)", "par 1\npinned"]
fig, ax = plt.subplots(figsize=(7.5, 3.8))
for i, c in enumerate(order):
    pl = per_launch[per_launch.config == c]
    ax.scatter([i] * len(pl), pl["median"], s=28, color="C0" if c.startswith("A") else "C1", zorder=3)
    for _, r in pl[pl["median"] > 1.10 * pl["median"].median()].iterrows():
        ax.annotate(str(r.launch), (i, r["median"]), xytext=(5, -3), textcoords="offset points", fontsize=7)
ax.axhline(329.37, ls="--", lw=0.8, color="C3")
ax.text(len(order) - 0.6, 335, "original anomaly (329.37 ms)", color="C3", fontsize=7, ha="right")
ax.set_xticks(range(len(order)), labels, fontsize=8)
ax.set_yscale("log")
ax.yaxis.set_minor_formatter(matplotlib.ticker.NullFormatter())
ax.set_yticks([130, 200, 265, 330, 400, 525, 615], ["130", "200", "265", "330", "400", "525", "615"])
ax.set_ylabel("Median time per launch (ms)")
ax.set_title("n = 1000: median of each JVM launch (7 reps each; slow launches labelled)", fontsize=9)
ax.grid(axis="y", alpha=0.3)
fig.tight_layout()
for ext in ("png", "pdf"):
    fig.savefig(os.path.join(root, f"fig_n1000_launches.{ext}"), dpi=200)
print("Wrote summary_per_launch.csv, summary_n1000.csv and fig_n1000_launches.(png|pdf)")
