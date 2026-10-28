#!/bin/bash
# run-u01.sh — GT.M / YottaDB micro-benchmark on U01 (in-process $ZUT).
# Usage (on U01): run-u01.sh gtm|ydb
set -e
D="$(cd "$(dirname "$0")" && pwd)"
eng="$1"

if [ "$eng" = ydb ]; then
  export ydb_dist=/usr/local/lib/yottadb/r206
  # shellcheck disable=SC1091
  source /usr/local/etc/ydb_env_set
  BIN="$ydb_dist/ydb"
elif [ "$eng" = gtm ]; then
  # shellcheck disable=SC1091
  source "$HOME/gtm-ecosystem/gtm-env.sh"
  unset gtm_chset   # YDB env exports the compat alias; GT.M must not run UTF-8 against CHSET=M objects
  BIN="$gtm_dist/mumps"
else
  echo "usage: run-u01.sh gtm|ydb" >&2; exit 2
fi

case "$eng" in
  ydb)
    rout="$D/routines"
    mkdir -p "$rout"
    cp "$D/BENCH.m" "$rout/BENCH.m"
    export ydb_routines="$rout $ydb_routines"   # gld/routine search: prepend bench dir
    ;;
  gtm)
    rout="$HOME/gtm-ecosystem/routines"
    mkdir -p "$rout"
    cp "$D/BENCH.m" "$rout/BENCH.m"
    cd "$HOME/gtm-ecosystem"   # gtm.gld segment is a relative gtm.dat — resolve from the instance dir
    ;;
esac

echo "=== $eng ==="
echo "D ^BENCH" | "$BIN" -direct 2>&1
rm -f "$rout/BENCH.m"
