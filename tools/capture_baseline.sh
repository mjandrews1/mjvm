#!/usr/bin/env bash
# jvmumps#98 — fixed-host baseline capture (U01 preferred).
# Usage: tools/capture_baseline.sh [host-label] [out.md]
# Produces a metrics-matrix column with one-line reproduce commands.
set -uo pipefail
cd "$(dirname "$0")/.."
HOST="${1:-$(uname -n)}"
OUT="${2:-docs/BENCH-baseline.md}"
ITERS="${BENCH_ITERS:-50000}"
CP="build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"
mkdir -p build
javac --release 21 -cp "$CP" -d build $(find src/main/java -name "*.java")
javac --release 21 -cp "$CP" -d build $(find src/test/java -name "*.java") 2>/dev/null || true

ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
tcmd() {
  local label="$1"; shift
  local out
  out=$(/usr/bin/time -f "WALL_S=%e RSS_KB=%M" "$@" 2>&1)
  local wall rss
  wall=$(echo "$out" | sed -n 's/.*WALL_S=\([0-9.]*\).*/\1/p' | tail -1)
  rss=$(echo "$out" | sed -n 's/.*RSS_KB=\([0-9]*\).*/\1/p' | tail -1)
  echo "| \`$label\` | ${wall:-} | ${rss:-} |"
}

{
  echo "# Baseline — $HOST"
  echo
  echo "- **Host:** $HOST · $(uname -s) $(uname -r) $(uname -m)"
  echo "- **CPU / RAM:** $(getconf _NPROCESSORS_ONLN 2>/dev/null) cpus · $(awk '/MemTotal/{printf "%.0f GB", $2/1024/1024}' /proc/meminfo 2>/dev/null || echo '?')"
  echo "- **Commit:** $(git rev-parse --short HEAD 2>/dev/null) $(git describe --tags --always 2>/dev/null)"
  echo "- **JDK:** $(java -version 2>&1 | head -1)"
  echo "- **Date:** $(ts)"
  echo
  echo "## Metrics matrix (filled)"
  echo
  echo "| # | Metric | Unit | Reproduce | Result |"
  echo "|--:|--------|------|-----------|--------|"

  # 1 corpus wall
  w=$(/usr/bin/time -f "%e" java -cp "$CP" jvmumps.CorpusRunner tests/conformance.json 2>&1 >/dev/null | tail -1)
  r=$(java -cp "$CP" jvmumps.CorpusRunner tests/conformance.json 2>/dev/null | tail -1)
  echo "| 1 | Corpus wall time | s | \`java jvmumps.CorpusRunner tests/conformance.json\` | **${w}s** · $r |"

  # 2 corpus score
  s1=$(java -cp "$CP" jvmumps.MjvmCorpusScore tests/conformance.json 2>/dev/null | tail -1)
  s2=$(java -cp "$CP" jvmumps.MjvmNativeScore tests/conformance.json 2>/dev/null | tail -1)
  echo "| 2 | Corpus score | MATCH/ERR/MISS | \`java jvmumps.MjvmCorpusScore …\` / \`MjvmNativeScore\` | $s1 · $s2 |"

  # 3-6 MjvmBench (tolerate fail)
  mb=$(java -cp "$CP" mjvm.bench.MjvmBench "$ITERS" 2>&1 | tail -25)
  echo '| 3 | IR arith loop | it/s | `java mjvm.bench.MjvmBench '"$ITERS"'` | '"$(echo "$mb" | grep 'IR loop .*inc' | head -1 | sed -E 's/.* ([0-9]+) it\/s.*/\1/')"' |'
  echo '| 4 | IR cat loop | it/s | `java mjvm.bench.MjvmBench '"$ITERS"'` | '"$(echo "$mb" | grep 'IR loop .*cat' | head -1 | sed -E 's/.* ([0-9]+) it\/s.*/\1/')"' |'
  echo '| 5 | Class vs IR speedup | × | `java mjvm.bench.MjvmBench '"$ITERS"'` (unroll) | '"$(echo "$mb" | grep 'unroll inc' | head -1 | sed -E 's/.* ([0-9.]+)x$/\1/')"' |'
  echo '| 6 | Compile latency | ms | `java mjvm.bench.MjvmBench '"$ITERS"'` | '"$(echo "$mb" | grep 'compile S X' | head -1 | sed -E 's/.* ([0-9.]+) ms.*/\1/')"' |'

  # 7-8 store via PerfMatrix
  pm=$(java -cp "$CP" mjvm.bench.PerfMatrix "$ITERS" 2>/dev/null)
  pget() { echo "$pm" | awk -F'|' -v k="$1" '{gsub(/ /,"",$2); if ($2=="`" k "`") {gsub(/^ +| +$/,"",$3); print $3}}' | head -1; }
  echo '| 7 | SET/GET bulk | ops/s | `java mjvm.bench.PerfMatrix '"$ITERS"'` | sqlite set '"$(pget store.sqlite.set_ops)"' / get '"$(pget store.sqlite.get_ops)"' ops/s |'
  echo '| 8 | $ORDER walk | ms / n | `java mjvm.bench.PerfMatrix '"$ITERS"'` | sqlite '"$(pget store.sqlite.order_walk_ms)"' ms / '"$(pget store.sqlite.order_walk_keys)"' keys |'

  # 9 TSTART — approximate via lock measure note
  echo "| 9 | TSTART/TCOMMIT | ms | lock/tx smokes (see #100) | *pending #100* |"

  # 10 lock uncontended
  lm=$(java -cp "$CP" jvmumps.SqlLockMeasureSmoke 2>/dev/null | tail -1)
  echo "| 10 | Lock acquire uncontended | µs | \`java jvmumps.SqlLockMeasureSmoke\` | $lm |"

  # 11 lock stress
  ls_=$(java -cp "$CP" jvmumps.SqlLockStressSmoke 2>/dev/null | tail -3 | tr '\n' ' ')
  echo "| 11 | Lock stress P50/P99 | ms | \`java jvmumps.SqlLockStressSmoke\` | $ls_ |"

  # 12 startup
  st=$(/usr/bin/time -f "%e" java -cp "$CP" mjvm.cli.Mjvm -V 2>&1 >/dev/null | tail -1)
  echo "| 12 | Startup to first output | s | \`java mjvm.cli.Mjvm -V\` | **${st}s** |"

  # 13 peak RSS of smoke
  sr=$(/usr/bin/time -f "%e %M" make smoke 2>&1 >/dev/null | tail -1)
  echo "| 13 | Peak RSS (make smoke) | s / KB | \`make smoke\` | $sr |"

  # 14 RSM
  echo "| 14 | RSM oracle compare | ratio | \`rsm\` + same corpus | *pending #99* |"

  echo
  echo "## PerfMatrix detail"
  echo
  echo '```'
  echo "$pm" | sed -n '/| Metric/,/^$/p'
  echo '```'
  echo
  echo "Captured $(ts) on $HOST."
} > "$OUT"

echo "wrote $OUT" >&2
head -5 "$OUT"
