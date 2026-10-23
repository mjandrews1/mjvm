# JavaM / mJVM heuristics (de-duplicated, ranked)

**Order:** most → least important. Solo project — **no peer-review rule**.

## Correctness (non-negotiable)

| Rank | Heuristic | Absorbs |
|-----:|-----------|---------|
| **1** | **Tests are real and enforced** — CI on every commit; new behavior tested in the same PR; the test **fails without the change**; new **modules** need integration tests | #41, #49, #55, #45 |
| **2** | **Concurrency & data safety** — safe/secure/private shared data; locks; **1W-nR** and **nW-nR** queued CRUD; defined reader/writer caps | #37 |
| **3** | **JLS / JVMS** — cite sections; `--release 21` / classfile 65; no folklore | #38 |

## Safety & change quality

| Rank | Heuristic | Absorbs |
|-----:|-----------|---------|
| **4** | **Dependencies** pinned; **no known CVEs** (or dated exception) | #46 |
| **5** | **Risk & rollback** — what could break; regression check; documented rollback **if it breaks production** | #52, #48 |
| **6** | **No perf regression** — benchmark every change; threshold + justification | #44 |
| **7** | **Reproduce** — what was tested and how (commands, host, expected vs actual) | #50 |

## Structure

| Rank | Heuristic | Absorbs |
|-----:|-----------|---------|
| **8** | **Dependency graphs** on demand (M + Java) + **cycle detection** | #39 |
| **9** | **No god objects / god files** | #42 |
| **10** | **JPMS** best practice (exports, no cyclic modules) | #40 |

## Documentation & ownership

| Rank | Heuristic | Absorbs |
|-----:|-----------|---------|
| **11** | **Docs + changelog** in the same PR — what changed **and why** | #47, #51 |
| **12** | **Javadoc** on public API + quarterly review | #43 |
| **13** | **Traceability** — PR links issue; names **maintainer** | #54, #53 |

## Skipped
- Second-engineer review (solo practitioner).

## Conflict rule
Correctness (1–3) &gt; safety (4–7) &gt; structure (8–10) &gt; docs/meta (11–13).  
Perf never overrides semantic correctness.
