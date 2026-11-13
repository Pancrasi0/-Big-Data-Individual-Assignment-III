#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf results_quick
java -Xms1g -Xmx1g -cp out bigdata.matmul.Main benchmark \
  --sizes 100,200,400 --threads 1,2,4 --variants sequential,parallel \
  --warmup 2 --reps 5 --out-dir results_quick
