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

## 2026-11-03 lazy numeric str (mjvm#14/#43) — LANDED

`MValue.str` is now lazy for numeric values (`ofNumber` no longer allocates an mstr string per
numeric result; materialized on first `asStr()`). All direct `.str` reads converted to `asStr()`
(compiler-enforced via private field; 20 sites). The per-STORE boxing was the top hot-loop frame
after the (reverted) slot-cache experiment.

Verified: corpus 139/139, smoke all green (TieredStoreRwSmoke pre-existing on HEAD), ORACLE 129
cases diverge=0 err=0 known-bugs=28. A/B vs 485a79a on SL20 (20M-iteration `S J=J+1` loop,
alternating runs): 3114 ms → 2287 ms avg (**-27%**); output identical. MjvmBench 1M: inc 5.15M it/s.

## 2026-11-03 #5 residual conclusion (post lazy-str)

Post-lazy-str JFR on SL20: dispatch (IrMachine.runFrame) ~50% of top frames, var access ~25%,
store tree write only ~10% (MStore.setNum is a no-op for locals — the tree is the only store for
locals, and data()/order() read the tree, so the write-through is load-bearing and can't be
skipped without breaking $DATA/$ORDER). Conclusion: #5 is at its agent-safe floor — landed
tranche 2 (+12% store loop) and lazy numeric str (-27% wall on the 20M loop), branch @ 829265b.
Further gains are compiler-level (peephole/EVAL_EXPR in MFrontend — P1b surface) or a
threaded-code/bytecode dispatch redesign. Do not chase the store write per-STORE.

## 2026-11-03 locals slot table (mjvm#14) — LANDED

With locals now map-only (no store write-through, previous commit), the locals HashMap is
replaced by a slot table (stable slot ids in arrays; `Ir.slot` caches the slot per LOAD/STORE
op site — one int compare per access once resolved). SL20 A/B vs 0f2a3db (20M-iter loop,
alternating): 1918ms -> 1547ms (**-19%**); cumulative vs 485a79a: 3114ms -> 1547ms (**-50%**).
Corpus 139/139, smoke green, ORACLE 129 diverge=0 err=0 known-bugs=28, TX-rollback/$DATA probes
correct. (The earlier slot-cache attempt regressed -18% because STORE still wrote the store
tree; with map-only locals it wins cleanly.)

## 2026-11-04 (step-8 optimization, branch perf/extrinsic-ops-cache)

First profile-driven lever: `IrMachine.run()` re-copied the whole program
(`prog.toArray`) on **every** nested DO/$$ call — for the ingest that's ~2,663 `$$LOADXML`
calls × a 1,490-op program. Now the Ir[] is cached per program (identity-keyed OPS_CACHE).

CAT ingest (mjvm_full.m, 15 MB / 2,663 records, Mac JDK 21, wall incl. JVM startup):
baseline (main `ce38481`) 1265/1305/1284 ms → after 1279/1259/1218 ms (**~2–3%**, gates green).
Small because the dominant cost is dispatch itself, not the copy. Next levers remain op
fusion/superinstructions (dispatch ~40%) and extrinsic frame cost (~27%) per the JFR profile.
