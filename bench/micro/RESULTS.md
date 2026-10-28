# micro RESULTS

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

## mJVM `--class` (compiled/JIT path) — BLOCKED (mjvm#9)

**`--class` cannot compile any program containing a FOR loop.**
`JavaSourceEmitter` emits `break L<n>;` for forward loop exits, but the label
exists only as a plain statement (`L<n>: ;`) — not a labeled loop/block — so
javac fails: `error: undefined label: L17`. Verified on every phase with a
loop; loop-free programs (e.g. the REPL smoke) compile fine, which is why
this survived earlier smoke tests. Also visible in the same generated source:
`continue L_root;` where the IR intends "back to L21" (inner loop head) —
the emitter maps loop-back edges to the root label, which would re-execute
loop init if the label bug is fixed without also fixing back-edge targets.

JIT measurement (mjvm#9) is therefore blocked on the emitter fix (filed
separately). IR numbers above are the valid mJVM baseline.
