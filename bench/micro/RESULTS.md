# micro RESULTS

## 2026-10-29 (late) — MRuntime hot paths (mjvm#6/#7/#8, commit 3bd0ecb)

Dispatch levers (mjvm#14, commit 2a8582f): ops-array dispatch + guarded preRead probes —
INTLOOP 0.129→0.119s (238ns/iter, 6.6x GT.M), ASET 0.032→0.029s. `--class` re-measure after
the hot-path work: INTLOOP class=0.070s vs IR=0.119s steady-state (~1.7x, unchanged ratio;
javac still ~0.4s one-shot) — the #9 decision (keep IR default for file runs) stands. The
remaining gap is structural: ~6 Ir ops per M statement with MValue boxing — next lever is op
fusion / superinstructions, not dispatch.

Per-call ablation (warmed JVM, before → after):

| Path | Before | After |
|---|---|---|
| evalExpr("i") — bare var | 37ns | 17ns |
| evalExpr("r+i") — 3-token | 60ns | 38ns |
| evalExpr("i#100+1") — chain | 250ns | 55ns |
| evalExpr('$P(str," ",i#100+1)') | 438ns | 142ns |
| setVar("a(i)") — SubRef cache | 197ns | 47ns |

Phase re-measure (IR machine, best of 5, parity=true all):

| Phase | Before | After | vs GT.M before → after |
|---|---|---|---|
| INTLOOP | 0.159s | 0.129s | 8.8x → 7.2x |
| STRCAT | 0.277s | 0.276s | concat-bound |
| ASET | 0.045s | 0.032s | 3.0x → 2.1x |
| AWALK | 0.256s | 0.229s | 1.12x |
| PIECE | 0.231s | 0.128s | **29x → 16x** |
| GSET | 0.039s | 0.031s | 1.26x |
| GREAD | 0.474s | 0.440s | 1.08x |
| GWALK | 0.153s | 0.128s | 1.2x |

Audit findings (mjvm#6): the hot costs were (1) full parse for every bare-variable
expression (the fast-path gate excluded single tokens), (2) name-string re-parse on every
subscripted setVar/getVar, (3) full parse of arithmetic chains and $P shapes, (4) double map
lookups in getVar. Fixed all four; the remaining gap to GT.M is IrMachine dispatch overhead
(~30-40ns/op × ~6 ops/iteration — INTLOOP's residual ~260ns/iter) plus the piece scan itself;
the next lever is op dispatch, then MExprJava compiling loop bodies for the --class path.

Semantic notes: chains evaluate strictly left-to-right (M has no operator precedence —
C30: 1+2*3=9); fast-path errors fall through to the trap-aware evaluator ($ETRAP transparency
— widening the gate had exposed that the fast path sat outside the trap try).

## 2026-10-28/29 — harness baseline (mjvm#5)

Hosts: U01 x86_64 (GT.M V7.2-001 compiled, YDB r2.06 compiled, in-process $ZUT),
Mac Apple Silicon (mJVM V2.2.2 IR, wall-clock best-of-3 minus startup baseline).
Cross-host deltas are conservative — compare within a host/pair only.

| Phase | GT.M 7.2-001 (U01) | YDB r2.06 (U01) | mJVM IR (Mac) |
|---|---|---|---|
| INTLOOP 500k adds | 0.018s | 0.018s | 0.234s |
| STRCAT 30k appends | 0.158s | 0.165s | 0.456s |
| ASET 100k local sets | 0.015s | 0.011s | 0.143s |
| AWALK 100k local $ORDER | 0.006s | 0.007s | 0.398s |
| STRBUILD 100 pieces | 0.000s | 0.000s | — |
| PIECE 200k $PIECE | 0.008s | 0.011s | 0.298s |
| GSET 50k global sets | 0.072s | 0.019s | 0.140s |
| GREAD 500k global gets | 0.083s | 0.078s | 0.667s |
| GWALK 50k global $ORDER | 0.010s | 0.009s | 0.304s |

Consistent with the 2026-10-28 survey numbers (mJVM IR ~15x GT.M/YDB on compute
loops, $PIECE ~33x; GREAD dominated by store backend). RSM numbers: see
SURVEY.md (V1.83.1; /tmp copy was ephemeral, archive remains at
`U02:coldstore/mac-rsm-2026-10-26/`).

## Store-backend matrix (mjvm#10, 2026-10-29) — global phases via `--store`

CLI single-process wall-clock minus `ph_base` baseline, best of 3 (Mac, IR machine):
memory is the CLI default (its numbers match the RESULTS table above).

| Store | GSET 50k global sets | GREAD 500k gets | GWALK 50k $ORDER |
|---|---|---|---|
| memory (default) | 0.140s | 0.658s | 0.304s |
| sqlite | 32.1–34.0s (≈240x; every set is its own SQL transaction) | impractical scale — same per-op SQL pattern | impractical scale |
| cdb2 | n/a locally — needs `tools/cdb2pipe` + a Comdb2 server (mesh/SQL plane) | n/a | n/a |

Conclusion: memory is the only viable bench backend; sqlite/cdb2 are functional
StoreApi backends, not benchmark targets. GSET at ~640µs/write on sqlite reflects
per-op SQL transactions, not a data-point for engine comparison.

## mJVM `--class` (compiled/JIT path) — measured after the mjvm#11 fix (2026-10-29)

`--class` was unfixably broken until mjvm#11 (undefined label on every FOR loop); fixed in
09139c4. Steady-state numbers below (warmed JVM, source-hash class cache — no re-javac; fresh
MRuntime per run; best of 5; parity-checked against the IR machine on every phase):

| Phase | IR machine | --class (compiled) | Speedup | Parity |
|---|---|---|---|---|
| INTLOOP 500k adds | 0.159s | 0.078s | 2.0x | ok |
| STRCAT 30k appends | 0.277s | 0.271s | 1.0x | ok |
| ASET 100k local sets | 0.045s | 0.030s | 1.5x | ok |
| AWALK 100k local $ORDER | 0.256s | 0.224s | 1.1x | ok |
| PIECE 200k $PIECE | 0.231s | 0.183s | 1.3x | ok |
| GSET 50k global sets | 0.039s | 0.029s | 1.3x | ok |
| GREAD 500k global gets | 0.474s | 0.368s | 1.3x | ok |
| GWALK 50k global $ORDER | 0.153s | 0.132s | 1.2x | ok |

One-shot javac cost: ~0.41–0.44s per program (fresh CLI process — the class cache is per-JVM).

**Findings.** (1) The compiled path's control flow is now correct and modestly faster
(1.0–2.0x steady-state) — but both engines bottleneck on `rt.evalExpr("...")` in loop bodies:
the emitter compiles control flow and stack ops, not the arithmetic/expression bodies. The big
wins remain where #6/#7/#8 point: compile loop-body expressions (`r+i`, `$P`, dense local
subscripts) instead of delegating to the runtime evaluator. (2) For one-shot file runs, javac
(~0.4s) costs more than the entire IR interpretation of a typical phase — `--class` is a net
loss for short scripts and a win only for hot loops / long-lived processes. Recommendation:
keep IR as the default for file runs; use `--class` opt-in (already the case).
