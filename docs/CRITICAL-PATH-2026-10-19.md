# Critical Path — 2026-10-19 (updated)

**Scope:** All open issues across JavaM, ydb-mesh, tsx (12 total).
**Principle:** Address highest-impact, lowest-effort first. Engine improvements help everything.

---

## Phase 1: HIGH — Engine store-op reduction ✅ COMPLETE

| Order | Issue | Status | Result |
|-------|-------|--------|--------|
| **1.1** | [#174](https://github.com/mjandrews1/JavaM/issues/174) Peephole: `S ^G=$G(^G)+expr` → 1 op, `$I` → 1 op, redundant return | ✅ **Done** | STORE_ADD, PUSH_REG, MStore.increment |
| **1.2** | [#171](https://github.com/mjandrews1/JavaM/issues/171) BANK.m store-op reduction | ✅ **Done** | DEP 9→5 (44%), WDR 9→5 (44%) |
| **1.3** | [#173](https://github.com/mjandrews1/JavaM/issues/173) Store-op count as primary metric | ✅ **Done** | StoreApi.storeOps() + MStore AtomicLong |

**Achieved:** Store ops/txn dropped from 9 → 5 (DEP/WDR). Smoke 108 OK, corpus 139/0/0.

---

## Phase 2: MEDIUM — Engine execution efficiency

| Order | Issue | What | Impact | Status |
|-------|-------|------|--------|--------|
| **2.1** | [#175](https://github.com/mjandrews1/JavaM/issues/175) Expression token cache | Eliminates re-lexing | ✅ **Done** | ExprEval.tokenCache (PR #181) |
| **2.2** | [#176](https://github.com/mjandrews1/JavaM/issues/176) Extrinsic call optimization | Reduces `$$fn` overhead | ✅ **Done** | Thread-local pools + evalExpr fast path (PR #182) |
| **2.3** | [#177](https://github.com/mjandrews1/JavaM/issues/177) String concat builder | Memory allocation | ✅ **Done** | StringBuilder for `_` operators (PR #183) |
| **2.4** | [#172](https://github.com/mjandrews1/JavaM/issues/172) Cdb2Store pipe protocol batching | Reduces I/O round-trips | ⬜ **NEXT** | — |

---

## Phase 3: LOW — Polish and external

| Order | Issue | What | Status |
|-------|-------|------|--------|
| **3.1** | [#28](https://github.com/mjandrews1/ydb-mesh/issues/28) MCP registry listings | Feature | ⬜ |
| **3.2** | [#29](https://github.com/mjandrews1/tsx/issues/29) FRED endpoint unreachable | Blocked on network | 🚫 |
| **3.3** | [#30](https://github.com/mjandrews1/tsx/issues/30) FluView endpoint unreachable | Blocked on network | 🚫 |

---

## Phase 4: RECURRING — Long-running

| Issue | What | Status |
|-------|------|--------|
| [#34](https://github.com/mjandrews1/JavaM/issues/34) Run full VistA under JavaM and native mJVM | ⬜ |
| [#179](https://github.com/mjandrews1/JavaM/issues/179) Bank.m benchmark runs across all stores and machines | ⬜ |

---

## Success criteria

| Phase | Metric | Target | Achieved |
|-------|--------|--------|----------|
| Phase 1 | Bank store ops/txn | 10.2 → 5.5 (−47%) | **9 → 5 (−44%)** ✅ |
| Phase 2 | Cdb2Store throughput | 0.03 → 0.12 txn/ms (4×) | — |
| Phase 3 | All 4 corners × 3 stores × 3 machines | Baseline table | — |
| Phase 4 | VistA smoke + Bank recurring | Full corpus + regression baseline | — |

---

## Progress (2026-10-20)

- **Phase 1:** Complete ✅
- **Phase 2:** 3/4 done (token cache, extrinsic, concat). **#172 Cdb2Store batching is next.**
- **Phase 3:** #177 moved to Phase 2 (done). MCP registry + blocked endpoints remain.
- **Infrastructure:** U03 syslog collector working (ydb-infra#18 closed).
