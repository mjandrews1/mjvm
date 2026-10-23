# FileMan Tier A — ENGINE GAP register

**Issue:** #32 · **Gate:** *FileMan Tier A without ENGINE GAP* = **1.0** · **Opened:** 2026-10-17

Gaps found while running the Tier A gates on the **native mJVM** path
(`MFrontend` + `IrMachine` + `MRuntime`). Each is classified `ENGINE GAP`
(not dialect, not data) and reproduced by `tools/tier_a_gap_probe.sh`.

Status key: **OPEN** · FIXED · WONTFIX (with reason).

---

## GAP-1 · numeric subscript canonicalisation on SET — **FIXED**

**Class:** ENGINE GAP · **Engine:** native mJVM · **Gate:** A7 (`fm.order-dec`)

M canonicalises a numeric **subscript** when it becomes a node name. Writing
`^DD(0.01)` stores the node as `^DD(.01)`; `$ORDER` then returns `.01`.

```
K ^DD S ^DD(0.01)="b" S K="" S K=$O(^DD(K)) W K,!   →  0.01     want .01
K ^DD S ^DD(1.10)="c" S K="" S K=$O(^DD(K)) W K,!   →  1.10     want 1.1
W 0.01,!                                            →  .01      ✓ (value path is fine)
S ^DD(".01")="b" … $O(^DD(K))                       →  .01      ✓ (explicit string is fine)
```

**Where it matters:** FileMan stores field/index numbers as numeric subscripts
throughout (`^DD(file,field,0)`, `^DD(0.81)`, `^DIC(1,0)`). Any comparison
against a literal (`DINDEX(1,"FILE")'=DIFILEI`, `X'=.01`) or `$ORDER` walk
depends on the canonical form.

**Note:** `LineInterp` (JavaM) already canonicalises correctly — this is a
**parity gap** between the two engines, which is exactly what the corpus
oracle is for.

**Fix:** `MRuntime.subOf` now canonicalises unquoted numeric subscripts via
`ExprEval.mstr` — the same rule `LineRefs.subToken` uses on the `LineInterp`
side. Quoted subscripts (`^DD("0.01")`) keep their literal form.

```java
S ^DD(0.01)="b" … $O(^DD(K)) W K,!   →  .01     ✓
S ^DD(1.10)="c" … $O(^DD(K)) W K,!   →  1.1     ✓
W 0.01,!                             →  .01     ✓
S ^DD(".01")="b" …                   →  .01     ✓ (string preserved)
```

**Repro:** `tools/tier_a_gap_probe.sh` · `FileManTierANativeSmoke` case `fm.order-dec` (now HEALED).

---

## GAP-2 · `$$` extrinsic dispatch after a tagged-routine prelude — **FIXED**

**Class:** ENGINE GAP · **Engine:** native mJVM · **Gate:** A6 (`fm.extrinsic`, `fm.extrinsic-byref`)

`$$tag(...)` dispatch works on a standalone line but was swallowed when the
same code was compiled as part of a program that begins with a tagged routine.

```
W $$P2("z"),!                                       →  zab      ✓
S X=$$P2("z") W X,!                                 →  zab      ✓
 Q
P2(Y)
 S Y=Y_"ab" Q Y
W $$P2("z"),!                                       →  ""       ✗  (handler never called)
```

**Root cause:** `MFrontend.compile` did not treat `;` as a comment. A tag line
like `P2(Y) ; extrinsic` has a comment-only body after formal extraction;
`compile("; extrinsic")` threw `MError("expected command")`, and the catch in
`compileRoutine` emitted `Ir.ret()` — severing fall-through into the next
body line. `runExtrinsic` starting at the tag then hit that `RET` immediately,
returning empty before the real body ran.

**Fix:** `MFrontend.compile` now breaks on `;` (rest-of-line comment), so a
comment-only body produces zero IR. Formal `BIND` ops fall through into the
subsequent body lines as M semantics require.

```java
// before: compile("; extrinsic") → MError → catch → Ir.ret()
// after:  compile("; extrinsic") → []  (comment stripped)
```

**Note:** the one-liner repro concatenating prelude + call into a single
program is *not* valid M — a routine's `Q` ends the top-down run. The real
shape is ZLINK: load the tagged routine, then run the call as a separate
statement against the linked tags (what `FileManTierANativeSmoke.runNative`
does). That shape now works on both engines.

**Repro:** `tools/tier_a_gap_probe.sh` · `FileManTierANativeSmoke` cases `fm.extrinsic`, `fm.extrinsic-byref` (now HEALED).

---

## Tier A scorecard (native mJVM, 2026-10-17)

| gate | state |
|---|---|
| A5 golden parse (8 routines) | **PASS** 8/8 |
| A7 FileMan-shaped RUN (10 cases) | **10/10 PASS** |
| A6 extrinsic (2 cases) | **2/2 PASS** (GAP-2 fixed) |
| A8 c3fst search | **SKIPPED** (c3fst deprecated) |

`LineInterp` (JavaM) is green on the same gates: A5 8/8 · A7 10/10 · A6 2/2.

**1.0 requires A5 + A6 + A7 green on BOTH engines. — ACHIEVED.**

---

## Tier B corpus run — VistA FOIA (2026-10-17)

**Corpus:** U02 `~/vista-foia/VistA-M-master` — 33,951 `.m` routines.
**Tool:** `jvmumps.FileManCorpusWalk [--native]`

| Engine | OK | DATA | DIALECT | ENGINE GAP |
|---|---|---|---|---|
| JavaM (`LineRoutine`) | 33,942 | 7 | 2 | **0** |
| Native mJVM (`MFrontend`) | 33,944 | 7 | 0 | **0** |

**DATA (7):** non-UTF-8 encoding — DVBCQAN2, DVBCQWR2, YTSFTND, RMPR4P23, RMPR9P23, RMPRP23, TIULC.

**DIALECT (2):** no labels — A7R3.m, X.m. Native mJVM compiles these fine (reports 0 DIALECT).

**ENGINE GAPs found and fixed:**
- `ifJmp0`/`endJmps` stale across lines in `compileRoutine` (556 failures)
- DO/GOTO patch offset out-of-bounds after formals BIND shift (173 failures)

Both fixed in `MFrontend.java`. **Tier B: 0 ENGINE GAPs on both engines.**

---

## Repro

```sh
# native mJVM Tier A smoke
FILEMAN_CORPUS=~/_fileman java -cp build:… jvmumps.FileManTierANativeSmoke

# the two gaps in isolation
bash tools/tier_a_gap_probe.sh
```
