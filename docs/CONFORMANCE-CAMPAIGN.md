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

## Current defect inventory (as of 2026-10-29, post first ORACLE ledger)

| # | Defect | Class | Status |
|---|---|---|---|
| e9223f3 | Subscripted locals raw-key cache (stale reads) | silent wrong answer | **FIXED** |
| #13 | QUIT in argumentless FOR exits the routine | silent wrong answer | open (MFrontend) |
| #15 | preRead batch poisons subscripted global LOADs | silent wrong answer | **FIXED** (2026-10-29; SUBGWALK + RTN15 regression) |
| #16 | Negative-increment FOR never loops | silent wrong answer | open (MFrontend) — ORACLE: FORNEG, NEGSUB |
| #17 | Comma-IF breaks parser | error (loud) | open — ORACLE: IFCOMMA |
| #18 | IF in dotted block corrupts block; IF+dotted double-exec | silent wrong answer | open — ORACLE: DOTIF2, DOTIFMID, DOTIFTRUE |
| #19 | Multi-command same-line FOR body breaks | silent wrong answer | open — ORACLE: MULTILINE now passes (retest after P1b tree updates) |
| #20 | ~~DO TAG fails to compile~~ MISDIAGNOSED — real trigger is #22 | — | **closed** (D TAG innocent; all repros contained `W "lit",VAR`) |
| #21 | **Postconditional WRITE argument list consumes next line** | silent wrong answer | open — ORACLE: WCONDP |
| #4 | `W:cond` silent no-op | silent wrong answer | open (MFrontend; #21 same family) |

**ORACLE ledger (2026-10-29, 59 cases):** 32 pass, 0 unexplained diverge, 13 upstream (WRITE line-break x9, , ELSE, div-by-zero, ), 13 known-bug cases (8 bugs: #4-family, #13, #16, #17, #18, #19, #21, #22+facets, #23). Gate live in GAUNTLET CI on u04 (four-bucket tripwire).
#16 ×2, #17, #18 ×3, #20 ×2, #21/#4-family), 10 upstream-divergent in two NEW classes
(see below). The harness: `tools/oracle/` — `cases/` (M programs), `expected/`
(GT.M V7.2-001 on U01 via `refresh-expected.sh`), `known-divergence.txt` (annotated
upstream classes), `run.sh` (three-bucket ledger; CI-ready exit code).

**Upstream-divergence classes discovered (mJVM is RSM-parity; GT.M differs):**
1. **WRITE line-break in routine mode** — RSM/pymumps/mJVM append a line break when a
   line ends with WRITE (END_WRITE per command); GT.M routine mode does not. Verified
   byte-for-byte against pymumps. 8 cases. Changing this would flip the 131/8/0 corpus
   peer target — it is a parity DECISION, not a bug fix; parked for owner review.
2. **$ETRAP continue-vs-report** — mJVM/RSM run the handler and continue ("after"
   prints); GT.M reports the error text and halts. Corpus C115/C116 sanction RSM behavior.

Case-authoring lessons recorded in the harness: postconditional argumentless commands
need the two-space convention (GT.M QUITARGUSE taught it); heredocs, not shell-expanded
strings, or `$`-functions get eaten.

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
test for the dispatch overheads as much as a computation), e (tools/e.m — same
fixed-point bignum; digit-exact at 30/1k/10k/100k, 1M soak running), PEMDAS-in-M
(tools/pemdasm.m — recursive-descent parser over M's flat precedence; found mjvm#26),
the 8 fundamental data structures (tools/dstruct.m — array, linked list, stack, queue,
hash, BST, min-heap, graph BFS; identical verdicts vs GT.M), and the operator x
operand-type matrix (tools/opmatrix.m; found mjvm#27, #28), primes/sieve, string
algorithms, $PIECE-heavy text processing — each with a Python or closed-form oracle.
These soak the runtime, the fast paths, and the store in combinations the corpus
never reaches.

**E — Regression lock-in.** Every fixed defect becomes a corpus case (shared 139-case
file where the shape is engine-relevant, else `MRuntimeFastPathRegression`-style repo
tests) before the issue closes. No fix without a failing-then-passing test.

## Sprint cadence and exit criteria (2026-11-30)

- Defect inventory A: **zero open silent-wrong-answer issues** in the construct matrix.
- ORACLE harness: ≥100 cases, wired to CI, green on mJVM; upstream-divergence set
  documented per case (like the 8 known corpus misses). **Met 2026-10-29: 116 cases,
  pass=74 / upstream=17 / known-bugs=25, diverge=0, err=0 — extended the same day by
  the algebra suite (symdiff, polyarith, roots, sieve, cfe) to 121 cases,
  pass=79 / upstream=17 / known-bugs=25, diverge=0.**
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
