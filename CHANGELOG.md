# Changelog — JavaM (JvMumps) / mJVM

All notable changes to this repo. Format follows [Keep a Changelog](https://keepachangelog.com/).
Versioning follows `m-semantics/docs/VERSIONING.md` (**MAJOR** if corpus `expected` changes, public Dafny lemma renames, or `^` schema / `rel` renames).

Internal checkout dir may still be `~/jvmumps`; **product / GitHub name is JavaM** (`mjandrews1/JavaM`).

---

## [1.2.0](https://github.com/mjandrews1/JavaM/releases/tag/v1.2.0) — 2026-10-23

**GitHub release:** https://github.com/mjandrews1/JavaM/releases/tag/v1.2.0

### Milestone: Production Cutover Complete

**JavaM frozen as semantic reference oracle. mJVM is the production engine.**

### Added
- **TieredStore** — LRU cache + H2Store + WAL crash recovery. Single-writer + read-only readers (snapshot-based). Binary WAL with CRC32 checksums, batched fsync (100ms). (#230)
- **BytecodeEmitter: all 46 IR ops** — complete bytecode emission path. 24× JIT speedup vs IR interpreter. (#231, #232)
- **mJVM CLI JIT** — `--useClass` uses JavaSourceEmitter for 24× speedup. (#233)
- **BankScaleBench** — 10K accounts × 1-10 instances, 0-250K accounts. (#228)
- **TieredStoreLatencySmoke** — CI regression guard (TieredStore p99 ≤ H2Store p99). (#225)
- **WalLockSmoke** — multi-process WAL locking test. (#229)
- **TieredStoreRwSmoke** — single-writer + read-only reader test. (#230)

### Changed
- **JavaM frozen** — `StoreApi` FROZEN notice. New features go to mJVM. (#234)
- **`--native` flag removed** — MFrontend is the unified compiler. (#234)
- **BytecodeEmitter** — all 46 IR ops covered (was 19). (#232)

### Fixed
- **Multi-process WAL locking** — flock prevents concurrent corruption. (#229)
- **TieredStore CME** — ConcurrentHashMap + ConcurrentLinkedDeque for thread safety. (#224)
- **WAL flush policy** — timer-based (100ms) instead of buffer-size. (#225)

### Performance (Mac, MStore, 10K txn)
| Method | Time | Speedup |
|--------|------|---------|
| IR interpreter | 21.7 ms | 1× |
| **JavaSource JIT** | **0.9 ms** | **24×** |

| Store | Peak TPS (U02) |
|-------|---------------|
| MStore | 32,400 |
| H2Store | 19,000 |
| MapDBStore | 3,700 |
| TieredStore | — (persistence) |

### Verification
- **VistA full run:** 99.97% (33,942/33,951) — 0 ENGINE GAP
- **Bank benchmark:** 707 txn/ms peak (10 acct, 4 inst)
- **Dafny:** 403 lemmas, 0 errors, 0 TODOs
- **Smoke:** 106 OK

### Closed issues
#34, #179, #187, #199 (all engineering issues)

---

## [1.1.0](https://github.com/mjandrews1/JavaM/releases/tag/v1.1.0) — 2026-10-22

**GitHub release:** https://github.com/mjandrews1/JavaM/releases/tag/v1.1.0

### Added
- **H2Store** — H2 MVStore backend (pure Java embedded B-tree). 288x faster than SqlStore. Peak 19,000 TPS on U02. (#207)
- **MapDBStore** — MapDB BTreeMap backend (pure Java disk B-tree). 47x faster than SqlStore. Peak 3,700 TPS on U02. (#208)
- **MFormatter** — M code expander tool with `--break`, `--organize`, `--xref` (IBM PL/I style attribute cross-reference), `--db` (searchable SQLite database). (#214)
- **StoreCore capability split** — `StoreCore` + `StoreOrder` + `StoreLock` + `StoreTx`. Production engines: MStore + H2Store + MapDBStore. SqlStore CI-only. Cdb2Store deprecated. (#201)
- **String key fast path** — `StoreCore.get/set/kill/increment(String key)` avoids `List<String>` allocation. (#195)
- **Native numeric fast paths** — `StoreCore.getNum/setNum` skip string format/parse. (#190)
- **Atomic increment** — `increment()` override in H2Store, MapDBStore, SqlStore. (#191)

### Changed
- **MFrontend is the sole compiler** — `LineRoutine`/`LineInterp` deprecated as execution paths. (#197)
- **Cdb2Store deprecated** — Comdb2/tsx/MeshGraph/YottaDB infrastructure frozen. (#201)
- **BankConcurrencyBench** — 12 corners (1/10/100 accounts × 1-4 instances). BANK.m BENCH2 accepts account count.
- **Makefile** — excluded SQLite/Comdb2 backend smoke tests. Smoke 102 OK.

### Fixed
- **MFrontend recursion depth guards** — prevents stack overflow on VistA corpus. VistA native: 99.97% (15K), 99.95% (33K). (#186)
- **Parser `out.set()` bounds checks** — fixed 3 IndexOutOfBoundsException crashes on VistA routines. (#212)
- **Cdb2BankStore SETB collation** — `sk` field now properly collated. (#193)
- **IR cache LRU eviction** — 500 entries, prevents OOM at 15K+ routines. (#196)

### Infrastructure
- **U03 syslog collector** (ydb-infra#18) — rsyslog on U03 receiving from U01/U02. Root cause: rsyslog drops to `syslog` user after startup, `/var/log/remote/` was `root:root`. Fixed by `chown syslog:adm`.
- **All infrastructure frozen** — Comdb2, tsx, MeshGraph, YottaDB stopped/disabled/masked on U01/U02/U03. Flaming Yack Limited Company placeholder page live.

### Performance (U02, txn/ms at N=10000)
| Store | 1 inst | 2 inst | 4 inst |
|-------|--------|--------|--------|
| MStore | 5.5 | 32.4 | 14.3 |
| H2Store | 3.8 | 10.9 | 15.5 |
| MapDBStore | 1.5 | 2.6 | 2.8 |

Peak TPS: MStore 32,400 · H2Store 19,000 · MapDBStore 3,700.

---

## [0.9.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.9.0) — 2026-10-18

**GitHub release:** https://github.com/mjandrews1/JavaM/releases/tag/v0.9.0

### Added
- **MeshGraph DS libraries** — `src/main/m/lib/`: HMAP (hash map), HSTACK (LIFO), HQUEUE (FIFO), HHEAP (min-heap), HPQUE (priority queue). Portable M, ISO-M only.
- **REPL** — `jvmumps.Repl` (JavaM) and `mjvm.cli.Repl` (mJVM). Interactive M shell with `/help` `/load` `/tags` `/reset` `/history` `/quit`. `REPL_INIT` env.
- **`MControl.dfy`** — DO/GOTO/trap/WRITE control-flow contracts (m-semantics). 12 postconditional DO lemmas.
- **Tier B corpus walk** — `FileManCorpusWalk --native` for 33,951 FOIA routines.
- **Tier C comparison** — `TierCCompare` dual-engine runtime diff.

### Fixed
- **GAP-1** subscript canonicalisation (`MRuntime.subOf` uses `ExprEval.mstr`).
- **GAP-2** `$$` extrinsic after tagged prelude (`;` comment stripping in `MFrontend.compile`).
- **`::` postconditional** syntax in `MFrontend` M3 DO path.
- **`TAG^ROUTINE` syntax** — strip `^routine` in DO and `$$` handlers.
- **DO args** — pop from stack and pass to callee (was passing empty).
- **`pathOf` variable subscripts** — evaluate variables, not just literals.
- **Argumentless `D`** — no-op (dotted block continues inline).
- **`compileDoGotoRet`** — emit `CALL` for external targets (was silent drop).
- **`ifJmp0`/`endJmps`** per-line clear in `compileRoutine`.
- **DO/GOTO patch** bounds check after formals BIND shift.
- **WRITE variable collision** — `W X` writes variable X, not WRITE then XECUTE.
- **`fireTrap()` returns boolean** — `IrMachine` rethrows when no trap set.
- **Error message alignment** — `No such label 'X'` (was `Extrinsic $$X not available`).

### Contract
- corpus: **135 / 4 / 0** (was 131/8/0). 4 placeholders filled, 4 RSM DIVERGENCES documented.
- Dafny: 261 verified, 0 errors (MSemantics 103 + MPattern 37 + MControl 121).

### Known gaps
- 4 corpus misses are RSM DIVERGENCES (`T11d`, `T15`, `C16`, `C24`) — documented with `#:DIVERGE`.
- Byref args (`.var`) in `$$` extrinsic not fully handled — `DsLibSmoke` avoids them.
- VistA smoke execute: 130/400 OK on sample, 126 missing host services (Kernel globals, FileMan).
- Perf baseline not yet recorded for v0.9.0.

---

## [0.4.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.4.0) — 2026-10-15

**GitHub release:** https://github.com/mjandrews1/JavaM/releases/tag/v0.4.0

### Added
- **`Cdb2Store`** — Comdb2 `StoreApi` via persistent `tools/cdb2pipe` (cdb2api). Grouped `SETB` multi-row inserts (BATCH=300).
- **`Stores` factory** + `mjvm --store cdb2|sqlite|memory` (mesh/SQL plane).
- `Cdb2MeshBench` / `Cdb2OrderBench` / `Cdb2OrderParitySmoke` / `MultiLevelOrderSmoke`.

### Fixed
- **Multi-level `$ORDER`** now skips the cursor subtree (`SqlStore` + `Cdb2Store`). Forward walk no longer re-returns the same subscript forever (ydb-mesh `related()` hang).
- Cdb2 smokes skip cleanly when no `cdb2pipe` (CI `make smoke` stays green).

### Contract
- corpus: **131 / 8 / 0** unchanged (no `expected` edits).
- Dafny: unchanged.

### Known gaps
- Cdb2 unbatched durable `$SET` ~120/s; use write-buffer / `flush()`.
- No FileMan Tier A gate yet (not 1.0).

## [0.3.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.3.0) — 2026-10-13

**GitHub release:** https://github.com/mjandrews1/JavaM/releases/tag/v0.3.0

### Added
- **#64** CI: `make smoke` runs **all** `*Smoke` classes (92) + `make itest` (SqlStore) + PR **Red / green** field (`Makefile`, `ci.yml`, PR template).
- **#70** `CorpusCase.corpusPath` — `args[0]` > `$CORPUS` > `tests/conformance.json`; no `/Users/mark` defaults in test/score mains.
- **#61** `CHANGELOG.md` (this file).
- **#62** type/method Javadoc on public API + `make javadoc` (exit 0).

### Fixed
- **#64/#69** `ClassEmitter`: illegal Java `goto` / unreachable labels → **pc-switch dispatch**; full IR.
- **#64/#70** CI `NoSuchFileException` on hardcoded Mac corpus path.
- `MjvmM7Smoke` expected stale `V0.1.0`; `Mjvm.VERSION` is **`V0.3.0 (mJVM)`**.

### Docs
- RSM file-backed production oracle note on `MStore` / `SqlStore`.

---

## [0.2.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.2.0) — 2026-10-11 (path / quality)

### Added
- OSV dependency advisory scan in CI for vendored jars (`#65`).
- `docs/DEPS.md` — pinned `lib/*.jar` versions + SHA-256.
- `docs/HEURISTICS.md` — 13 ranked engineering heuristics (was 19 unranked).
- `tools/ci-block-probe.sh` + `docs/RELEASE-CHECKLIST.md`.

### Changed
- PR template: DEPS.md rule, benchmark / rollback sections.

---

## [0.1.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.1.0) — 2026-10-09/10 (peer target **131/8/0**)

Corpus gate **`MATCH=131 MISS=8 ERR=0`** (miss set = RSM upstream eight).

### Added
- **J12–J17** JvMumps catch-up: symbols/literals · `@` indirection · DO/GOTO/XECUTE · traps + `$Z*` · WRITE `*n`/`?n`/`#` + `$X$Y` · `$NEXT`/`$ST`/`$TEXT` · ISO `=` numeric equality (C24).
- **M10–M12** mJVM: IF/ELSE/FOR + NEW/KILL/MERGE · ExprEval + traps · parity to LineInterp.
- **Native mJVM** (`MjvmNativeScore`) at **131/8/0** (`fc143c8`).
- PatternMatch: ISO groups + alternation `n.(alt,…)`.
- FmJv smoke; direct-mode error text counted as corpus match where ISO-correct.

### Fixed
- **#6** SET multi-arg hang (`S A=1,B=2`) — T14 `$ORDER` corpus (`4cd819e`).
- Pattern counts: unbounded `n.` / `.n` and empty-literal `0` width (`72f0a2d`).

---

## [0.0.2](https://github.com/mjandrews1/JavaM/releases/tag/v0.0.2) — 2026-10 (mJVM **M0–M9** JVM-native runtime)

### Added
- M0–M2 StoreApi + M frontend + IrMachine + ClassEmitter + CLI.
- M3 invokedynamic CALL/GOTO + M4 store oracle.
- M5 devices · M6 JOB/locks/TX · M7 `mjvm` CLI drop-in for `rsm` (`RSM_DBFILE`, `bin/mjvm`).
- M8 bench harness (`docs/mjvm-perf.md`).
- M9 packaging: `install.sh`, `man/mjvm.1`, `jvmumps.7`, `--release 21` (runs on Java 25 hosts).

---

## [0.0.1](https://github.com/mjandrews1/JavaM/releases/tag/v0.0.1) — 2026-10 early (**Charter** / suite)

### Added
- Charter: JvMumps + mJVM = **complete M** (RSM-equivalent scope; capability parity, not file-level port).
- W1–W23 / J1–W50 smoke suite + StoreApi / SqlStore / JOB / LOCK.
- Portable Makefile JDK discovery (PATH javac, Homebrew fallback).
- CI: GitHub Actions smoke + corpus + **native 131/8/0** gate; vendored `tests/conformance.json` (private `pymumps` not required on runners).

---

## Template for new entries

```markdown
## [1.0.0](https://github.com/mjandrews1/JavaM/releases/tag/v1.0.0) — 2026-10-18

**MAJOR** — corpus `expected` values changed (VERSIONING.md rule). 1.0.0 gates met: corpus **139/0/0**, smoke 106 OK, FileMan Tier A no ENGINE GAP.

### Added
- **PEMDAS M evaluator** — `src/main/m/lib/PEMDAS.m`, pure-M recursive descent arithmetic with full PEMDAS precedence (unary > `**` right-assoc > `*` `/` left-assoc > `+` `-` left-assoc). 17/17 cases green.

### Fixed
- **Parser: QUIT postcond** — `Q:COND` now falls through to the next command when the guard is false (was `break`, dropping the rest of the line).
- **Parser: argless FOR `Q:cond` guard** — `compileArglessFor` peels a leading `Q:cond` as the loop exit guard instead of treating it as body code.
- **Parser: dotted-block merger** — matches `F … D` tails (not just standalone `D`); strips nested dots correctly.
- **Parser: `isCmdStart` word boundary** — `%AL` no longer matches `L` (LOCK) at the tail of a variable name.
- **Conformance: 4 RSM divergences resolved** — T11d/C16/C24 expected values updated to ISO 11756 §8.3 (numeric compare for numeric strings, string collation otherwise) matching post-rsm#11 RSM. T15 `$TLEVEL` is 0 outside a transaction per ISO §6.3.20. Corpus now **139/0/0**.

### Contract
- corpus: **139 / 0 / 0** (was 135/4/0). 4 RSM DIVERGENCES resolved to ISO 11756.
- mJVM version string: `V1.0.0`.

## [1.0.1](https://github.com/mjandrews1/JavaM/releases/tag/v1.0.1) — 2026-10-18

**PATCH** — docs, CI, and repo hygiene only (VERSIONING.md rule). No engine or contract changes.

### Docs
- **README** — link to v1.0.0 release; add v1.0.0 status summary (PEMDAS 17/17, corpus 139/0/0, smoke 106 OK).

### Repo
- **.gitignore** — add `docs/javadoc/` and `tools/__pycache__/` to suppress untracked generated files.
- **CI** — native score gate updated to `MATCH=139` (was `MATCH=135`).
- **mJVM version string** — `V1.0.1`.

## [Unreleased]

### Added
- **Bank simulator** — `src/main/m/lib/BANK.m`: double-entry bank with overdraft protection ($3 advance fee, −100 floor, immutable journal). 61 boundary tests.
- **Dafny boundary smoke** — `DafnyBoundarySmoke.java`: 209 test cases covering MSemantics/MPattern/MControl/PEMDAS lemmas. Dafny lemma coverage 91% (139/153).
- **Postconditional SET** — `S:cond var=val` support in MFrontend (assign only if cond).

### Fixed
- **`compileInto` JMP/JMP0 offset** — local jump targets were not offset when copied into parent list, causing self-loops in FOR bodies with postcond SET or IF.

### Docs
- **COVERAGE-MATRIX.md** — Dafny lemma coverage table (153 lemmas, 139 tested, 91%).
```

Every user-visible PR should append here (PR template checkbox).

[1.0.1]: https://github.com/mjandrews1/JavaM/releases/tag/v1.0.1
[1.0.0]: https://github.com/mjandrews1/JavaM/releases/tag/v1.0.0
[0.9.0]: https://github.com/mjandrews1/JavaM/releases/tag/v0.9.0
[0.4.0]: https://github.com/mjandrews1/JavaM/releases/tag/v0.4.0
[0.3.0]: https://github.com/mjandrews1/JavaM/releases/tag/v0.3.0
[0.2.0]: https://github.com/mjandrews1/JavaM/releases/tag/v0.2.0
[0.1.0]: https://github.com/mjandrews1/JavaM/releases/tag/v0.1.0
[0.0.2]: https://github.com/mjandrews1/JavaM/releases/tag/v0.0.2
[0.0.1]: https://github.com/mjandrews1/JavaM/releases/tag/v0.0.1
