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

## Performance benchmarks

### BankFull (10K accounts, 4 instances, 25K txn/inst)

| Mode | txn/ms | vs Java | Invariant | Thread-Safe |
|------|--------|---------|-----------|-------------|
| **mJVM JIT (BankFullM)** | **10,155K** | **1.76×** | ✅ | Single-threaded |
| **Java native (sync)** | **5,776K** | 1× | ✅ | `synchronized` |
| **mJVM IR (baseline)** | **127K** | 0.02× | ✅ | Single-threaded |

### All configurations

| Configuration | Java | JIT | JIT/Java |
|---------------|------|-----|----------|
| Objects + no sync | 2.1M | 10.2M | 4.9× |
| Primitive + sync | 5.8M | 10.2M | **1.76×** |
| Primitive + no sync | 8.5M | 10.2M | 1.21× |

### Three Performance Metrics

| Metric | MStore | TieredStore | H2Store |
|--------|--------|-------------|---------|
| **Throughput** | 7.5M ops/s | 4.4M ops/s | 2.8M ops/s |
| **Read p99** | 0.1μs | 0.3μs | 0.5μs |
| **Write p99** | 0.3μs | 0.5μs | 1.0μs |
| **Invariant** | ✅ | ✅ | ✅ |

### GAUNTLET: 134/134 correctness + latency gates

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
| MFrontendRegression | 30/30 (10 bug classes) |
| GAUNTLET | 134/134 (correctness + latency gates) |
| DafnyBoundarySmoke | 209/209 |
| BankFull (Java) | invariant OK (13.5M txn/ms) |
| BankFull E2E (M) | invariant OK (journal + CRC) |
| StoreConcurrencySmoke | 5/5 |
| Dafny proofs | 464 verified, 0 errors (6 .dfy files) |
| VistA full run | 99.97% (33,942/33,951) |

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
