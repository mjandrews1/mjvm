# FileMan byref audit — findings

**Date:** 2026-10-17 · **Scope:** `FILEMAN_CORPUS` (2,132 routines, VA FileMan 22.2 + Kernel/RTL/Toolkit, extracted from OSEHRA VistA-M) · **Tool:** `tools/byref_unset.py` + `tools/load_fileman_index.py` · **Issue:** #32

## Verdict

**Zero genuine defects.** All 3,462 pass-by-reference (`.var`) arguments in the FileMan corpus were analysed; every flagged site was hand-verified and is an idiom, not a bug.

## Method

1. **Index** (`tools/load_fileman_index.py`) — SQLite + FTS5 over the corpus: `routine` / `label` / `call` / `call_arg` / `intrinsic` / `gap`. `call_arg` records every DO/`$$` argument with its byref flag and actual name.
2. **Unset-at-call scan** (`tools/byref_unset.py`) — SET/NEW/KILL event model per name, ordered by `(line, col)`. Caller-side: direct assignments, enclosing-tag formals (bound at entry), and nested-`DO` write tracing. Callee-side: `read_before_write` with body-wide `$D`/`$G` guards.
3. **Bucketing** — a byref call that is unset at the call is classified by *why* it is safe.
4. **Hand verification** — every flagged row read back against the source with its read site and write site.

## Funnel

| stage | count |
|---|---:|
| byref args (`call_arg.byref=1`) | 3,462 |
| set before call | 2,607 |
| **unset at call** | **855** |
| → callee safe | 775 |
| → flagged | 80 |

Note: an earlier pass over-counted byref at 3,588 — the difference is comment-stripping and string-masking in the index (see *Scanner defects*).

## The 80 flagged — every one verified

| bucket | count | meaning |
|---|---:|---|
| shared-state **written** | 18 | callee reads then mutates shared driver state |
| shared-state **read-only** | 15 | pure consumers of shared driver state |
| conclusive (writes) | 14 | in/out byref — read then write, **the write is the purpose** |
| array-in **read-only** | 14 | `.X` hands over `X`, `X(1)`, `X(2)`… for reading |
| array-in **written** | 9 | in/out array |
| resolved (nested DO) | 7 | written by a nested `DO` before the read |
| known-output | 3 | `D ^DIC` writes `Y` (documented contract) |
| **genuine defects** | **0** | |

### Shared state (33 rows)
Names initialised outside the callee's call tree — the DIC*/DDS lookup driver and the DDW screen editor. Allowlisted in `SHARED_STATE` in `tools/byref_unset.py`:

| name | role | ro/rw |
|---|---|---|
| `DDWOFS` | DDW window offset (`DDW.m:65`) | 0 / 14 |
| `DINDEX` | lookup-index descriptor (`INDEX^DICUIX`/`DICL`/`DICF`) | 6 / 1 |
| `DA` | record number / IEN stack | 1 / 0 |
| `DIC` | file spec | 4 / 1 |
| `DIFILE` | resolved file number | 2 / 1 |
| `DIFILEI` | file number | 1 / 0 |

`DDWOFS` is mutable scroll state (`SHIFT` advances it). `DINDEX` is a descriptor published to readers (`NXTINDX^DICF2` is the sole mutator).

### Array-in (23 rows)
The M array-passing idiom: `.DA` hands over the whole IEN stack. **Byref ≠ output promise.** Split by whether the callee assigns.

### In/out byref (14 rows)
Read-then-write where the write is the API contract. Four of six distinct callees document it:
- `BK(DDSBK,DDP)` — *"DDSBK = Block number or [Block name] (by ref)"* / *"Returns: DDSBK = Block number"*
- `VALID^XPARDD(PAR,VAL,FLD,ERR)` — *"[.]VAL: value (external form), internal form returned"*
- `ENTLST(ENT,PAR,INST)` — *".ENT: multiple entity pieces or keyword 'ALL'"*
- `DDA(Y,DA,DL)` — *"Push Y onto DA array"*

Others: `GETFORM` (specifier normaliser + counter), `DF` (path normaliser), `FL` (filename validator).

## Scanner defects found and fixed by the hand-verification

The hand-check found **no code bugs**, but it did find **six false-positive generators** in the analyzer. Each is fixed and permanent on `main` (PRs #146–#151).

| # | defect | effect | fix |
|---|---|---|---|
| 1 | `parse_routine` scanned raw lines | 1,587 / 41,487 (3.8%) indexed calls sat on **comment-only** lines | `strip_comment()` (quote-aware) |
| 2 | code-generators emit M source in strings | `D L(" Q $$UNIQUE^DIE17(.X,.DA)")` indexed as a live call | `mask_strings()` (offset-preserving) |
| 3 | DO target names read as variables | `D DA(RIENS,.DA)` — first `DA` is the tag | `do_target_cols()` |
| 4 | piece-assign write mislocated | `S $P(DIDS,";",DICOUNT)=DIDENT` writes `DIDS`, but `name_col` returned the `$P` column | `im.start(1)` |
| 5 | `RE_MERGE` one match per line | `M I=DIC N DIC M DIC=I K I` — second `M` invisible | match lhs before `=` |
| 6 | nested-paren SET lvalue | `S XULMVAR($P(NODE,"^",2))=…` and `(Y,J(0))=S` missed by `[^)]*` | `set_targets()` balanced-paren walk |

Plus five more the array-in pass exposed:

| # | defect | fix |
|---|---|---|
| 7 | `R`/`READ` not a write | `RE_READ` → SET (cleared all 8 `READNXT` false positives) |
| 8 | multi-letter command words not skipped | `is_cmd_name` gate `len==1` → all `CMD_WORDS`; bare-EOL at a command slot is a label (`DO ; comment`) |
| 9 | `RE_WORD` no word boundary | `$P(`'s `P` matched as variable `P` — `(?<![A-Za-z0-9%$])` |
| 10 | byref forward counted as write | `.HL` → read-only `UNESC` flipped `UNESCWP` to rw; now `BYREF` kind + `byref_assigns()` resolves the callee |
| 11 | caller-side `BYREF` ≠ `SET` | a prior `D FOO(.X)` looked like unset |

Also: `NEW`/`KILL` are `UNDEF`, not `WRITE` (`N X` then `W X` is an UNDEF). Caller-tag formals are bound at entry. Body-wide `$D`/`$G` guards shield later lines (`Q:'$D(DA(1))`). Known FileMan outputs recorded (`D ^DIC` writes `Y`, `D ^DIR` writes `DIR`).

## Key lessons

- **`AUXVAL` was never a bug.** `FLDVAL` sets `DIEV0`/`DIEVP2` at `DIEV.m:32` before `AUXVAL` reads them. The caller-side scan could not see through `D FLDVAL`. **Do not patch correct corpus source to silence a false positive** — the analyzer was the defect. (Same failure mode as weakening a test expectation.)
- **The shared-state bucket must run *after* `read_before_write`.** It short-circuited first and held every unset call involving an allowlisted name, including rows where the callee never reads the formal (80 → 31).
- **Hand-verification is what caught all of it.** The funnel went 134 "conclusive" → 0 genuine over ~12 rounds; every reduction was a false-positive class, never a loosened standard.

## Artifacts

| file | purpose |
|---|---|
| `tools/load_fileman_index.py` | corpus index (SQLite + FTS5) |
| `tools/byref_unset.py` | unset-at-call + read-before-write analyzer |
| `docs/FILEMAN-INDEX.md` | index schema |
| `~/Downloads/fileman-byref-read-before-write.tsv` | 625-row review dump (local) |
| `~/Downloads/fileman-shared-state-read-sites.txt` | 80-row read-site listing (local) |

PRs: #132 (Tier A smoke) · #133–#134 (index) · #135–#151 (analyzer + fixes).

## What remains for #32

This audit is a **static source audit**. #32's charter is broader: **run** FileMan / VistA-scale M on JavaM and native mJVM and classify failures as ENGINE GAP vs dialect vs data, with the 1.0 gate being *FileMan Tier A without ENGINE GAP*. Remaining:

- [ ] `FileManTierASmoke` is green (A5/A6/A7) — done, but on `LineInterp` only
- [ ] Same Tier A gates on **native mJVM** (`MFrontend` + `IrMachine`)
- [ ] Tier B: run the wider corpus (or U02's 33,951-routine VistA FOIA) and classify
- [ ] No ENGINE GAP at Tier A
