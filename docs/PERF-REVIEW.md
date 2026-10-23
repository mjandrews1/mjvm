# Perf review — one-page findings (jvmumps#102)

**Baseline:** 2026-10-15 · U01 (x86_64, 6 cpus, 31 GB, OpenJDK 21) + Mac (aarch64, 10 cpus, OpenJDK 27)  
**Commit:** `28353a9` (v0.4.0 tree + perf harness) · **Corpus:** 131 / 8 / 0  
**Tables:** [BENCH.md](BENCH.md) · [BENCH-baseline.md](BENCH-baseline.md) · [ROADMAP-0.5.0.md](ROADMAP-0.5.0.md)

---

## Top 3 wins

1. **Comdb2 `$ORDER` walk is ~6× faster than SqlStore** on 20k keys (10.1 s vs 64.7 s U01). Indexed `(name,sk)` beats SqlStore’s per-step cursor. Best path for MeshGraph-style trees.
2. **mJVM IR arith is strong** — 2–4M simple loop it/s (warm), well above the 1M floor. Store loop ~4.3M/s. This stack is not “slow Java M”; the interpreter core is competitive.
3. **Locks scale cleanly through 64 threads** (0 timeouts, p99 linear in queue depth). Multi-key traffic is ~1.9× faster wall than one hot key. `SqlLockStressSmoke` auto-sizes so slow LXC still passes.

## Top 3 losses / surprises

1. **SqlStore bulk SET is ~6.4K ops/s** (in-process SQLite file, one autocommit/node). Fine for tests; painful for load paths. Group-commit or Cdb2 `SETB` is the lever.
2. **`ClassEmitter` + javac is the compile-to-run cliff** — ~450–700 ms to emit a 200-op class vs **0.03–0.05 ms** frontend. Also **64KB method limit**: unroll ×1000 dies (`code too large`); ×200 is safe. Warmup matters: cold MjvmBench IR inc 560K vs warm PerfMatrix 1.8M it/s on U01.
3. **`$ORDER` walk is O(n) steps × index lookup** and **MStore walk is O(n²)** (~400–1900 ms / 2k). SqlStore walk is also weak vs Comdb2. Tree walks are a primary optimization target *after* this baseline freezes.

## Recommended next bets (ordered)

1. **Freeze this baseline** into the `#44` regression rule (>5–10% on matrix rows) and m-semantics#19 cadence.
2. **Persistent RSM routine runner** ([#113](https://github.com/mjandrews1/JavaM/issues/113)) — fair row-14 ratio and oracle harness for DO/XECUTE cases.
3. **SqlStore write path** (WAL / batch / page size) — lift 6.4K SET/s before anyone builds load tools on SqlStore.
4. **ClassEmitter CFG → Java loops** (M8b) so class path can compile real programs without 64KB unroll games; then warm steady-state class vs IR (#99 follow-up).
5. **Cdb2Store as mesh/SQL default** for multi-host trees; keep SqlStore for single-node CI/corpus.

## Explicit non-findings (not measured)

- **RSM throughput ratio** — `rsm -x` process-per-case is harness-limited (38 Z12/Z13); needs #113.
- **LineInterp vs mJVM IR per-command latency** — only corpus wall (43 ms vs 90 ms) is recorded.
- **GC pause distributions** — collection counts only, not pause times.
- **REPL startup-to-prompt** — `mjvm -V` only (#35/#36).
- **Comdb2 under multi-writer contention** — single-client benches only.
- **Production RSM multi-block DBs** / global buffer sizing.
- **FileMan Tier A** — out of scope until 1.0 gate.

---

*Honesty: numbers are from one dated fixed-host run; rerun `make bench` / `tools/capture_baseline.sh` before quoting in a release.*
