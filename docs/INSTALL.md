# Installing JvMumps / mJVM (M9)

## Prerequisites

- **JDK 17+** (tested OpenJDK 27)
- Optional: `man` for the manual pages

```bash
# macOS (Homebrew)
brew install openjdk
export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Build

```bash
make all          # JvMumps engine
make mjvm         # mJVM packages
make smoke        # J0–J2 + persist smokes
make bench        # M8 harness (optional)
```

## Install

```bash
# user-local (default PREFIX=~/.local)
./install.sh

# system-wide
PREFIX=/usr/local sudo ./install.sh
```

| Artifact | Location |
|----------|----------|
| `mjvm` CLI | `$PREFIX/bin/mjvm` |
| Classes + jars | `$PREFIX/lib/mjvm/` |
| Man pages | `$PREFIX/share/man/man1/mjvm.1` · `man7/jvmumps.7` |

Add `$PREFIX/bin` to `PATH` if needed.

## Uninstall

```bash
rm -f "$HOME/.local/bin/mjvm"
rm -rf "$HOME/.local/lib/mjvm"
rm -f "$HOME/.local/share/man/man1/mjvm.1" \
      "$HOME/.local/share/man/man7/jvmumps.7"
```

Or `make uninstall PREFIX=…`

## Verify

```bash
mjvm -V
mjvm -h
man mjvm
man jvmumps
```

## Database volumes (rsm-shaped)

```bash
export RSM_DBFILE=$HOME/mjvm.dat   # optional default
mjvm -v DEMO -b 8 -s 128
mjvm -j 8
mjvm -x 'S X=42 W X,! '
mjvm -k
```

## Notes

- **SQLite** (`lib/sqlite-jdbc.jar`) is used for volume persistence.
- mJVM **W1–W9 / M0–M9** gates: see `CRITICAL_PATH.md` and `docs/mjvm-perf.md`.
