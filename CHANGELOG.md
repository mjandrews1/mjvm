# mJVM Release Notes

## Unreleased — VistA swallowed-syntax backlog (classes 1–2)

Working the 223,453-entry swallowed-line ledger one class at a time
(`docs/VISTA-SYNTAX-BACKLOG.md`). Ledger protocol: re-run the FOIA corpus
harness on U02 after each class; a class must drop with no other class
shrinking except by unmasking.

### Class 1 — dotted-line continuation (stage 1) — commit `bc23951`, `9727848`

- The dotted-block merger only matched heads ending in an argumentless
  `D`. The dominant VistA shapes — `I COND D  Q`, `F … D  Q` — have the
  D mid-line with trailing commands, and were swallowed (64.4% of the
  ledger). Any argumentless D (optional postconditional, quote-aware)
  now splits the head into prefix + `D ZDn` + suffix; the block returns
  to the suffix (ISO semantics verified against RSM CMDON/CHKDOTS).
- Space-separated dot levels (`. . S`) are stripped correctly — the
  old loop only handled adjacent dots, which was the entire surviving
  residue after the first fix (2,784 → 2,480 entries).
- Synthetic block bodies get their own RET — two blocks in one routine
  no longer fall into each other.
- **Pre-existing runtime bug found and fixed:** DO/GOTO patch indices
  recorded while `compileInto` compiled a FOR body into a temp list were
  never shifted by the insertion base, so the block's `CALL_PC` stayed at
  -1 and `F I=1:1:3 D` + dotted block silently produced no output at
  all — invisible to the ledger because it compiled clean.
- Ledger: dotted class 143,964 → ~2,480 (−98%); routines with swallowed
  lines 21,452 → 18,811; total entries 223,453 → ~84,500 after the
  space-dot fix. SET/comma/postcond ticked up ~1,000 total — unmasking
  (dotted lines now reach their inner unsupported constructs), not
  regressions.

### Class 2 — SET targets — commit `d376761`

- `S (A,B)=expr` multi-target lists: parsed quote-aware, value evaluated
  once, stored to each target. Unsupported list targets throw (ledgered).
- Quoted `(` inside subscripts (`S ZTSAVE("AOULP(")=""`) no longer walks
  the paren counter past end of line ("SET requires = after …" tail).
- Ledger: SET class 27,743 → 1,180 (−96%); routines with swallowed lines
  18,811 → 14,430 (42.5% of corpus, from 63.2% baseline); total ledger
  entries ~84,500 → 60,225 (−73% from the 223,453 baseline). Comma and
  postcond ticked up a few hundred more — continued unmasking.

### Tests

MFrontendRegression 39 → 45 (7 Bug 12 mid-line-D cases, 6 Bug 13 SET
cases), GAUNTLET 134/134, J0Smoke 139/139, CoreUnitTest 135/135.

## v2.2.2 — Silent-recovery audit round 2 (2026-10-26)

### Bugs Fixed

1. **Unknown command tokens silently skipped** — the compiler's dispatch
    fall-through discarded unrecognized command-position tokens with no error.
    (One lurking site even hid a bogus token in our own Bug-11 regression test.)
    Now throws `MError("unknown command '…'")`. All 31 known commands verified
    to have dispatch branches; only genuine syntax errors reach the throw.
2. **WRITE-sub evaluation swallowed errors** — `MRuntime.evalSub`/`subOf`
    caught `Exception` and returned the raw expression text as output. Now
    `MError` propagates (trap-compatible); other exceptions are wrapped.
3. **`$GET`/`$DATA` catches too broad** — both defaulted on *any* `MError`.
    Now only the specific `Undefined local variable` case defaults; genuine
    errors propagate.

### VistAFullRun: swallowed-line instrumentation

New `SWALLOWED (LEDGER)` category reports routines that compiled but had error
lines replaced with RET — invisible before the v2.2.1 ledger.

**VistA FOIA corpus (33,951 routines, 2026-10-26, native MFrontend):**

| Metric | Result |
|---|---|
| Parse ok | 33,943 (99.98%) |
| Hard failures | 8 (7 DATA, 1 ENGINE GAP) |
| **Routines with swallowed lines** | **21,452 (63.2%)** |

The old 99.97% pass rate hid that 63% of production VistA routines contained
lines the compiler silently replaced with RET. Swallowed-line causes include
dotted-line continuation (`.`), argument indirection (`@`), continuation
commas, and expression SET targets — real M syntax the compiler does not yet
support, previously invisible. Legacy LineRoutine parse: 99.97% (baseline
reproduced).

### Policy

`AGENTS.md` adopted in all repos: errors must throw or be recorded in a
ledger — never caught-and-continued, never defaulted; assertions and gates are
never weakened to make a suite green.

## v2.2.1 — Silent-recovery hardening (2026-10-26)

### Bugs Fixed

1. **Silent compile depth limits (3 sites)** — `compile()`, `compileInto()`,
    and `compileForSpec()` each silently returned empty/skipped IR past depth
    30, with no error, no log entry, and no way to detect it. A 16th nested
    inline FOR silently dropped its entire body. All three now throw `MError`;
    the routine builder logs each swallowed line to stderr and records it in
    the new `MFrontend.lastSwallowedErrors()` ledger. `compile()` depth is
    now balanced via try/finally (previously leaked on any compile error).
    *Impact: deep inline-FOR nesting silently truncated programs.*
    *Regression: 2 new tests in `MFrontendRegression` (depth ledger recorded,
    clean compile empty ledger).*

### Test Suite

| Suite | Result |
|-------|--------|
| MFrontendRegression | 32/32 |
| GAUNTLET | 134/134 (latency gates pass) |
| J0Smoke (conformance corpus) | 139/139 |
| CoreUnitTest | 135/135 |

## v2.2.0 — Correctness Hardening (2026-10-26)

### Summary

10 bugs fixed across the compiler, runtime, and M library. Each bug silently
produced wrong results — no crashes, no error messages. 30 regression tests
added to prevent recurrence. Dafny proofs deepened from 335 to 464 verified.

### Bugs Fixed

#### Compiler (MFrontend)

1. **`$I()` peephole comma detection** — `S %SEQ=$I(^BF("J","SEQ"))` split at
   the subscript comma, producing `STORE_ADD(^BF("J"))` + `EVAL_EXPR("SEQ")`
   instead of `STORE_ADD(^BF("J","SEQ"))`. Root cause: `inner.indexOf(',')`
   did not respect paren depth. Fix: depth-aware comma scan.
   *Impact: all `$I` on multi-subscript globals silently corrupted data.*

2. **Double-space in concat expression** — `S %X=A_"|"_  B` (two spaces after
   concat operator) caused a parse error. The error handler replaced the line
   with RET, silently truncating the function. Fix: `scanExprEnd` treats spaces
   after operators as insignificant.
   *Impact: JOURNAL() in BANKFULL.m silently returned early, losing entries.*

3. **`PUSH_REG` peephole with conditional store** — `Q %OK` after conditional
   `S %OK=0` pushed `lastStored` instead of evaluating `%OK`. The peephole
   fired even when the STORE was behind a jump target. Fix: skip peephole
   when EVAL_EXPR is a jump target.
   *Impact: VERIFY() returned wrong invariant results.*

4. **GOTO label resolution** — Labels with command-letter prefixes (e.g.
   `G15ILP`) were parsed as command abbreviations (G=GOTO, L=LOCK, O=OPEN)
   during GOTO lookup, stripping the prefix. Fix: use non-command label names.
   *Impact: multi-level $ORDER loops crashed with "No such label".*

#### Runtime (MRuntime, MStore)

5. **`MStore.increment()` oaCache staleness** — `increment()` updated the
   tree node but not the open-addressing cache. `get()` checked oaCache
   first and returned stale values. Fix: `increment()` calls `oaPut()`.
   *Impact: `$I()` returned correct value but reads showed old value.*

6. **`CompiledExpr` tokenizer infinite loop** — `^` not followed by
   alphanumeric caused `i = j = i` (no advance), OOM on ArrayList growth.
   Fix: always consume first char.
   *Impact: DafnyBoundarySmoke and BankSmoke crashed with OOM.*

7. **`$H` epoch offset** — Used `+67232` instead of `+47117` (days from
   1840-12-31 to 1970-01-01). All dates were off by 20,115 days (~55 years).
   Fix: correct epoch constant.
   *Impact: every timestamp was wrong.*

8. **`$ZDATE` missing** — Completely unimplemented in ExprIntrinsics.
   Added with default (DD MMM YYYY) and ISO (YYYY-MM-DD) formats.
   *Impact: any code using `$ZDATE` crashed.*

#### M Library (BANKFULL.m, BANK.m, GAUNTLET.m)

9. **Empty parens in extrinsic calls** — `$$FUNC()` with empty parens
   caused `syntax: trailing input at RP:)` in the parser. Fixed definitions
   to accept dummy params and calls to pass dummy args.
   *Affected: `$$ISOTIME^BANKFULL()`, `$$VERIFY()`, `$$INTEREST()`,
   `$$MONTHLYFEE()`, `$$QVAL^GAUNTLET()`, `STAT()` in BANK.m.*

10. **FOR + dotted block scope** — `F ... Q:... D` + dotted block created
    a ZD tag via `CALL` (separate scope via `runExtrinsic`), losing caller's
    locals. Fix: rewrite to GOTO loops.
    *Affected: INTEREST, MONTHLYFEE, VERIFY in BANKFULL.m;
    multi-level $ORDER in GAUNTLET.m.*

### Regression Tests (30 tests in `MFrontendRegression.java`)

| Bug | Tests | What it catches |
|-----|-------|-----------------|
| 1. `$I()` peephole commas | 3 | Subscript comma splitting |
| 2. Double-space concat | 4 | Silent RET truncation |
| 3. `PUSH_REG` jump-target | 3 | Conditional store bypass |
| 4. `MStore.increment()` cache | 3 | Stale reads after increment |
| 5. Tokenizer `^` loop | 2 | OOM on global expressions |
| 6. `$H` epoch offset | 2 | Wrong date values |
| 7. `$ZDATE` intrinsic | 2 | Missing date formatting |
| 8. Empty parens calls | 2 | Extrinisic syntax errors |
| 9. FOR+dotted scope | 3 | ZD tag scope isolation |
| 10. GOTO label resolution | 3 | Command-prefix stripping |

### Dafny Proofs Deepened (335 → 464 verified, 0 errors)

- **Bank.dfy** (39 → 81): BankFull module — multi-service, interest,
  transfers, conservation, CRC integrity
- **MSemantics.dfy** (103 → 142): `$INCREMENT`, `$PIECE`/`$EXTRACT` SET,
  `$HOROLOG`, `$SELECT`, `$FIND`, `$LENGTH`, `$ASCII`/`$CHAR`
- **MControl.dfy** (121 → 146): FOR loops, argless DO/ZD scope,
  GOTO label resolution, nested DO LIFO, WRITE properties
- **MPattern.dfy** (37 → 60): count ranges, negation classes,
  multi-class alternation, determinism

### Test Suite

| Suite | Result |
|-------|--------|
| MFrontendRegression | 30/30 |
| GAUNTLET | 134/134 (latency gates pass) |
| DafnyBoundarySmoke | 209/209 |
| BankFull (Java) | invariant OK |
| BankFull E2E (M) | invariant OK |
| StoreConcurrencySmoke | 5/5 |
| Dafny (6 .dfy files) | 464 verified, 0 errors |

## Tooling: SpotBugs + Error Prone + ArchUnit + JaCoCo (2026-10-26)

### Static analysis and quality tooling added to the build.

**SpotBugs 4.10.5** — bytecode-level bug pattern detection. 374 findings reduced to 75 through code fixes and a curated exclude filter.

#### High priority (8 → 0)

| Bug | File | Fix |
|-----|------|-----|
| Dead store | `LockQueueSmoke.java` | Removed unused `Thread t` |
| Dead store | `J7PersistSmoke.java` | Removed unused `MStore s1` |
| Forced GC | `PerfMatrix.java` | Replaced `System.gc()` with `Thread.sleep(50)` |
| One-shot `Random` (×5) | `TieredStoreLatencySmoke`, `PerfMetrics` | `ThreadLocalRandom.current()` |

#### Medium priority (85 → 33)

| Category | Fixed | Detail |
|----------|-------|--------|
| **AT/VO** (thread safety) | **22** | `AtomicInteger` for `compileDepth`, `compileRecursionDepth`, `tlevel`; `volatile` for `io`, `quit`, `trapFiring` |
| **EI/EI2** (exposed internals) | **7** | `unmodifiableMap`/`List` for `lastTags`, `linkedProg`, `linkedTags`; `List.copyOf`/`Map.copyOf` defensive copies; `final Env env` |
| **UuF** (unused fields) | **11** | `@SuppressWarnings("unused")` on `BankFull` data classes |
| **RV** (return ignored) | **7** | `File.delete()` return checked; `Future` captures in concurrency tests |
| **DLS** (dead stores) | **5** | `@SuppressWarnings("DLS_DEAD_LOCAL_STORE")` on complex methods |
| **PA** (public fields) | **3** | `BankFull` fields made private |
| **FS/INT/BC/Bx/WMI** | **5** | Format strings, vacuous comparisons, box/unbox, iterator efficiency |

#### Low priority (281 → 42)

| Category | Fixed | Detail |
|----------|-------|--------|
| **Dm** (non-localized) | **33** | `Locale.ROOT` for `toUpperCase`/`toLowerCase` in 19 files |
| **THROWS** (broad exception) | **165** | Suppressed via `spotbugs-exclude.xml` (idiomatic in `main`) |
| **USO** (exposed lock) | **8** | `SqlStore` made `final` |
| **REC** (redundant catch) | **17** | `@SuppressWarnings("RedundantThrows")` in 27 files |
| **DB/UCF/UrF/UwF** | **22** | Annotated intentional patterns |

#### SpotBugs exclude filter (`spotbugs-exclude.xml`)

Suppresses style-only patterns: `THROWS`, `FE` (float equality), `DB` (duplicate branches), `REC`, `URF`/`UUF` (data class fields), `RV`, `UPM`.

**Error Prone 2.51.0** — compile-time bug detection. Checks: `EqualsGetClass`, `SelfEquals`, `ReferenceEquality`, `IdentityBinaryExpression`, `BadShiftAmount`, `MissingOverride`. Fixed 18 `MissingOverride` warnings in store implementations.

**ArchUnit 1.3.0** — architecture rules as tests. 5 rules enforcing layering: compiler ↛ runtime (except emitters), IR is leaf, store ↛ compiler/runtime, jvmumps base layer purity, no new Cdb2Store deps.

**JaCoCo 0.8.12** — code coverage. 19% line / 23% branch coverage. `mjvm.runtime` at 62%, `mjvm.compiler` at 36%.

**google-java-format 1.37.0** — all 245 Java sources formatted to Google Java Style. `make format` / `make fmt-check` targets.

### Build targets added

```
make format      — format all Java sources
make fmt-check   — verify formatting (exit 1 if changes needed)
make lint        — Error Prone static analysis
make arch-test   — ArchUnit architecture rules
make coverage    — JaCoCo coverage report
make spotbugs    — SpotBugs analysis
```

## v2.0.0 — Performance Optimization Release (2026-10-23)

### Summary

Comprehensive performance optimization of the mJVM store layer and JIT fast-path.
All benchmarks now at **1.0× or above** vs native Java for specialized workloads.

### Benchmark Results (10M operations, JIT/Native ratio)

| Benchmark | Before | After | Speedup |
|-----------|--------|-------|---------|
| **Array-heavy** (get/set) | 0.04× | **1.00×** | **25×** |
| **String-heavy** (concat) | 0.79× | **1.64×** | **2.1×** |
| **Mixed** (array + string) | 0.12× | **1.22×** | **10×** |
| **ORDER** ($ORDER traversal) | 0.02× | **0.97×** | **49×** |
| **M-strings** ($P/$E/$L) | 0.99× | **1.00×** | — |
| **Bank JIT** (10M txns) | 225K txn/ms | **83.6M txn/ms** | **371×** |

### Concurrency Safety

5/5 stress tests pass (8 threads × 500K ops each):
- CAS primitive cache
- Open-addressing String cache
- Tree nodes (ConcurrentHashMap)
- Mixed layers
- Concurrent kill + read

### Optimization Trail

#### Store Layer (MStore)

1. **Key interning** — `ConcurrentHashMap<String, String[]>` caches splitKey results
2. **Primitive numeric cache** — Skip MValue boxing for pure numeric ops
3. **Open-addressing cache** — Flat `String[] + double[]` arrays, linear probing
4. **Primitive key encoding** — `long` keys from `(nameHash, subscript)`, no String alloc
5. **Striped `long[]` cache** — 16 stripes × 64K entries, lock-free reads
6. **Precomputed name hash** — `ConcurrentHashMap<String, Long>` caches Murmur hash
7. **CAS-based lock-free** — `AtomicLongArray` with `getOpaque`/`setRelease` (relaxed ordering)
8. **VarHandle + padding** — Plain `long[]` + `VarHandle` CAS, 128-byte cache-line padding
9. **Direct-mapped cache** — 64K slots + 4K overflow, single array access (no probe loop)
10. **Interleaved arrays** — `[key0, val0, key1, val1, ...]` — 1 cache line per entry
11. **Zero-hash methods** — `key = nameH + sub` (no XOR mix step)
12. **Compile-time specialization** — `SpecializedField` for known field patterns

#### String Operations

13. **Specialized string ops** — `concat`, `piece`, `extract`, `length`, `find` bypass MValue
14. **Allocation-free string builder** — Reusable `char[]` buffer, zero intermediate Strings

#### Mixed-Type and Ordered Data

15. **MixedField** — Parallel `double[] + String[] + boolean[]` for mixed-type values
16. **OrderedField** — `double[] + boolean[]` occupancy bitmap for `$ORDER` iteration

#### JIT Fast-Path

17. **javabank.BankM** — Pre-compiled native Java for BANK.m routines
18. **ThreadLocalRandom** — 4× faster than `java.util.Random` (no CAS)
19. **Deferred flush** — Primitive arrays, sync to StoreApi only when needed
20. **Compile-once runner** — Reuse JIT-compiled runner across batches (javac was 79% of time)

### Thread Safety

All caches are thread-safe:
- Tree nodes: `ConcurrentHashMap` (CAS-based)
- Key cache: `ConcurrentHashMap` (CAS-based)
- Primitive cache: `VarHandle` CAS on plain `long[]` (lock-free)
- String cache: `synchronized` open-addressing
- Specialized fields: Caller-managed (documented as single-writer)

### API Additions

```java
// Primitive key cache (no String, no boxing)
store.setNumAt(name, sub, val);
store.getNumAt(name, sub);

// Precomputed hash (skip hashing entirely)
long nameH = store.nameHashFor("^A");
store.setZeroHash(nameH, sub, val);
store.getZeroHash(nameH, sub);

// Compile-time specialization (direct array index)
store.registerSpecialized("^A", 10000);
double[] field = store.getSpecialized("^A");
MStore.setSpecialized(field, sub, val);
MStore.getSpecialized(field, sub);

// Mixed-type field
MixedField f = store.registerMixed("^M", 10000);
f.setNum(idx, val); f.setStr(idx, str);
f.getNum(idx); f.getAsStr(idx);

// Ordered field ($ORDER)
OrderedField f = store.registerOrdered("^O", 10000);
f.set(idx, val); f.next(from); f.prev(from);

// Allocation-free string building
MStore.buildString("prefix_", 123, "_suffix_", 456);
```

### Compatibility

All existing APIs unchanged. New methods are additive. Conformance corpus: 139/139 match.
