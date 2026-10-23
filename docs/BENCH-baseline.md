# Baseline — u01

- **Host:** u01 · Linux 7.0.0-31-generic x86_64
- **CPU / RAM:** 6 cpus · 31 GB
- **Commit:** df89769 v0.4.0
- **JDK:** openjdk version "25.0.4.1" 2026-08-18
- **Date:** 2026-10-15T20:11:50Z

## Metrics matrix (filled)

| # | Metric | Unit | Reproduce | Result |
|--:|--------|------|-----------|--------|
| 1 | Corpus wall time | s | `java jvmumps.CorpusRunner tests/conformance.json` | **0.04s** · J0 RUNNER OK cases=139 engine=StubEngine |
| 2 | Corpus score | MATCH/ERR/MISS | `java jvmumps.MjvmCorpusScore …` / `MjvmNativeScore` | MJVM CORPUS MATCH=131 MISS=8 ERR=0 · MJVM-NATIVE MATCH=131 MISS=8 ERR=0 |
| 3 | IR arith loop | it/s | `java mjvm.bench.MjvmBench 50000` | 574104 |
| 4 | IR cat loop | it/s | `java mjvm.bench.MjvmBench 50000` | 227890 |
| 5 | Class vs IR speedup | × | `java mjvm.bench.MjvmBench 50000` (unroll) | 0.32 |
| 6 | Compile latency | ms | `java mjvm.bench.MjvmBench 50000` | 2.019 |
| 7 | SET/GET bulk | ops/s | `java mjvm.bench.PerfMatrix 50000` | sqlite set 6820 / get 38185 ops/s |
| 8 | $ORDER walk | ms / n | `java mjvm.bench.PerfMatrix 50000` | sqlite 1093.26 ms / 2500 keys |
| 9 | TSTART/TCOMMIT | ms | lock/tx smokes (see #100) | *pending #100* |
| 10 | Lock acquire uncontended | µs | `java jvmumps.SqlLockMeasureSmoke` | LOCK MEASURE OK uncontended_us=6186 contended_timeout_ms=149 serialized=8 max_wait_ms=60 last_wait_ms=61 |
| 11 | Lock stress P50/P99 | ms | `java jvmumps.SqlLockStressSmoke` | LOCK STRESS threads=50 iters=5 samples=250 granted=250 timeouts=0 timeout_ms=10000 wall_ms=486 WAIT_MS mean=77.80 p50=0 p90=296 p99=457 max=471 LOCK STRESS OK  |
| 12 | Startup to first output | s | `java mjvm.cli.Mjvm -V` | **0.01s** |
| 13 | Peak RSS (make smoke) | s / KB | `make smoke` | 30.13 200224 |
| 14 | RSM oracle compare | ratio | `rsm` + same corpus | *pending #99* |

## PerfMatrix detail

```
| Metric | Value |
|--------|------:|
| `host.os` | Linux 7.0.0-31-generic |
| `host.arch` | amd64 |
| `host.cpus` | 6 |
| `host.java` | 25.0.4.1 (Ubuntu) |
| `host.maxheap_mb` | 7860 |
| `m.irexec.compile_line_ms` | 0.041 |
| `m.irexec.compile_line_ops` | 8 |
| `m.irexec.compile_fragment_ms` | 0.071 |
| `m.irexec.compile_fragment_ops` | 8 |
| `mjvm.ir.loop_inc_ops` | 2328761 |
| `mjvm.ir.loop_inc_ms` | 21.47 |
| `mjvm.ir.loop_cat_ops` | 340685 |
| `mjvm.ir.loop_cat_ms` | 146.76 |
| `mjvm.ir.loop_store_ops` | 4298513 |
| `mjvm.ir.loop_store_ms` | 11.63 |
| `store.mstore.n` | 2500 |
| `store.mstore.set_ms` | 3.53 |
| `store.mstore.get_ms` | 1.0 |
| `store.mstore.order_walk_ms` | 510.78 |
| `store.mstore.order_walk_keys` | 2500 |
| `store.mstore.set_ops` | 708839 |
| `store.mstore.get_ops` | 2499782 |
| `store.sqlite.n` | 2500 |
| `store.sqlite.set_ms` | 366.55 |
| `store.sqlite.get_ms` | 65.47 |
| `store.sqlite.order_walk_ms` | 1093.26 |
| `store.sqlite.order_walk_keys` | 2500 |
| `store.sqlite.set_ops` | 6820 |
| `store.sqlite.get_ops` | 38185 |
| `lock.uncontended_us` | 181 |
| `footprint.heap_before_mb` | 2 |
| `footprint.heap_after_store_mb` | 15 |
| `footprint.heap_delta_mb` | 12 |
| `footprint.rss_kb` | 457024 |

```

Captured 2026-10-15T20:12:26Z on u01.
