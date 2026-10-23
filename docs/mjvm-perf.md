# mJVM M8 — performance targets, harness, results

**Date:** 2026-10-08 · **Machine:** macOS, OpenJDK 27 · **Harness:** `java mjvm.bench.MjvmBench [iters]`

## Targets (M8)

| Metric | Target | Notes |
|--------|--------|--------|
| IR arith / store loop | **≥ 1e6 it/s** | counted `S I=N` loop via JMP |
| IR concat loop | **≥ 1e5 it/s** | string-alloc bound (growing `S`) |
| Generated class | runs; IR vs class reported | class is straight-line M2 (no `goto` in Java) |
| Frontend compile | **≤ 50 ms** | small program (`S X=1 S Y=2 W X+Y,!`) |

## Harness

```bash
# from jvmumps/
javac -cp "build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar" \
  -d build src/main/java/jvmumps/*.java src/main/java/mjvm/*/*.java
java -cp "build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar" \
  mjvm.bench.MjvmBench 200000
```

- **IR loops** — real JMP back-edge (M frontend + IrMachine).
- **Unroll IR vs class** — straight-line only (ClassEmitter emits Java; `goto` is not Java — loops stay on IR until M8b class CFG).
- **Compile latency** — `MFrontend.compileLine` on a tiny SET/WRITE line.

## Results (2026-10-08, iters=200000)

| Workload | Result | Target | |
|----------|--------|--------|---|
| compile `S X=1 S Y=2 W X+Y,!` | **8.95 ms** | ≤ 50 ms | PASS |
| IR loop **inc** `S X=X+1` | **2.61M it/s** (76.7 ms) | ≥ 1.0M | PASS |
| IR loop **cat** `S S=S_"x"` | **185K it/s** (1081 ms) | ≥ 100K | PASS |
| IR loop **store** `S A=A+1` | **4.58M it/s** (43.7 ms) | ≥ 1.0M | PASS |
| unroll inc ×1000 | IR 12.7M it/s · class 3.5M it/s · **0.28× IR/class** | class runs | PASS |
| unroll cat ×200 | IR 1.2M it/s · class 3.7M it/s · **3.05× IR/class** | class runs | PASS |

**Interpretation**

- IR machine is **~2.5–4.6M simple loop iterations/s** on this host — well above the 1M floor.
- Concat is **string-alloc bound** (~185K/s on a growing string); target set accordingly.
- Generated **Java** class wins on concat (JIT + `String.concat`); IR wins on tight arith until C2 warms the class path (0.28× on a single-shot unroll). A warmup-loop bench would flatten this.
- **Compile** of a 10-op line is ~9 ms (javac-path class emit is the heavy part when `--class` is used; frontend-only is cheaper).

## Follow-ups (M8b)

1. CFG → Java (`while`/`if`) so ClassEmitter can compile **loops** (no `goto`).
2. Warmup + steady-state class vs IR (JIT).
3. BytecodeEmitter (true `invokedynamic` CALL without javac) for lower class-build latency.

## Commands

```text
java mjvm.bench.MjvmBench 200000
# optional: capture
java mjvm.bench.MjvmBench 200000 | tee /tmp/mjvm-bench.txt
```
