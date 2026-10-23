#!/usr/bin/env bash
# jvmumps#33 — run make-bench-family and emit a markdown table (stdout or file).
# Usage: tools/bench_report.sh [out.md] [iters]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-}"
ITERS="${2:-50000}"
CP="build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"
mkdir -p build
javac --release 21 -cp "$CP" -d build $(find src/main/java -name "*.java")
javac --release 21 -cp "$CP" -d build $(find src/test/java -name "*.java")

emit() {
  echo "# Bench report"
  echo
  echo "- **Host:** $(uname -n) $(uname -smr)"
  echo "- **JDK:** $(java -version 2>&1 | head -1)"
  echo "- **Commit:** $(git rev-parse --short HEAD) $(git describe --tags --always 2>/dev/null || true)"
  echo "- **Date:** $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## MjvmBench"
  echo '```'
  java -cp "$CP" mjvm.bench.MjvmBench "$ITERS" 2>/dev/null | grep -E 'compile|IR loop|unroll|PASS|FAIL' || true
  echo '```'
  echo
  echo "## PerfMatrix"
  java -cp "$CP" mjvm.bench.PerfMatrix "$ITERS" 2>/dev/null | sed -n '/| Metric/,/^$/p' || true
  echo
  echo "## Footprint"
  java -cp "$CP" mjvm.bench.FootprintBench 200 "$ITERS" 2>/dev/null | sed -n '/| compile/,/^$/p' || true
  echo
  echo "## StoreLock"
  echo '```'
  java -cp "$CP" jvmumps.StoreLockBench 2000 32 2>/dev/null | grep -E 'BULK|TX |CONT|MULTIKEY|threads' || true
  echo '```'
  echo
  echo "## Limit probes"
  echo '```'
  java -cp "$CP" jvmumps.LimitProbeSmoke 2>/dev/null || echo "(LimitProbeSmoke missing)"
  echo '```'
}

if [ -n "$OUT" ]; then
  emit > "$OUT"
  echo "wrote $OUT" >&2
else
  emit
fi
