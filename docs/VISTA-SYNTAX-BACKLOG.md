# VistA swallowed-syntax backlog — analysis and plan

**Ledger run:** v2.2.2, 2026-10-26, native MFrontend over the FOIA corpus
(U02 `~/vista-hist.log`, 33,951 routines; corpus at
`~/coldstore/vista-foia/VistA-M-master`).

**Topline (baseline):** 21,452 routines (63.2%) had at least one line
silently replaced with RET — 223,453 ledger entries across 5,377 classes.
The 8 hard failures (7 DATA + 1 ENGINE GAP) are unchanged and tracked
separately.

## Progress (updated 2026-10-27)

| Metric | Baseline | After class 1 | After class 2 |
|---|---|---|---|
| Routines with swallowed lines | 21,452 (63.2%) | 18,811 (55.4%) | **14,430 (42.5%)** |
| Total ledger entries | 223,453 | ~84,500 | **60,225** |
| Dotted continuation entries | 143,964 | 2,480 | 2,483 |
| SET target entries (+ tail) | 26,889 | 27,743 | 1,180 |
| Hard failures | 8 | 8 | 8 |

- **Class 1 (dotted, stage 1) — DONE** (`bc23951`, `9727848`): mid-line
  argless D merger + space-separated dot levels + syn-body RET isolation.
  Also fixed a pre-existing runtime bug invisible to the ledger:
  `compileInto` never shifted DO/GOTO patch indices by the insertion
  base, so `F … D` + block emitted `CALL_PC -1` and silently produced no
  output.
- **Class 2 (SET targets) — DONE** (`d376761`): `S (A,B)=` multi-target
  lists (value evaluated once) + quote-aware subscript scans.
- Remaining residue after class 2 (60,225 entries / 7,451 classes):
  **comma 17,057 (28.3%)** → **postcond 16,204 (26.9%)** → @ 4,168 →
  dotted residue 2,483 (dotted lines with no argless-D head) →
  $ 1,182 → SET residue 1,180 → tail 9,070 (5,553 of them
  "SET requires = after" shapes incl. single-element lists
  `S (X)=`, quoted-string targets, `.BYREF` in lists).
- Small upticks in comma/postcond/@ after each fix are unmasking —
  formerly-dotted lines now reach their inner unsupported constructs.

## Histogram (entries ≥ 100) — baseline v2.2.2

| Class | Entries | Share |
|---|---|---|
| Dotted-line continuation (`.`) | 143,964 | 64.4% |
| SET target must be a name | 26,889 | 12.0% |
| Post-conditional (`:`) at command dispatch | 15,991 | 7.2% |
| Comma after a single-arg command (`D ^A,^B`) | 15,931 | 7.1% |
| Other token `!` (WRITE arg starting `!`) | 2,254 | 1.0% |
| Argument indirection (`@`) | 3,973 | 1.8% |
| Other token `^` (global at command scan) | 1,865 | 0.8% |
| Function/intrinsic (`$`) | 1,171 | 0.5% |
| `(` — KILL/NEW exclusive paren lists | 926 | 0.4% |
| `'` / `1` / `[` / `"` / `%` / `2` singles | ~2,000 | ~0.9% |
| Long tail (5,357 distinct, 7,426 entries) | 7,426 | 3.3% |
| Compile depth limit | 3 | 0.0% |

The tail is dominated by "SET requires = after …" (quoted `(` inside
subscripts defeats the target scanner) and "unknown command 'X'" (usually a
tag/label misread after a mis-consumed command).

## Class 1 — dotted-line continuation (64.4%)

Corpus census: **578,420 dotted lines** total; levels 1–5+ are 451,468 /
85,078 / 27,951 / 9,391 / 3,059.

- **405,444 (70%)** follow a head line that *ends* with an argumentless `D` —
  already handled by the ZD merger in `compileRoutine`.
- **~173,000** follow heads the merger does not match. Dominant shapes
  (corpus-verified):

```
 I $D(IO("Q")) D  G EXIT              ; if cond: block, then GOTO
 I (TYPE="ACK") D  Q                  ; if cond: block, then QUIT
 S I=0 F  S I=$O(@ROOT@(I)) Q:'I  D  Q   ; per-iteration block; Q after D
```

  I.e. **argumentless D mid-line, with commands after it** (plus leading-IF
  guards). Trailing-token census of unhandled heads: `Q` 39,734; `Q:cond`
  shapes ~20,000+; GOTO/DO targets (`EXIT` 3,015, `END` 1,056, `RC` 771 …).

Semantics (verified against RSM, the frozen ISO implementation —
`compile/routine.c` CHKDOTS, `runtime/run.c` CMDON/CHKDOTS/OPIFA):

1. Each line compiles with its dot level (`CHKDOTS n`).
2. A false IF skips to the matching ELSE, else **end of line** (ISO 7.2.4) —
   the IF governs the rest of the head line. It does *not* govern the block.
3. Argumentless DO pushes a block frame at level+1; if the next line is not
   dotted at level+1 the D is a no-op.
4. A line with fewer dots acts as QUIT (pops the block frame); control
   resumes on the head line **immediately after the D**, so the trailing
   `Q`/`G EXIT` runs after the block.
5. More dots than the current level ⇒ line is skipped (used when entering
   blocks at depth).

**Fix (recommended, staged):** generalize the ZD merger from
`endsWith(" D")` to *any* argumentless D: split the head at the D into
prefix/suffix, emit `prefix + D ZDn + suffix`, and set the synthetic tag to
the joined block as today. That converts the `Q`/`G EXIT` tails correctly
because suffix code runs after the block returns. Stage 2 (fidelity):
compile dotted lines as separate IR blocks instead of space-joined text, so
QUIT inside a block pops the block frame rather than the routine, and
level-2+ nesting gets real frames. The stage-1/stage-2 split mirrors how
the corpus is written: 451K of 578K dotted lines are level 1.

**Open design decision:** stage 1 (text merger, days) vs going straight to
IR-level block frames (correct QUIT/$TEST scoping from the start, bigger
change to `compileRoutine`/`MFrontend`). The corpus volume argues for stage
1 first with a per-class ledger re-run between stages.

## Class 2 — SET targets (12.0% + most of the tail)

Two root causes in `MFrontendCmds.compileSet`:

1. **Parenthesized multi-target** `S (XQ,XQ1)=XQXT` / `S (I,X)=0` — target
   scan requires the first char to be a name char, so `(` throws
   "SET target must be a name". Fix: when the target starts with `(`, parse
   the comma-separated target list, evaluate the RHS once, store to each.
2. **Quoted `(` inside subscripts** — `S ZTSAVE("AOULP(")=""` walks the paren
   counter past end of line → "SET requires = after …". Fix: paren scanning
   must skip quoted segments.

## Class 3 — post-conditional (7.2%)

`compile()` special-cases postconditionals for SET and QUIT only. Corpus:
`D:$Y>20`, `W:$E(X,1)="!" !`, `K:cond`, `R:DTIME` etc. Fix: hoist the
`cmd:cond` scan into the dispatch loop (eval cond → `jmp0` over the single
command's IR), replacing the two special cases.

## Class 4 — comma after single-arg commands (7.1%)

Name is a misnomer — not line continuation. `D ^%ZISC,^FOO`,
`G RD:$D(X),PR` and friends: the command compilers consume one argument and
stop at the comma; the dispatch loop then sees `,` in command position. Fix:
loop over comma-separated arguments in the DO/GOTO/KILL/WRITE (etc.)
compilers.

## Class 5 — argument indirection (1.8%)

`@` mid-target (`S X(@Y@)=…`), DO/GOTO with `@target`. `compileSet` handles
only a leading `@name=`; DO/GOTO dispatch doesn't accept `@` targets.

## Class 6 — other tokens (≈2.5%)

- `!` — WRITE argument lists whose argument begins with `!` (format-only
  arg) after a postconditional WRITE.
- `(` — `K (I)` / `N (I,X)` exclusive paren lists (standard M).
- `^` — global as first token after a mis-consumed command (follow-on from
  classes 3/4; expect most to disappear once those land).
- `$` — extrinsic `$$TAG^RTN` in positions the scanner doesn't parse
  (e.g. `D $$FOO` variants) plus `$Z` vendor intrinsics.

## Implementation order

1. **Class 1 stage 1** — DONE (`bc23951`, `9727848`; 143,964 → 2,480).
2. **Class 2** — DONE (`d376761`; 27,743 → 1,180).
3. **Class 3** — universal postconditional at dispatch (16,204).
4. **Class 4** — multi-arg loops for D/G/K/W (17,057).
5. **Class 5/6** — indirection and residuals, incl. the 2,483 dotted
   no-D heads and the 5,553 "SET requires = after" tail shapes.
6. **Class 1 stage 2** — IR-level block frames for QUIT/$TEST fidelity.

Between each step: re-run the ledger (U02 harness, `vista-runs2.sh`), require
the class count to drop with no other class shrinking except by unmasking,
full regression (45/45), GAUNTLET, corpus. Per AGENTS.md policy, the
swallow-net stays; the goal is that these classes stop erroring at all.

## Corpus notes

- Dot lines in this corpus are `␣.` (space, dots, body) — not `.` at column 0.
  Any scanner must treat `[ ]*\.` as dotted.
- The histogram and raw stderr ledger live on U02 (`~/vista-hist.log`,
  `~/vista-native.log`); scripts `/tmp/dotscan.awk`, `/tmp/dotagg.awk`
  (re-creatable from this repo's `tools/` if needed).
