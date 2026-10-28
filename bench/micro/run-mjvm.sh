#!/bin/bash
# run-mjvm.sh — mJVM micro-benchmark: IR (default) vs --class (compiled).
# Wall-clock per phase, best of 3, minus baseline (ph_base.m) best of 3.
# Usage: run-mjvm.sh [--class]   (JAVA_HOME auto-detected: env > Homebrew)
set -e
D="$(cd "$(dirname "$0")" && pwd)"
MJVM_ROOT="$(cd "$D/../.." && pwd)"

if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java"
fi

MODE="ir"
[ "$1" = "--class" ] && MODE="class"
CP="$MJVM_ROOT/build:$MJVM_ROOT/lib/*"

best() { # file -> seconds
  local best=""
  for _ in 1 2 3; do
    local t0 t1
    t0=$(python3 -c 'import time; print(time.time())')
    if [ "$MODE" = "class" ]; then
      "$JAVA" -cp "$CP" mjvm.cli.Mjvm --class "$1" >/dev/null 2>&1
    else
      "$JAVA" -cp "$CP" mjvm.cli.Mjvm "$1" >/dev/null 2>&1
    fi
    t1=$(python3 -c 'import time; print(time.time())')
    local d
    d=$(python3 -c "print($t1-$t0)")
    if [ -z "$best" ] || python3 -c "exit(0 if $d < $best else 1)"; then best=$d; fi
  done
  echo "$best"
}

# baseline
base=$(best "$D/phases/ph_base.m")

for f in ph_intloop ph_strcat ph_aset ph_awalk ph_piece ph_gset ph_gread ph_gwalk; do
  t=$(best "$D/phases/$f.m")
  net=$(python3 -c "print(f'{max($t-$base,0):.3f}')")
  printf 'mJVM %-6s %-9s %ss\n' "$MODE" "$f" "$net"
done
