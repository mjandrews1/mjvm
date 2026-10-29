#!/bin/bash
# run.sh — ORACLE gate: run every case under mJVM and diff against GT.M expecteds.
# Usage: tools/oracle/run.sh [case ...]        (no args = all cases)
# Exit nonzero if any case diverges. Per-case detail: tools/oracle/last/<case>.{got,want}
set -u
D="$(cd "$(dirname "$0")" && pwd)"
J="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}/bin/java"
CP="$(cd "$D/../.." && pwd)/build:$(cd "$D/../.." && pwd)/lib/*"
OUT="$D/last"
mkdir -p "$OUT"

if [ $# -gt 0 ]; then CASES="$*"; else CASES=$(ls "$D"/cases/*.m); fi

pass=0; fail=0; err=0; upstream=0; knownbug=0
KD="$D/known-divergence.txt"
KB="$D/known-bugs.txt"
for f in $CASES; do
  name=$(basename "$f" .m)
  want_f="$D/expected/$name.out"
  if [ ! -f "$want_f" ]; then echo "NOEXP  $name (run refresh-expected.sh)"; continue; fi
  if grep -q "^$name " "$KD" 2>/dev/null; then
    # known upstream divergence: must still diverge (or it was fixed upstream-side);
    # report either way but never count as an mJVM failure
    got=$("$J" -cp "$CP" mjvm.cli.Mjvm "$f" 2>&1)
    want=$(cat "$want_f")
    if [ "$got" = "$want" ]; then
      echo "upstream-now-matching $name (!)"; pass=$((pass+1))
    else
      echo "upstream $name (documented divergence)"
      upstream=$((upstream+1))
    fi
    continue
  fi
  got=$("$J" -cp "$CP" mjvm.cli.Mjvm "$f" 2>&1)
  rc=$?
  want=$(cat "$want_f")
  printf '%s\n' "$got" > "$OUT/$name.got"
  printf '%s\n' "$want" > "$OUT/$name.want"
  kbissue=$(grep "^$name " "$KB" 2>/dev/null | awk '{print $2}')
  if [ $rc -ne 0 ] && [ -z "$got" ]; then
    echo "ERR    $name (crash, rc=$rc)"; err=$((err+1))
  elif [ "$got" = "$want" ]; then
    echo "ok     $name"; pass=$((pass+1))
    if [ -n "$kbissue" ]; then
      echo "FIXED? $name was annotated $kbissue and now PASSES — remove the annotation"
    fi
  elif [ -n "$kbissue" ]; then
    echo "knownbug $name ($kbissue)"
    knownbug=$((knownbug+1))
  else
    echo "DIVERGE $name"
    echo "    got:  $(echo "$got" | head -1)"
    echo "    want: $(echo "$want" | head -1)"
    fail=$((fail+1))
  fi
done
echo
echo "ORACLE: pass=$pass diverge=$fail err=$err upstream-divergent=$upstream known-bugs=$knownbug"
echo "CI red only on: diverge>0, err>0. Known bugs tracked in known-bugs.txt; upstream in known-divergence.txt."
[ $((fail+err)) -eq 0 ]
