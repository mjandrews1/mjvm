#!/bin/bash
# build-fixture-run.sh — critical-path step 3.1: dual-engine ingest re-time (mjvm#43).
#
# Runs the m-ils CAT fixture (catplus_sample.xml, ~15 MB / 2,663 records) through the
# device-less GT.M-portable ingest shell in ils-fixture/mjvm_full.m (O fn / U fn / C fn,
# PR #50 form) and times it. With --gtm, also times GT.M V7.2-001 on U01 (utility-01) in a
# scratch DB and diffs the dumps.
#
# Usage: tools/build-fixture-run.sh [--gtm] [reps]
# Env:   ILS_FIXTURE (default ~/ils-fixture), JAVA_HOME, U01 (default mark@100.72.84.8)
set -u
D="$(cd "$(dirname "$0")/.." && pwd)"   # mjvm repo root
ILS_FIXTURE="${ILS_FIXTURE:-$HOME/ils-fixture}"
U01="${U01:-mark@100.72.84.8}"
REPS="${2:-3}"
WANT_GTM=0; [ "${1:-}" = "--gtm" ] && WANT_GTM=1
J="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}/bin/java"

XML="$ILS_FIXTURE/catplus_sample.xml"
SRC="$ILS_FIXTURE/mjvm_full.m"
[ -f "$XML" ] || { echo "missing fixture $XML (set ILS_FIXTURE)"; exit 1; }
[ -f "$SRC" ] || { echo "missing routine $SRC"; exit 1; }

# Light driver: drop the ILSDUMP call from the carrier's head, keep ingest + ing= count.
TMPD="$(mktemp -d)"
grep -v '^ D ILSDUMP$' "$SRC" > "$TMPD/mjvm_ing.m"

cd "$D"
[ -d build ] || { mkdir -p build; find src/main/java -name '*.java' | xargs javac -cp "lib/*" -d build; }

echo "== mjvm (local, $(basename "$J")) — ingest only, $REPS reps"
for i in $(seq 1 "$REPS"); do
  s=$(date +%s%N)
  out=$("$J" -cp "build:lib/*" mjvm.cli.Mjvm "$TMPD/mjvm_ing.m" 2>&1 | tail -2 | tr '\n' ' ')
  e=$(date +%s%N)
  echo "  mjvm run$i: $(( (e-s)/1000000 )) ms :: $out"
done

if [ "$WANT_GTM" = 1 ]; then
  sed "s|$XML|/tmp/catplus_sample.xml|" "$TMPD/mjvm_ing.m" > "$TMPD/mjvm_ing_u01.m"
  scp -q "$XML" "$U01:/tmp/catplus_sample.xml"
  scp -q "$TMPD/mjvm_ing_u01.m" "$U01:/tmp/mjvming.m"
  echo "== GT.M V7.2-001 on U01 — ingest only, $REPS reps (scratch gld /tmp/ingest.gld)"
  ssh -o ConnectTimeout=10 -o BatchMode=yes "$U01" \
    'export gtm_dist=/home/mark/gtm/V7.2-001; source $gtm_dist/gtmprofile 2>/dev/null; cd /tmp; export gtmgbldir=/tmp/ingest.gld gtmroutines="$gtm_dist /tmp(.)"; for i in 1 2 3; do s=$(date +%s%N); out=$(echo "D ^mjvming" | $gtm_dist/mumps -direct 2>&1 | grep -E "ing=|%GTM" | tr "\n" " "); e=$(date +%s%N); echo "  GT.M run$i: $(( (e-s)/1000000 )) ms :: $out"; done'
fi
rm -rf "$TMPD"
