#!/bin/bash
# refresh-expected.sh — regenerate expected outputs from GT.M (U01, the oracle engine).
# Usage: tools/oracle/refresh-expected.sh
# Requires ssh key auth to mark@100.72.84.8 (U01). Expecteds land in expected/<case>.out
# and are committed — CI only needs run.sh.
set -u
D="$(cd "$(dirname "$0")" && pwd)"
REMOTE=mark@100.72.84.8
RDIR=/home/mark/gtm-ecosystem/routines
TMP=/tmp/oracle-cases

echo "ORACLE refresh: $(ls "$D"/cases/*.m | wc -l) cases"
rsync -a --delete "$D/cases/" "$REMOTE:$TMP/cases/"

ssh -o BatchMode=yes -o ConnectTimeout=15 "$REMOTE" '
  set -u
  cd /home/mark/gtm-ecosystem
  source ./gtm-env.sh >/dev/null 2>&1
  unset gtm_chset
  mkdir -p '"$TMP"'/out
  RDIR='"$RDIR"'
  for f in '"$TMP"'/cases/*.m; do
    name=$(basename "$f" .m)
    cp "$f" "$RDIR/$name.m"
    printf "D ^%s\nH\n" "$name" | "$gtm_dist/mumps" -direct >"'"$TMP"'/out/$name.out" 2>&1
    rm -f "$RDIR/$name.m" "$RDIR/$name.o"
  done
  exit 0
'

mkdir -p "$D/expected"
rsync -a --delete "$REMOTE:$TMP/out/" "$D/expected/"
# strip GT.M direct-mode prompts: first-line "GTM> " and bare prompt lines
for f in "$D/expected"/*.out; do
  sed -i '' -e 's/^GTM> //' -e '/^GTM>$/d' -e '/^$/d' "$f"
done
echo "expected refreshed: $(ls "$D/expected"/*.out | wc -l) files"
