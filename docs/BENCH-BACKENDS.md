# Store backends — SQLite vs Comdb2 (jvmumps#31)

**Date:** 2026-10-15 · **Host:** U01 (x86_64, 6 cpus, 31 GB, OpenJDK 21) · **Tree:** `0c7e536` (post-v0.4.0)  
**Tables:** [BENCH.md](BENCH.md) · findings [PERF-REVIEW.md](PERF-REVIEW.md)

---

## Recommendation

| Use case | Default backend | Why |
|----------|-----------------|-----|
| **Corpus / CI / single-node tests** | **`SqlStore` (SQLite)** | In-process, no RPC, MATCH=131/8/0, `make smoke` green; fastest SET/GET |
| **Multi-user / mesh / large `$ORDER` trees** | **`Cdb2Store` (Comdb2, grouped)** | ~6× faster walk; cluster (U01/U02); SQL plane share |
| **Load / bulk publish** | **Cdb2 `SETB`** (or SqlStore + batch txn) | Per-node SqlStore SET is only 6.4K ops/s |
| **In-memory / direct mode** | **`MStore`** | 200K+ SET/s; not durable |

**One-liner:** *SQLite for the test harness and laptop; Comdb2 for the published mesh and multi-host `$ORDER`.*

---

## Comparison (U01)

| Metric | `MStore` | `SqlStore` | `Cdb2Store` |
|--------|---------:|-----------:|------------:|
| SET ops/s | 291K–552K | **6.4K** | ~2K grouped (`SETB`) · ~120/s durable/autocommit |
| GET ops/s | 1.8M–2.7M | **36K** | RTT-bound (~5 ms local) |
| `$ORDER` walk 2k | 426–1894 ms | **732 ms** | **854 ms** (2k) · **10.1 s @ 20k** vs SqlStore **64.7 s** |
| KILL subtree | ~0 | 2.7 ms | prefix delete |
| Locks | in-process | `mlocks` + `LockQueue` | default SPI (no cross-process yet) |
| TSTART undo 500 rows | — | TCOMMIT 0.80 ms | n/a (no tx undo) |
| Contended lock 64 thr | — | **0 timeouts** | n/a |
| Durability | none | SQLite file | Comdb2 cluster |
| MATCH corpus | 131/8/0 (LineEngine) | **131/8/0** | order/mesh parity OK (see below) |

### Corpus contract under each store

| Store | Evidence |
|-------|----------|
| SqlStore / LineEngine | `MjvmNativeScore` **MATCH=131 MISS=8 ERR=0** |
| Cdb2Store | `Cdb2OrderParitySmoke` + `MultiLevelOrderSmoke` — identical `$ORDER` sequences; mesh `related()` OK |

*(Full M corpus on Cdb2Store is the same `StoreApi`; smoke parity is the regression stand-in until a Cdb2 `MjvmNativeScore` run is added to `make itest`.)*

---

## Commit modes

| Mode | SqlStore | Cdb2Store |
|------|----------|-----------|
| Single `$SET` | autocommit (JDBC) | UPDATE+INSERT→**one `INSERT OR REPLACE`** + client buffer |
| Bulk | N autocommits | **`SETB` multi-row** (≤300 rows / statement; bind cap) |
| Group commit | possible (future WAL) | **default** (`flush()` / BATCH=300) |
| Cross-process lock | `mlocks` + `LockQueue` | not implemented (SPI default) |

---

## Hardware / flags

- U01: 6 cpus, 31 GB, SQLite temp files, Comdb2 `testdb local`, JDK 21  
- No `JAVA_TOOL_OPTIONS`; SQLite JDBC 3.45.1 (vendored)  
- Cdb2: `/opt/bb/bin/cdb2sql` / `tools/cdb2pipe.c` (cdb2api), engine `eb6dfab` cluster U01+U02

---

## Reproduce

```bash
java jvmumps.StoreLockBench 2000 64
java jvmumps.SqlOrderBench
java jvmumps.Cdb2OrderBench tools/cdb2pipe testdb local 2000
java jvmumps.Cdb2MeshBench tools/cdb2pipe testdb local 2000 50
java jvmumps.MjvmNativeScore tests/conformance.json
make smoke   # SqlStore path, 99/99
```
