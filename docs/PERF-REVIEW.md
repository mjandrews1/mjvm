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

## 2026-11-03 tranche (mjvm#14/#43 follow-up, uncommitted at capture)

Safe interpreter hot-path wins landed in `Ir`/`IrMachine`/`MRuntime` (no MFrontend changes):
- PUSH string constants now share one immutable `MValue` per op (`Ir.constVal`) instead of allocating per push.
- `MRuntime.setVar` takes the String-key store path for simple names — skips the `pathOf` ArrayList allocation per STORE.

Measured on the Mac (JDK 27, MjvmBench 200k): IR store loop 5.23M → **5.86M it/s (+12%)**; IR inc loop ~flat (3.36M → 3.39M it/s); INTLOOP 2M wall unchanged (JVM-startup dominated). Corpus 139/139, smoke all green (TieredStoreRwSmoke failure is pre-existing on HEAD 7b6aee1), ORACLE 129 cases: pass=81 diverge=0 err=0 upstream=20 known-bugs=28.

Remaining #14 residual is structural: locals live in a HashMap + every STORE writes through the store tree (TieredStore WAL-append per set; MStore tree write per set). Closing it needs per-op local slot caching with NEW/KILL invalidation — spec before code; do not hack around $DATA/$ORDER-on-locals, which read through the store.

## 2026-11-03 slot-cache experiment (mjvm#14) — implemented, measured, REVERTED

Implemented a per-op locals slot cache (Ir.slot → stable slot ids in an MRuntime slot table
replacing the locals HashMap; LOAD/STORE hot path became one int compare + array read).
Correctness was clean (corpus 139/139, ORACLE 129 diverge=0, smoke green), and the JFR profile
confirmed the map probe disappeared (MRuntime.setVar 86→24 samples, MStore.set 76→11) — but the
A/B wall-clock loop (SL20, 20M iters, alternating runs vs 485a79a) came out ~18% SLOWER
(3.12s vs 3.69s avg): the loop is bound by numeric boxing at STORE (MValue.ofNumber + mstr per
set, now the top frame at 106 samples) plus dispatch, and the added per-op slot branches cost
what they saved. Reverted; HEAD stays at 485a79a (tranche 2, store loop +12%).

Real next step for #14/#43: keep numeric locals as doubles in the slot table (parallel double[])
and box lazily only when a string consumer needs asStr() — but the store write-through forces an
MValue per STORE because $DATA/$ORDER read the store tree. So the design must add a numeric-aware
local path in the store (setNum-with-tree-visibility) before this pays. Spec only — do not ship
partial.
