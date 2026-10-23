# mJVM — M (MUMPS) as a JVM-Native Language

**mJVM** compiles M to JVM bytecode with 24× JIT speedup over IR interpretation.

**Status:** Production engine. JavaM is the frozen semantic reference oracle.

## Quick start

```bash
make build
java -cp build:lib/* mjvm.cli.Mjvm "S X=1 W X,!"
```

## Architecture

```
MFrontend.compile ──→ List<Ir> ──→ JavaSourceEmitter ──→ JIT bytecode
                                                              │
                                                         C2 JIT (24x)
                                                              │
                                                        StoreApi
                                                              │
                                              MStore / H2Store / MapDBStore / TieredStore
```

## Storage backends

| Store | Peak TPS | Persistence | Crash Safety |
|-------|----------|-------------|--------------|
| MStore | 32,400 | No | No |
| H2Store | 19,000 | Yes | Yes |
| MapDBStore | 3,700 | Yes | Yes |
| TieredStore | — | Yes (WAL) | Yes (WAL) |

## Performance

| Method | Time | Speedup |
|--------|------|---------|
| IR interpreter | 21.7 ms | 1× |
| **JIT bytecode** | **0.9 ms** | **24×** |

## Verification

| Check | Result |
|-------|--------|
| VistA full run | 99.97% (33,942/33,951) |
| Bank benchmark | 707 txn/ms peak |
| Smoke | 106 OK |

## CLI

```bash
mjvm -V                    # version
mjvm -h                    # help
mjvm "S X=1 W X,!"        # run code (IR path)
mjvm --useClass "S X=1"   # run code (JIT path, 24x faster)
mjvm --store h2 app.db    # use H2Store
mjvm --store tiered app.db # use TieredStore
```

## Development

```bash
make build    # compile
make smoke    # run test suite
make bench    # run benchmarks
```

## Docs

- `docs/DESIGN-TIERED-STORE.md` — TieredStore design
- `docs/CRITICAL-PATH-2026-10-20-v2.md` — development history

## Related

- **JavaM** (archived) — frozen semantic reference oracle
- **m-semantics** — Dafny formal proofs (403 lemmas, 0 errors)
