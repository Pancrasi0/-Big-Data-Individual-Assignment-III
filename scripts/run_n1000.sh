#!/usr/bin/env bash
# Investigación de la anomalía n = 1000, 2 hilos (S = 1.63).
# Colocar en scripts/ del repositorio y ejecutar desde la raíz:  bash scripts/run_n1000.sh
set -euo pipefail
cd "$(dirname "$0")/.."

JVM_FLAGS="-Xms4g -Xmx4g -XX:+UseParallelGC"
COMMON="--sizes 1000 --warmup 3 --reps 7 --seed 42 --max-seconds 240 --mem-budget-mb 8192"
LAUNCHES=5
ROOT=results_n1000

[ -d out ] || bash scripts/build.sh
rm -rf "$ROOT"; mkdir -p "$ROOT"

# --- Topología: qué procesadores lógicos comparten núcleo físico ---
lscpu -e > "$ROOT/lscpu_e.txt"
SIBLING=$(tr '-' ',' < /sys/devices/system/cpu/cpu0/topology/thread_siblings_list | cut -d, -f2)
CORE0=$(cat /sys/devices/system/cpu/cpu0/topology/core_id)
CORE1=$(cat /sys/devices/system/cpu/cpu1/topology/core_id)
if [ "$CORE0" = "$CORE1" ]; then echo "cpu0 y cpu1 comparten núcleo; edita PHYS_PAIR"; exit 1; fi
PHYS_PAIR="0,1"            # dos núcleos físicos distintos
SIB_PAIR="0,$SIBLING"      # dos lógicos del MISMO núcleo físico
echo "physical_pair=$PHYS_PAIR sibling_pair=$SIB_PAIR" | tee "$ROOT/pairs.txt"
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor >> "$ROOT/pairs.txt" 2>/dev/null || true

run() {  # run <out_dir> <affinity|none> <args...>
  local dir=$1 aff=$2; shift 2
  mkdir -p "$dir"
  if [ "$aff" = none ]; then
    MATMUL_CPU_AFFINITY=none java $JVM_FLAGS -cp out bigdata.matmul.Main benchmark "$@" $COMMON --out-dir "$dir"
  else
    MATMUL_CPU_AFFINITY="$aff" taskset -c "$aff" \
      java $JVM_FLAGS -cp out bigdata.matmul.Main benchmark "$@" $COMMON --out-dir "$dir"
  fi
}

for L in $(seq 1 "$LAUNCHES"); do
  echo "=== Lanzamiento $L / $LAUNCHES ==="
  # Experimento A: repetir el protocolo original (sin afinidad), JVM nueva cada vez
  D="$ROOT/A_noaffinity/launch$L"
  run "$D" none --variants sequential
  run "$D" none --variants parallel --threads 1
  run "$D" none --variants parallel --threads 2
  run "$D" none --variants parallel --threads 4

  # Experimento B: 2 hilos fijados a núcleos físicos distintos vs. al mismo núcleo
  run "$ROOT/B_physical/launch$L" "$PHYS_PAIR" --variants parallel --threads 2
  run "$ROOT/B_sibling/launch$L"  "$SIB_PAIR"  --variants parallel --threads 2
  run "$ROOT/B_single/launch$L"   "0"          --variants parallel --threads 1
done

tar czf results_n1000.tar.gz "$ROOT"
echo "Hecho. Envía results_n1000.tar.gz"
