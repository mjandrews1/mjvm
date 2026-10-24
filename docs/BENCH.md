# BENCH — JavaM + mJVM performance review (jvmumps#97)

**Status:** v0.5.0 review cycle · findings: [PERF-REVIEW.md](PERF-REVIEW.md)

**Status:** baseline capture in progress · **Milestone:** [v0.5.0](https://github.com/mjandrews1/JavaM/milestone/2)  
**Roadmap:** [ROADMAP-0.5.0.md](ROADMAP-0.5.0.md) · **Harness:** `mjvm.bench.PerfMatrix` · `tools/perf_review.sh`

This file is the **single place** that answers: *how fast is this stack, where does it lose, and is it getting better?*

---

## Metrics matrix (#98)

One row per metric: **unit · reproduce command · stacks**. Baseline column lives in the **Baselines** section (U01 = host of record).

| # | Metric | Unit | Reproduce command | Stacks | Target / gate |
|--:|--------|------|-------------------|--------|---------------|
| 1 | Corpus wall time | s | `java jvmumps.CorpusRunner tests/conformance.json` | JavaM, mJVM | baseline only |
| 2 | Corpus score | MATCH/ERR/MISS | `java jvmumps.MjvmCorpusScore` / `MjvmNativeScore` | JavaM, mJVM | **131 / 0 / 8** |
| 3 | IR arith loop | it/s | `java mjvm.bench.MjvmBench 50000` | mJVM | ≥ 1e6 |
| 4 | IR cat loop | it/s | `java mjvm.bench.MjvmBench 50000` | mJVM | ≥ 1e5 |
| 5 | Class vs IR speedup | × | `java mjvm.bench.MjvmBench 50000` (unroll x200) | mJVM | class runs · 64KB method limit |
| 6 | Compile latency | ms | `java mjvm.bench.MjvmBench 50000` | mJVM | ≤ 50 |
| 7 | SET/GET bulk | ops/s | `java mjvm.bench.PerfMatrix 50000` | JavaM, mJVM | baseline |
| 8 | `$ORDER` walk | ms / keys | `java mjvm.bench.PerfMatrix 50000` | JavaM, mJVM | baseline |
| 9 | TSTART/TCOMMIT | ms | lock/tx smokes (extend with timers) | JavaM | pending #100 |
| 10 | Lock acquire uncontended | µs | `java jvmumps.SqlLockMeasureSmoke` | JavaM | ~6–10 ms historical; µs on warm |
| 11 | Lock stress P50/P99 | ms | `java jvmumps.SqlLockStressSmoke` | JavaM | 0 timeouts |
| 12 | Startup to first output | s | `java mjvm.cli.Mjvm -V` | both | baseline |
| 13 | Peak RSS (make smoke) | s / KB | `make smoke` + `/usr/bin/time` | both | baseline |
| 14 | RSM oracle compare | ratio | `python3 tools/rsm_routine_runner.py --mode stdin` | RSM | **0.023 s · 5953 cases/s** · LineEngine 43 ms → RSM ~1.9× wall |

## Three Performance Metrics (2026-10-23)

Beyond peak TPS. **Peak TPS is misleading** — hides tail latency and degradation.

| # | Metric | What it catches | Tool |
|---|--------|----------------|------|
| 1 | **Sustained throughput** | Degradation over time (GC, cache, disk) | `LatencyBench` |
| 2 | **Response time** (p50/p95/p99) | Tail latency — what the user *feels* | `StoreMetrics.latencyReport()` |
| 3 | **Transactional accuracy** | Invariants under concurrency | `BankTimedBench` + `StoreConcurrencySmoke` |

### Results — Mac (aarch64 · Java 27 · 2026-10-23)

#### (1) Sustained Throughput (1M ops)

| Store | Throughput | Total Time |
|-------|-----------|------------|
| **MStore** (memory) | **7,567K ops/sec** | 132 ms |
| **TieredStore** (cache+WAL) | **4,374K ops/sec** | 229 ms |
| **H2Store** (disk) | **2,791K ops/sec** | 358 ms |

#### (2) Response Time — p50/p95/p99

| Store | Read p50 | Read p95 | Read p99 | Write p50 | Write p95 | Write p99 |
|-------|----------|----------|----------|-----------|-----------|-----------|
| **MStore** | 0.0μs | 0.1μs | **0.1μs** | 0.1μs | 0.1μs | **0.3μs** |
| **TieredStore** | 0.1μs | 0.1μs | **0.3μs** | 0.1μs | 0.3μs | **0.5μs** |
| **H2Store** | 0.1μs | 0.3μs | **0.5μs** | 0.3μs | 0.3μs | **1.0μs** |

**Key finding:** TieredStore p99 beats H2Store (0.3μs vs 0.5μs read, 0.5μs vs 1.0μs write) — WAL buffer absorbs disk latency.

#### (3) Accuracy

| Test | Result |
|------|--------|
| Bank double-entry invariant (10M txns) | ✅ **OK** |
| Concurrency stress (8 threads × 500K ops) | ✅ **5/5 PASS** |

#### Reproduce

```bash
java -cp "build:lib/*" jvmumps.LatencyBench        # metrics 1+2
java -cp "build:lib/*" jvmumps.BankTimedBench mem --jit 10000000  # metric 3
java -cp "build:lib/*" jvmumps.StoreConcurrencySmoke  # metric 3 (concurrent)
```

**Capture (fixed host):**

```bash
./tools/capture_baseline.sh u01 docs/BENCH-baseline.md
# or raw matrix
java -cp build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar mjvm.bench.PerfMatrix --json 50000
```

## Baselines

### Mac (aarch64 · 10 cpus · OpenJDK 27) — 2026-10-15

Captured with `mjvm.bench.PerfMatrix 50000` on the v0.4.0 tree.

| Metric | Value | Notes |
|--------|------:|-------|
| C1 compile line | **0.031 ms** | well under 50 ms target |
| I1 IR inc | **2.22M it/s** | target ≥ 1M |
| I2 IR store | **4.34M it/s** | |
| I3 IR cat | **524K it/s** | target ≥ 100K |
| S1 mstore set | **880K ops/s** | |
| S1 mstore get | **3.58M ops/s** | |
| S2 sqlite set | **3.0K ops/s** | n=2500; matches SqlOrderBench scale |
| S2 sqlite get | **79K ops/s** | |
| S2 sqlite `$ORDER` walk | **632 ms / 2500 keys** | |
| L1 uncontended lock | **372 µs** | |
| F1 heap delta (2500 nodes) | **11 MB** | |

`MjvmBench` M8 targets on this host remain PASS (see `docs/mjvm-perf.md`).

### U01 (x86_64 · 6 cpus · Ubuntu OpenJDK 25) — 2026-10-15 · **host of record**

Full matrix dump: [`docs/BENCH-baseline.md`](BENCH-baseline.md) (`tools/capture_baseline.sh`). Row 5 class unroll filled (0.32× on U01; unroll x200 is 64KB-safe). Row 14 (RSM) pending #99.

`./tools/perf_review.sh 50000` · host `Linux 7.0.0-31-generic` · **baseline of record**

| Metric | Value | Mac (same n) | Notes |
|--------|------:|-------------:|-------|
| C1 compile line | **0.051 ms** | 0.031 ms | |
| I1 IR inc (PerfMatrix, warm) | **1.82M it/s** | 2.22M it/s | |
| I2 IR store | **4.27M it/s** | 4.34M it/s | |
| I3 IR cat | **304K it/s** | 524K it/s | still ≥ 100K |
| S1 mstore set | **703K ops/s** | 880K ops/s | n=2500 |
| S1 mstore get | **2.44M ops/s** | 3.58M ops/s | |
| S2 sqlite set | **6.8K ops/s** | 3.0K ops/s | |
| S2 sqlite get | **38K ops/s** | 79K ops/s | |
| S2 sqlite `$ORDER` walk | **1098 ms / 2500** | 632 ms / 2500 | |
| L1 uncontended lock | **164 µs** | 372 µs | |
| F1 heap delta | **12 MB** | 11 MB | |
| F1 RSS | **487 MB** | n/a | Linux `/proc` |

**Findings from this capture**

1. PerfMatrix IR numbers are the **warm** floor; `MjvmBench` cold IR inc was **564K it/s** (below 1M target) — warmup matters for the baseline of record.
2. `ClassEmitter.emit` **failed on U01** (`javac failed for B…`) under OpenJDK 25 — class-unroll row is Mac-only until #101/#99 diagnoses the generate/javac path.
3. SQLite walk is the weak spot on both hosts (aligns with the Cdb2 6× walk win).

---

## Cross-stack compare (#99)

Corpus wall + score on **U01** (2026-10-15) — same `tests/conformance.json`.

| Stack | Surface | Score | Wall | cases/s | Notes |
|-------|---------|------:|-----:|--------:|-------|
| JavaM | `LineEngine` / `LineInterp` | **131 / 8 / 0** | **43 ms** | 3236 | `mjvm.bench.CrossStackBench` |
| mJVM | `MjvmCorpusScore` (IR) | **131 / 8 / 0** | 90 ms | 1544 | includes JVM startup |
| mJVM | `MjvmNativeScore` | **131 / 8 / 0** | 90 ms | 1544 | native path |
| RSM oracle | **persistent stdin** ([#113](https://github.com/mjandrews1/JavaM/issues/113)) | 98 / 41 / 0 | **23 ms** | **5953** | one `rsm` process · marker protocol |

**RSM note (row 14):** `tools/rsm_routine_runner.py` drives **one long-lived `rsm`** via stdin markers (no process spawn). Wall **0.023 s / 139 cases (5953 cases/s)** vs old `rsm -x` 0.583 s.

- JavaM LineEngine 43 ms / RSM persistent 23 ms → **RSM ≈ 1.9× faster wall** on this corpus shape (process setup dominates the old ratio)
- Remaining misses include DO/XECUTE/labels and RSM vs corpus error-text shape; a **ZLINK routine file** mode is sketched in the runner (`--mode routine`) for true routine execution

**Class unroll (row 5):** filled — U01 **0.32×** class/IR at unroll x200 (64KB-safe).

---

## Store + lock (#100)

### Bulk ops (U01, `jvmumps.StoreLockBench`, n=2000)

| Op | MStore | SqlStore | Cdb2 (grouped) |
|----|-------:|---------:|---------------:|
| SET | 291K/s | **6.4K/s** | ~900/s raw · grouped SETB ~2K/s |
| GET | 1.77M/s | **36K/s** | (order-path RTT) |
| `$ORDER` walk 2k | 426 ms | **732 ms** | **854 ms** (2k) |
| KILL subtree | ~0 ms | 2.7 ms | |

Cdb2 20k comparison (earlier): insert 13.4 s · walk **10.1 s** vs SQLite 64.7 s.

### TSTART / TCOMMIT / TROLLBACK (SqlStore undo, per txn)

| Rows in undo | empty TSTART+TCOMMIT | TCOMMIT | TROLLBACK |
|-------------:|---------------------:|--------:|----------:|
| 0 | 0.005 ms | — | — |
| 10 | — | 0.07 ms | 0.04 ms |
| 100 | — | 0.21 ms | 0.20 ms |
| 500 | — | 0.80 ms | 0.82 ms |

Undo cost is **O(rows)** on rollback/commit fold — linear and small.

### Contention sweep (one key, 5 acquires/thread, U01)

| Threads | wall ms | granted | timeouts | p50 | p99 |
|--------:|--------:|--------:|---------:|----:|----:|
| 1 | 3 | 5 | 0 | 0 | 1 |
| 4 | 11 | 20 | 0 | 0 | 8 |
| 8 | 16 | 40 | 0 | 0 | 15 |
| 16 | 43 | 80 | 0 | 0 | 33 |
| 32 | 118 | 160 | 0 | 0 | 108 |
| 64 | 711 | 320 | 0 | 0 | 681 |

**No timeouts** through 64 threads (8s budget). p99 grows ~linearly with queue depth.

### Multi-key vs single-key (32 threads × 20)

| Keys | wall | granted | timeouts |
|-----:|-----:|--------:|---------:|
| 1 | 975 ms | 640 | 0 |
| 4 | **507 ms** | 640 | 0 |

~**1.9×** wall win when lock traffic spreads over 4 keys.

### Reproduce

```bash
java -cp build:lib/... jvmumps.StoreLockBench 2000 64
java -cp build:lib/... jvmumps.Cdb2OrderBench tools/cdb2pipe testdb local 2000
java jvmumps.SqlLockStressSmoke          # adaptive 50×5 or 8×2
```

## Footprint (#101)

U01 · OpenJDK 21 · `java mjvm.bench.FootprintBench 200 50000` + `/usr/bin/time`

| Metric | U01 | Mac (aarch64) | Notes |
|--------|----:|--------------:|-------|
| Compile 1 line | **0.043 ms** | 0.031 ms | well under 50 ms |
| Compile 200-line program | **2.39 ms** | 1.62 ms | 404 IR ops |
| Class emit unroll ×200 | **677 ms** | 443 ms | javac-driven (heavy) |
| Class size | 31,799 B | 31,795 B | source 73 KB |
| Heap after 50k IR store loop | 12 → 3 MB | 9 → 4 MB | after `System.gc()` |
| Process RSS (FootprintBench) | **178 MB** | n/a (no /proc) | |
| Startup `mjvm -V` | **0.02 s · 43 MB** | 0.02 s | `/usr/bin/time` |
| CorpusRunner | 0.04 s · 47 MB | | 139 cases |
| MjvmNativeScore | 0.09 s · 61 MB | | 131/8/0 |
| GC collections (sample JVM) | 2 | 8 | note only |

**Class emit** dominates compile-to-run when `--class` is used (~0.5–0.7 s / unroll) — frontend-only is µs–ms.

**Heap flags:** default JVM; no `JAVA_TOOL_OPTIONS`. `make bench` runs PerfMatrix + Footprint + StoreLock.

## Findings (living)

1. **IR arith is the strength** — 2–4M simple loop it/s; well above the 1M floor.
2. **SQLite `$ORDER` walk is the weak spot** vs Comdb2 (~6× on 20k) — walk path is the first optimization target *after* this review freezes a baseline.
3. **ClassEmitter vs IR** is workload-dependent (class wins concat via JIT; IR wins tight arith on cold unroll) — needs a warm steady-state row (#99).
4. **Lock acquire** is fine uncontended; contended matrix is host-sensitive (adaptive `SqlLockStressSmoke`).

*Full one-page findings ship with #102.*

---

## Related

- [ROADMAP-0.5.0.md](ROADMAP-0.5.0.md) · `docs/mjvm-perf.md` (M8 targets) · `docs/SQLSTORE-LOCKS.md`
- Issues [#97](https://github.com/mjandrews1/JavaM/issues/97)–[#102](https://github.com/mjandrews1/JavaM/issues/102)
