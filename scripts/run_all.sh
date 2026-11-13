#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JVM_FLAGS="-Xms4g -Xmx4g -XX:+UseParallelGC"
SIZES="250 500 1000 2000 3000"
THREAD_COUNTS="1 2 4 6 8 12 16"
WARMUP=3
REPS=7
SEED=42
MAX_SECONDS=240
MEMORY_BUDGET_MB=8192
OUT_DIR=results

export MATMUL_CPU_AFFINITY="${MATMUL_CPU_AFFINITY:-none}"
mkdir -p "$OUT_DIR"

run() {
  java $JVM_FLAGS -cp out bigdata.matmul.Main benchmark "$@" \
    --warmup "$WARMUP" --reps "$REPS" --seed "$SEED" \
    --max-seconds "$MAX_SECONDS" --mem-budget-mb "$MEMORY_BUDGET_MB" --out-dir "$OUT_DIR"
}

for n in $SIZES; do
  run --sizes "$n" --variants sequential
  for p in $THREAD_COUNTS; do
    run --sizes "$n" --variants parallel --threads "$p"
  done
done

echo "Done. Now run: python3 scripts/analyze.py $OUT_DIR"
