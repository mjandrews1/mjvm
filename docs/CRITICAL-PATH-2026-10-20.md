# Critical Path — 2026-10-20 (post cross-language analysis)

**Scope:** JavaM/mJVM performance and architecture, driven by the Java/Kotlin/ABCL baseline comparison.
**Principle:** mJVM bytecode compilation is the path to performance. JavaM is the semantic oracle.

---

## Phase 1: mJVM correctness (gate for everything)

mJVM must pass the same tests as JavaM before we optimize.

| Order | Issue | What | Status |
|-------|-------|------|--------|
| **1.1** | [#186](https://github.com/mjandrews1/JavaM/issues/186) Fix MFrontend recursion OOM | Blocks VistA native runs | ⬜ |
| **1.2** | — mJVM passes smoke suite (108 tests) | Correctness gate | ⬜ |
| **1.3** | — mJVM passes corpus 139/0/0 | Semantic parity | ⬜ |
| **1.4** | [#193](https://github.com/mjandrews1/JavaM/issues/193) Fix Cdb2BankStore SETB rows | Test tooling | ⬜ |

---

## Phase 2: Bytecode emission (unlock C2 JIT)

The cross-language data is clear: bytecode compilation + JIT closes the gap. IR interpretation cannot.

| Order | Issue | What | Impact | Status |
|-------|-------|------|--------|--------|
| **2.1** | [#196](https://github.com/mjandrews1/JavaM/issues/196) Bytecode caching | Load once, JIT thereafter | Large | ⬜ |
| **2.2** | — `BytecodeEmitter` produces loadable `.class` | C2 inlining | Large | ⬜ |
| **2.3** | — Bank benchmark: mJVM bytecode vs JavaM vs Java | Measure gap closure | — | ⬜ |

**Target:** Close 50%+ of the mem gap (JavaM 532 → 800+ txn/ms at C4/10K).

---

## Phase 3: Allocation elimination (close the rest)

From the foundational analysis (#194-#195): boxing and path allocation dominate the remaining overhead.

| Order | Issue | What | Impact | Status |
|-------|-------|------|--------|--------|
| **3.1** | [#194](https://github.com/mjandrews1/JavaM/issues/194) Primitive value stack | Eliminate MValue boxing | 30-50% of mem gap | ⬜ |
| **3.2** | [#195](https://github.com/mjandrews1/JavaM/issues/195) Interned string keys | Eliminate List<String> per store call | 12+ allocs/txn | ⬜ |
| **3.3** | [#191](https://github.com/mjandrews1/JavaM/issues/191) Atomic increment | 1 round-trip per LOG | Good win | ⬜ |
| **3.4** | [#190](https://github.com/mjandrews1/JavaM/issues/190) Native numeric values | Skip string format/parse | Good win | ⬜ |

---

## Phase 4: Engine consolidation

| Order | Issue | What | Status |
|-------|-------|------|--------|
| **4.1** | [#198](https://github.com/mjandrews1/JavaM/issues/198) Split StoreApi (core + extensions) | Specialization | ⬜ |
| **4.2** | [#197](https://github.com/mjandrews1/JavaM/issues/197) Unify on MFrontend, deprecate LineRoutine | One compiler | ⬜ |
| **4.3** | [#189](https://github.com/mjandrews1/JavaM/issues/189) FOR loop counted-loop bytecode | 2-5x on hot loops | ⬜ |
| **4.4** | [#192](https://github.com/mjandrews1/JavaM/issues/192) Eliminate reflective dispatch | Direct method calls | ⬜ |

---

## Phase 5: Production cutover

| Task | Status |
|------|--------|
| mJVM becomes default engine for `mjvm` CLI | ⬜ |
| JavaM frozen (bug fixes only) | ⬜ |
| Single test suite (no `--native` flag) | ⬜ |
| VistA full run on mJVM bytecode path (#34) | ⬜ |
| Bank recurring benchmark (#179) on mJVM | ⬜ |

---

## Success criteria

| Metric | JavaM (baseline) | mJVM bytecode (target) | Measured |
|--------|-----------------|----------------------|----------|
| Bank mem C4/10K | 532 txn/ms | **800+** | — |
| Bank mem C1/10K | 140 txn/ms | **300+** | — |
| Bank sql C4/10K | 0.3 txn/ms | **2+** | — |
| Corpus | 139/0/0 | 139/0/0 | — |
| Smoke | 108 OK | 108 OK | — |
| VistA parse | 99.97% | 99.97% | — |
| MValue allocs/txn | ~20 | **0** | — |
| Store key allocs/txn | ~12 | **0** | — |

---

## Cross-language reference data (10K, Mac, mem)

| Corner | Kotlin | Java | JavaM | ABCL |
|--------|--------|------|-------|------|
| 1a/1i | 545 | 471 | 140 | 25 |
| 10a/1i | 1036 | 865 | 234 | 25 |
| 1a/2i | 574 | 505 | 377 | 53 |
| 10a/2i | **1202** | 607 | 532 | 52 |

**The gap:** JavaM is 2.3x slower than Kotlin at C4. The target is 800+ (close 60% of gap).

---

## Progress tracking

- **Phase 1:** Not started (blocked on #186)
- **Phase 2:** Not started
- **Phase 3:** Issues filed (#189-#195)
- **Phase 4:** Issues filed (#189, #192, #197, #198)
- **Phase 5:** Waiting on Phases 1-4

**Last updated:** 2026-10-20
