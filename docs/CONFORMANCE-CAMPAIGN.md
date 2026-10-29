# mJVM Conformance Campaign — sprint through 2026-11-30

**Opened:** 2026-10-29 · **Origin:** the π program (Machin, base-10⁹ limbs) — one
moderate, textbook-constructs-only program surfaced **seven distinct engine defects in a
single session**, five of them silent-wrong-answer class. This campaign generalizes how
those were found into a standing method.

## Why π found so much (the method, generalized)

The 139-case corpus and GAUNTLET gate mostly exercise **line-mode** (direct-mode one-liners).
The π program is **routine-mode**: tags, dotted blocks, loop-carried subscripted state,
multi-command control flow. That is the under-tested surface — and it is exactly the
surface real VistA code lives on. The corpus passes 131/8/0 while `F J=3:-1:1` silently
no-ops; both facts are true because they measure different things.

The π program worked as a bug-finder because it had an **oracle** — the known digits of π.
Most programs don't. The campaign's core move is therefore **differential testing**: give
every program an oracle — GT.M (V7.2-001 on U01, the FIS parent engine), Python/integers for
math, or the IR machine vs `--class` for parity — and diff.

## Current defect inventory (as of 2026-10-29)

| # | Defect | Class | Status |
|---|---|---|---|
| e9223f3 | Subscripted locals raw-key cache (stale reads) | silent wrong answer | **FIXED** |
| #13 | QUIT in argumentless FOR exits the routine | silent wrong answer | open (MFrontend) |
| #15 | preRead batch poisons subscripted global LOADs | silent wrong answer | open |
| #16 | Negative-increment FOR never loops | silent wrong answer | open (MFrontend) |
| #17 | Comma-IF breaks parser | error (loud) | open |
| #18 | IF in dotted block corrupts block; IF+dotted double-exec | silent wrong answer | open |
| #19 | Multi-command same-line FOR body breaks | silent wrong answer | open |
| #4 | `W:cond` silent no-op | silent wrong answer | open (MFrontend) |

## Workstreams

**A — Triage and fix the inventory.** Everything above, ordered by silent-wrong-answer
first. #13/#16/#17/#18/#19/#4 are all MFrontend — the same command-argument/scoping surface
P1b is consuming (comma, postconditional, dotted, argument-list classes). This is a
convergence, not a conflict: fixes land with P1b, each with a regression test. The π
program's workaround ledger (descending-index loops, IF-free bodies, chained IFs) is the
checklist of what must work without workarounds when A completes.

**B — ORACLE harness (differential vs GT.M).** A snippet corpus + runner: each case is an
M program; GT.M on U01 produces the expected output; mJVM must match. Seed with (1) every
defect's repro as a passing case after its fix, (2) the construct matrix: FOR
(positive/negative/argumentless), IF forms (single/comma/dotted/ELSE), dotted blocks
(I-free and IF-bearing), subscripted locals and globals in loops, QUIT/DO scoping,
multi-command lines. Wire as a CI gate on u04 next to GAUNTLET. GT.M runs headless
(`echo "D ^RTN" | mumps -direct`); harness lives in `tools/oracle/`.

**C — Grammar fuzzer.** Python generator over the construct matrix with randomized
nesting, subscripts, and literals; nightly batch vs GT.M; divergences auto-filed to a
ledger for triage. Budget: one page of generator, run forever. The P1b ledger counts
routines that fail to *parse*; the fuzzer measures programs that parse but *misbehave*.

**D — Known-answer math suite.** π (finished, digit-exact to 1000; the 1M run is a soak
test for the dispatch overheads as much as a computation), primes/sieve, string
algorithms, $PIECE-heavy text processing — each with a Python or closed-form oracle.
These soak the runtime, the fast paths, and the store in combinations the corpus
never reaches.

**E — Regression lock-in.** Every fixed defect becomes a corpus case (shared 139-case
file where the shape is engine-relevant, else `MRuntimeFastPathRegression`-style repo
tests) before the issue closes. No fix without a failing-then-passing test.

## Sprint cadence and exit criteria (2026-11-30)

- Defect inventory A: **zero open silent-wrong-answer issues** in the construct matrix.
- ORACLE harness: ≥100 cases, wired to CI, green on mJVM; upstream-divergence set
  documented per case (like the 8 known corpus misses).
- Fuzzer: running nightly, triaged weekly; divergence rate trending down.
- π to 1,000,000 digits: completed and verified against a Python reference.
- The π program runs **unmodified** — no workarounds — and matches GT.M's output on the
  ORACLE cases.
- VistA swallowed-routine ledger (P1b) at or below its current trajectory; no regression
  in parse rate (99.97%).

## Relation to the critical path

This is **P1d** on the master critical path. P1b (syntax classes) and P1d share the
MFrontend surface; P1c (engine-gap perf) shares the runtime; the m-corpus 131/8/0 peer
gates and GAUNTLET-on-u04 remain the safety net for every change. The campaign does not
displace P0/P5 ops work — it runs alongside.
