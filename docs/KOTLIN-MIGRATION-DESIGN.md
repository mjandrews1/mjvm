# mJVM Java → Kotlin migration — design (critical path item 12)

**Status:** DESIGN ONLY — no code (green-lit as an analysis item 2026-11-04; do not start
conversion before P1b converges in `MFrontend.java`). Owner decision recorded in
`~/CRITICAL_PATH.md` item 12.

## Why / why not (summary of the analysis)

Pros: sealed-class AST/IR + exhaustive `when` (compile-time-checked op dispatch — the biggest
structural win over `IrMachine`'s int-switch); data classes cut node boilerplate; 100% Java
interop (ABCL, guava, eclipse-collections, sqlite-jdbc, h2, mapdb, lz4, elsa all work unchanged);
`kotlin-stdlib.jar` is **already in `lib/`** (transitive dep), so zero new runtime deps;
incremental module-by-module conversion alongside Java.

Cons: build is a hand-rolled Makefile + `javac` — needs a `kotlinc` joint step or Gradle;
error-prone + google-java-format are Java-only → detekt + ktlint for Kotlin sources
(SpotBugs/ArchUnit/JaCoCo are bytecode-based and carry over); JIT perf parity must be proven
via JMH (watch generics boxing); M "undefined" is a first-class value, NOT Kotlin null — keep it
a sentinel; ~20k LOC diff noise collides with in-flight P1b; no functional gain; GraalVM
native-image is easier from plain Java.

## Codebase facts (measured 2026-11-04, branch perf/ir-dispatch-20261102)

| Module | Files | LOC | Notes |
|---|---|---|---|
| jvmumps (core: ExprEval/Ast/Intrinsics/MStore/Line*) | 46 | 10,228 | biggest; includes the LineInterp engine |
| mjvm/compiler (MFrontend, emitters) | 9 | 4,560 | **P1b in flight — convert last** |
| mjvm/runtime (IrMachine, MRuntime) | 4 | 2,269 | hot path; slot table landed 2026-11-03 |
| mjvm/tools | 5 | 1,341 | oracle harness, fuzz |
| jvmumps/store (TieredStore, WAL, metrics) | 3 | 990 | |
| mjvm/bench | 4 | 678 | JMH benches — the perf gate |
| mjvm/cli | 2 | 637 | entry point |
| mjvm/ir | 1 | 293 | `Ir.java` — opcodes + factories; **first conversion candidate** (sealed class payoff) |
| tests (src/test) | ~40 | 15,448 | J0Smoke corpus 139 + per-class smokes + ArchUnit |

Build: Makefile, `javac --release 21` directly, no Gradle/Maven. CI: `.github/workflows/gauntlet.yml`
on the U04 self-hosted runner. Static analysis: error-prone + google-java-format (javac/source
only), SpotBugs + ArchUnit + JaCoCo (bytecode — Kotlin-safe). Pre-commit hook runs format +
gauntlet locally.

## Recommended build change

Keep the Makefile; add a `kotlinc` pass: `kotlinc src/main/kotlin src/main/java -cp build:lib/* -d build`
(joint compilation handles Java↔Kotlin circular refs), then `javac` for the rest as today.
Alternative: migrate to Gradle `org.jetbrains.kotlin.jvm` — only if the Makefile's manual step
proves fragile. kotlin-stdlib already on the classpath; no new artifacts.

## Conversion order (risk-ascending, payoff-first)

1. `mjvm/ir/Ir.java` — opcodes + factories → sealed class `Ir` with `when` dispatch in IrMachine.
   Payoff: exhaustive op checking at compile time. Gate: corpus + ORACLE + smoke.
2. `jvmumps/ExprAst.java` + `ExprTokens.java` — AST nodes → data/sealed classes.
3. `mjvm/compiler/BytecodeEmitter.java`, `mjvm/bench/*` — mechanical.
4. `jvmumps/store/*` — store impls; ArchUnit/JaCoCo still apply (bytecode).
5. `mjvm/runtime` (IrMachine/MRuntime) — hot path; convert only after JMH parity is proven on
   the converted IR/AST, and keep `MRuntime`/`MStore` Java until then if JMH disagrees.
6. `jvmumps` core (ExprEval/ExprIntrinsics/Line*) — biggest; last.
7. `mjvm/compiler/MFrontend.java` — **last**, after P1b converges (it is the user's in-flight file).

## Semantics traps (must not change behavior)

- M undefined ≠ Kotlin null: keep an `UNDEF` sentinel; never map undefined to null.
- `MValue` is immutable — data-class/value semantics carry over, but watch the lazy-`str`
  field added 2026-11-03 (keep the volatile lazy-init pattern).
- String collation (`MRuntime.lt/eq` with M collation) — port the comparators verbatim; ORACLE
  OPMATRIX cases lock them.
- `$DATA`/`$ORDER` read the store tree — the locals-map-only split (2026-11-03) must be
  preserved exactly.

## Gates (every step)

- `make smoke` (J0Smoke corpus 139/139 + all *Smoke classes) — note TieredStoreRwSmoke fails on
  HEAD already (pre-existing, tracked separately).
- `tools/oracle/run.sh` — 129 cases, diverge=0 err=0 (known-bugs/known-divergence annotations).
- GAUNTLET pre-commit/CI on U04 (134/134 correctness + latency gates).
- JMH `make jmh-quick` before/after for anything in runtime/ir.

## Rollback

Per-module branches off main; each step is an independent commit, so rollback = revert the
module commit. Never convert two modules in one commit.
