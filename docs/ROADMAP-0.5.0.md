# Roadmap — JavaM / mJVM v0.5.0

**Milestone:** [v0.5.0](https://github.com/mjandrews1/JavaM/milestone/2) · **Previous:** [v0.4.0](https://github.com/mjandrews1/JavaM/releases/tag/v0.4.0)  
**Scope:** next minor after the Cdb2 mesh store ship (2026-10-15).  
**Contract:** corpus **131 / 8 / 0** unless an ISO/RSM-cited `expected` change is approved (then MAJOR).

---

## Theme

**Measure honestly, then decide.** v0.4.0 landed the Comdb2 `StoreApi` and multi-level `$ORDER` fix with a clean fleet smoke matrix. v0.5.0 is the **performance review cycle** — one re-runnable baseline across JavaM, mJVM, and the RSM oracle — not a rewrite or micro-optimization campaign.

---

## Work items (milestone)

| # | Title | Deliverable | Labels |
|---|-------|-------------|--------|
| [#97](https://github.com/mjandrews1/JavaM/issues/97) | Holistic performance review | Parent: numbers that can be re-run and compared | `perf` |
| [#98](https://github.com/mjandrews1/JavaM/issues/98) | Metrics matrix + fixed-host baseline | Matrix + U01/U02 baseline capture | `perf` |
| [#99](https://github.com/mjandrews1/JavaM/issues/99) | Cross-stack compare | JavaM vs mJVM vs RSM on the same matrix | `perf` |
| [#100](https://github.com/mjandrews1/JavaM/issues/100) | Store + lock latency | SQLite now; Comdb2 (grouped `Cdb2Store`) when ready | `perf` |
| [#101](https://github.com/mjandrews1/JavaM/issues/101) | Footprint | Compile time, startup, RSS / GC | `perf` |
| [#102](https://github.com/mjandrews1/JavaM/issues/102) | Publish `docs/BENCH.md` | Bench tables + one-page findings | `perf` `documentation` |

**Definition of done for the milestone**

- [ ] #98 metrics matrix + baseline committed (`docs/BENCH.md` or sibling)
- [ ] #99 cross-stack table filled on a fixed host (U01 preferred)
- [ ] #100 store/lock latency includes SQLite **and** Cdb2 row
- [ ] #101 footprint numbers recorded
- [ ] #102 `docs/BENCH.md` + findings writeup published
- [ ] Fleet smoke still green (Mac / U01 / U02 / LXC) at the release tag

---

## Out of scope (v0.5.0)

- FileMan Tier A without ENGINE GAP (**that is 1.0**, not this minor)
- Corpus `expected` edits without ISO/RSM citation
- Optimize-before-measure rewrites (follow the review in a later minor)
- CI as a perf lab — CI stays `smoke` / corpus gate; prefer **U01/U02 fixed hosts**

---

## Starting point (from v0.4.0)

| Surface | Baseline |
|---------|----------|
| Corpus | 131 / 8 / 0 (`MjvmNativeScore`) |
| Fleet smoke | 99 / 99 on Mac, U01, U02, `u01-javam-ci`, `u01-mjvm-bench` |
| `$ORDER` walk 20k (U01) | SQLite 64.7 s · **Cdb2 10.1 s** |
| Insert 20k (U01) | SQLite 2.4 s · Cdb2 13.4 s (grouped `SETB`) |
| Mesh `related()` 50 hubs | Cdb2 **136 ms** |
| `SqlLockStressSmoke` | 50×5 fast hosts · adaptive 8×2 on slow LXC |

Related notes: `docs/SQLSTORE-LOCKS.md` · `docs/mjvm-perf.md` · `docs/RELEASE-CHECKLIST.md` · `m-semantics/docs/VERSIONING.md`.

---

## Release checklist (when cutting v0.5.0)

1. Milestone issues closed or explicitly re-targeted  
2. CI `smoke` green on `main`  
3. `ci-block-probe.sh` **ALL PASS**  
4. Tag `v0.5.0` + GitHub release + fleet smoke matrix  
5. Update README release line + this file’s “Previous” pointer  
