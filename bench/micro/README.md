# micro — cross-engine M micro-benchmark harness (mjvm#5)

Portable micro-benchmarks across GT.M, YottaDB, RSM, and mJVM from the
2026-10-28 four-engine survey (`~/m-ecosystem/SURVEY.md`). Same phase bodies
everywhere; results in RESULTS.md.

## Layout

- `BENCH.m` — single portable routine (in-process `$ZUT` timing; GT.M, YottaDB)
- `phases/*.m` — per-phase programs (wall-clock timing; engines without `$ZUT`, e.g. mJVM)
- `gen-phases.sh` — (re)generate `phases/`
- `run-mjvm.sh` — mJVM IR vs `--class` (compiled); best of 3, minus `ph_base` baseline
- `run-u01.sh gtm|ydb` — GT.M / YottaDB on U01 via in-process `$ZUT`
- `RESULTS.md` — baseline table + findings

## Usage

    # mJVM (Mac; JAVA_HOME or Homebrew openjdk autodetected)
    ./run-mjvm.sh          # IR interpreter
    ./run-mjvm.sh --class # compiled path

    # U01 (GT.M V7.2-001 user-space instance + YottaDB r2.06)
    ./run-u01.sh gtm
    ./run-u01.sh ydb

## Methodology gotchas (measured, not guessed)

- `$ZHOROLOG` is integer-seconds — use `$ZUT` for sub-second timing.
- Wall-clock engines: subtract a startup baseline (`ph_base.m`), best of 3.
- GT.M `gtm-ecosystem/gtm.gld` has a *relative* `gtm.dat` segment — run from
  the instance dir or global phases fail with `%GTM-E-DBFILERR, ENO2`.
- `unset gtm_chset` before GT.M commands: YDB's env script exports the compat
  alias, which puts GT.M in UTF-8 mode against CHSET=M objects.
- YDB: prepend the routine dir to `$ydb_routines` (env default excludes `$HOME`).
- RSM on macOS: SysV shm cleanup after kills (`ipcrm`; shmmax 4MB — `-g 1` max);
  STRCAT overflows its string limit; `$ORDER` walks are quadratic.
- mJVM takes files as args; RSM/GTM/YDB direct mode differs on multi-line stdin.
- Single-run noise is real (see GSET deltas) — same-run pairs only.
