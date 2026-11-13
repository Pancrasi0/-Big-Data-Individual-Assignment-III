#!/usr/bin/env python3
"""Extra JVM launches of the main study plus a Hyper-Threading test at n = 2000.

The main study (results/) is launch 1. This script adds launches 2..L with exactly
the protocol of scripts/run_all.sh, one fresh JVM per configuration, and a pinned
2-thread test at n = 2000 (two physical cores vs. the two logical processors of one
core). Results go to results_rigor/. Every configuration is appended to its CSV as
soon as it finishes, so an interrupted run keeps everything already measured.

Usage (from the repository root):
    python3 scripts/run_rigor.py                 # HT test + launches 2 and 3 (about 35 min)
    python3 scripts/run_rigor.py --quick         # same, without n = 3000 in the extra launches (about 15 min)
    python3 scripts/run_rigor.py --launches 2    # only launch 2
    python3 scripts/run_rigor.py --skip-ht       # only the extra launches
"""
import argparse, datetime, os, shutil, subprocess, sys, time

JVM_FLAGS = ["-Xms4g", "-Xmx4g", "-XX:+UseParallelGC"]
SIZES = [250, 500, 1000, 2000, 3000]
THREAD_COUNTS = [1, 2, 4, 6, 8, 12, 16]
COMMON = ["--warmup", "3", "--reps", "7", "--seed", "42",
          "--max-seconds", "240", "--mem-budget-mb", "8192"]
HT_SIZE = 2000
HT_LAUNCHES = 3
OUT = "results_rigor"


def cpu_topology():
    base = "/sys/devices/system/cpu"
    def read(path):
        with open(path) as f:
            return f.read().strip()
    sib = read(f"{base}/cpu0/topology/thread_siblings_list").replace("-", ",").split(",")
    sibling = next(c for c in sib if c != "0")
    other = next(c for c in range(1, os.cpu_count())
                 if read(f"{base}/cpu{c}/topology/core_id") != read(f"{base}/cpu0/topology/core_id"))
    return f"0,{other}", f"0,{sibling}"


def run(log, out_dir, args, affinity=None):
    os.makedirs(out_dir, exist_ok=True)
    env = dict(os.environ, MATMUL_CPU_AFFINITY=affinity or "none")
    cmd = ["java", *JVM_FLAGS, "-cp", "out", "bigdata.matmul.Main", "benchmark",
           *args, *COMMON, "--out-dir", out_dir]
    if affinity:
        cmd = ["taskset", "-c", affinity, *cmd]
    line = f"[{datetime.datetime.now():%H:%M:%S}] {' '.join(cmd)}"
    print(line, flush=True); log.write(line + "\n"); log.flush()
    t0 = time.time()
    proc = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    log.write(proc.stdout); log.write(f"exit={proc.returncode} wall={time.time() - t0:.1f}s\n"); log.flush()
    if proc.returncode != 0:
        print(proc.stdout[-2000:]); sys.exit(f"Configuration failed (exit {proc.returncode}); see {OUT}/run_rigor.log")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--launches", type=int, default=3, help="highest launch number (launch 1 is results/)")
    ap.add_argument("--quick", action="store_true", help="leave n = 3000 out of the extra launches")
    ap.add_argument("--skip-ht", action="store_true", help="skip the n = 2000 Hyper-Threading test")
    ap.add_argument("--sizes", help="override sizes, comma-separated (for testing only)")
    a = ap.parse_args()

    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    if not os.path.isfile("out/bigdata/matmul/Main.class"):
        subprocess.run(["bash", "scripts/build.sh"], check=True)
    if os.path.isdir(OUT):
        sys.exit(f"{OUT}/ already exists; move or delete it first so the CSVs are not appended to.")
    os.makedirs(OUT)

    sizes = [int(s) for s in a.sizes.split(",")] if a.sizes else [s for s in SIZES if not (a.quick and s == 3000)]
    ht_size = max(sizes) if a.sizes else HT_SIZE

    with open(f"{OUT}/run_rigor.log", "w") as log, open(f"{OUT}/settings.txt", "w") as st:
        try:
            governor = open("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").read().strip()
        except OSError:
            governor = "unknown"
        st.write(f"start={datetime.datetime.now().isoformat(timespec='seconds')}\n"
                 f"governor={governor}\nsizes_extra_launches={sizes}\nthreads={THREAD_COUNTS}\n"
                 f"launches=2..{a.launches} (launch 1 = results/)\njvm_flags={' '.join(JVM_FLAGS)}\n"
                 f"common={' '.join(COMMON)}\n")
        if governor != "performance":
            print(f"WARNING: CPU governor is '{governor}', not 'performance'.")
        if shutil.which("lscpu"):
            with open(f"{OUT}/lscpu_e.txt", "w") as f:
                subprocess.run(["lscpu", "-e"], stdout=f)

        # 1) Hyper-Threading test at n = 2000 (first, so it is done even if time runs out)
        if not a.skip_ht:
            phys, sib = cpu_topology()
            st.write(f"ht_size={ht_size}\nphysical_pair={phys}\nsibling_pair={sib}\n"); st.flush()
            for L in range(1, HT_LAUNCHES + 1):
                base = f"{OUT}/ht_n{ht_size}"
                run(log, f"{base}/physical/launch{L}", ["--sizes", str(ht_size), "--variants", "parallel", "--threads", "2"], phys)
                run(log, f"{base}/sibling/launch{L}", ["--sizes", str(ht_size), "--variants", "parallel", "--threads", "2"], sib)
                run(log, f"{base}/single/launch{L}", ["--sizes", str(ht_size), "--variants", "parallel", "--threads", "1"], "0")

        # 2) Extra launches of the main study, one complete launch after another
        for L in range(2, a.launches + 1):
            d = f"{OUT}/launch{L}"
            for n in sizes:
                run(log, d, ["--sizes", str(n), "--variants", "sequential"])
                for p in THREAD_COUNTS:
                    run(log, d, ["--sizes", str(n), "--variants", "parallel", "--threads", str(p)])
            print(f"=== Launch {L} complete ===", flush=True)

        st.write(f"end={datetime.datetime.now().isoformat(timespec='seconds')}\n")
    print(f"Done. Now: python3 scripts/analyze_rigor.py")


if __name__ == "__main__":
    main()
