#!/usr/bin/env bash
# jvmumps#97 — capture a holistic PerfMatrix baseline (fixed host).
# Usage: tools/perf_review.sh [iters] [out.md]
set -euo pipefail
cd "$(dirname "$0")/.."
ITERS="${1:-100000}"
OUT="${2:-}"
CP="build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"
mkdir -p build
javac --release 21 -cp "$CP" -d build $(find src/main/java -name "*.java")
{
  echo "host: $(uname -s) $(uname -r) $(uname -m) · $(getconf _NPROCESSORS_ONLN 2>/dev/null || echo '?') cpus"
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  java -cp "$CP" mjvm.bench.PerfMatrix "$ITERS"
  echo
  echo "## MjvmBench (M8 targets)"
  java -cp "$CP" mjvm.bench.MjvmBench "$ITERS" 2>&1 | tail -25 || echo "(MjvmBench exited non-zero)"
} > "${OUT:-/dev/stdout}"
echo "OK ${OUT:-stdout}" >&2
