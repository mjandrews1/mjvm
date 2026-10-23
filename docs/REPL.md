# REPL — interactive M shell

Two REPLs with shared UX: **JavaM** (LineInterp) and **mJVM** (native compiler/runtime).

## Usage

```sh
# JavaM REPL
java -cp build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar jvmumps.Repl

# mJVM REPL
java -cp build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar mjvm.cli.Repl
```

## Commands

| Command | Description |
|---|---|
| `<M code>` | Evaluate M expression/statement |
| `/help` | Show help |
| `/load FILE` | Load a routine file (register tags for DO/$$) |
| `/tags` | Show loaded routine tags |
| `/reset` | Clear variable state |
| `/history` | Show command history |
| `/quit` | Exit |

## Environment

| Var | Description |
|---|---|
| `REPL_INIT` | Path to a startup script (one M line per line) |

## Examples

```
JavaM> W "Hello, World!",!
Hello, World!
JavaM> S X=5 W X*2,!
10
JavaM> /load routines/FOO.m
Loaded: routines/FOO.m
JavaM> D FOO
  [FOO]
JavaM> S Y=$$FOO() W Y,!
```

## Semantic parity

Both REPLs use the same engines as the corpus runners:
- **JavaM** → `LineInterp` (same as `MjvmCorpusScore`)
- **mJVM** → `MFrontend` + `IrMachine` (same as `MjvmNativeScore`)

Corpus gate: **135/4/0** (4 documented RSM DIVERGENCES). REPL must not become a second semantics.

## CI smoke

The CI runs a scripted session to verify basic functionality:

```sh
printf 'W "ok",!\nS X=5 W X,!\n/quit\n' | java -cp build:lib/... jvmumps.Repl
```
