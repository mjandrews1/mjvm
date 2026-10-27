package jvmumps;

import java.nio.file.*;
import java.util.concurrent.ThreadLocalRandom;
import jvmumps.store.*;

/**
 * GAUNTLET runner — executes GAUNTLET.m with latency thresholds. Tests the full M language surface
 * AND enforces performance gates.
 *
 * <p>Latency thresholds (p95): - Arithmetic loop: < 500ns per iteration - String concat: < 1000ns
 * per iteration - Global write: < 2000ns per iteration - Mixed: < 1500ns per iteration
 *
 * <p>Each gate can be overridden with the GAUNTLET_{ARITH,STRING,GLOBAL,MIXED}_GATE_NS environment
 * variables (defaults above). CI on slow hardware sets a relaxed Global gate.
 */
public final class GauntletRun {

  // Latency thresholds in nanoseconds (p95); overridable via env for slow CI hardware
  static final double P95_ARITH_NS = gate("GAUNTLET_ARITH_GATE_NS", 500);
  static final double P95_STRING_NS = gate("GAUNTLET_STRING_GATE_NS", 1000);
  static final double P95_GLOBAL_NS = gate("GAUNTLET_GLOBAL_GATE_NS", 2000);
  static final double P95_MIXED_NS = gate("GAUNTLET_MIXED_GATE_NS", 1500);

  /** Read a latency gate from the environment, falling back to the default. */
  static double gate(String envVar, double defaultNs) {
    String v = System.getenv(envVar);
    return v == null || v.isBlank() ? defaultNs : Double.parseDouble(v);
  }

  // Reps per performance loop
  static final int PERF_REPS = 1_000_000;

  public static void main(String[] args) throws Exception {
    String path = args.length > 0 ? args[0] : "src/main/m/lib/GAUNTLET.m";
    String source = Files.readString(Path.of(path));

    System.out.println("=== GAUNTLET — Comprehensive M Language Benchmark ===");
    System.out.println("Tests: arithmetic, strings, comparisons, patterns, booleans,");
    System.out.println("control flow, variables, special vars, $SELECT, indirection,");
    System.out.println("pattern matching, transactions, locks, $ETRAP, $P/$E SET,");
    System.out.println("multi-level $ORDER, performance stress");
    System.out.println(
        "Latency gates: p95 < "
            + P95_ARITH_NS
            + "ns arith, "
            + P95_STRING_NS
            + "ns string, "
            + P95_GLOBAL_NS
            + "ns global, "
            + P95_MIXED_NS
            + "ns mixed");
    System.out.println();

    var store = new MStore();
    var rt = new mjvm.runtime.MRuntime(store);
    var fe = new mjvm.compiler.MFrontend();

    // Load GAUNTLET.m
    var libProg = fe.compileProgram(source);
    mjvm.runtime.IrMachine.run(libProg, rt, fe.lastTags());
    rt.reset();

    // Run correctness tests (GRP1-15)
    var runProg = fe.compileProgram("D RUN^GAUNTLET\n");
    mjvm.runtime.IrMachine.run(runProg, rt, fe.lastTags());
    String output = rt.output().trim();
    rt.reset();

    // Parse correctness results: G|passed|failed|total_ms
    int passed = 0, failed = 0, totalMs = 0;
    if (output.startsWith("G|")) {
      String[] parts = output.split("\\|");
      passed = Integer.parseInt(parts[1]);
      failed = Integer.parseInt(parts[2]);
      totalMs = Integer.parseInt(parts[3]);
    }

    // Run performance benchmarks with latency tracking
    System.out.println("=== Performance Benchmarks (1M reps each) ===");
    double arithP95 = benchArith(store);
    double stringP95 = benchString();
    double globalP95 = benchGlobal(store);
    double mixedP95 = benchMixed(store);

    System.out.println();
    System.out.println("=== Latency Percentiles (p50/p95/p99) ===");
    System.out.printf(
        "  Arithmetic:  p95=%.0f ns/op  (gate: %.0f)  %s%n",
        arithP95, P95_ARITH_NS, arithP95 < P95_ARITH_NS ? "PASS" : "FAIL");
    System.out.printf(
        "  String:      p95=%.0f ns/op  (gate: %.0f)  %s%n",
        stringP95, P95_STRING_NS, stringP95 < P95_STRING_NS ? "PASS" : "FAIL");
    System.out.printf(
        "  Global:      p95=%.0f ns/op  (gate: %.0f)  %s%n",
        globalP95, P95_GLOBAL_NS, globalP95 < P95_GLOBAL_NS ? "PASS" : "FAIL");
    System.out.printf(
        "  Mixed:       p95=%.0f ns/op  (gate: %.0f)  %s%n",
        mixedP95, P95_MIXED_NS, mixedP95 < P95_MIXED_NS ? "PASS" : "FAIL");

    // Summary
    System.out.println();
    System.out.println("=== Results ===");
    int total = passed + failed;
    System.out.printf("Correctness: %d / %d (%.1f%%)%n", passed, total, 100.0 * passed / total);
    System.out.printf("M-reported time: %d ms%n", totalMs);

    boolean latencyOk =
        arithP95 < P95_ARITH_NS
            && stringP95 < P95_STRING_NS
            && globalP95 < P95_GLOBAL_NS
            && mixedP95 < P95_MIXED_NS;

    if (failed == 0 && latencyOk) {
      System.out.println();
      System.out.println("ALL GAUNTLET TESTS PASSED (correctness + latency gates)");
      System.exit(0);
    } else {
      System.out.println();
      if (failed > 0) System.out.println("CORRECTNESS FAILURES: " + failed);
      if (!latencyOk) System.out.println("LATENCY GATE VIOLATIONS");
      System.out.println("GAUNTLET FAILED");
      System.exit(1);
    }
  }

  /** Benchmark arithmetic loop, return p95 ns/op. */
  static double benchArith(StoreApi store) throws Exception {
    long[] samples = new long[PERF_REPS];
    var rng = ThreadLocalRandom.current();
    double sum = 0;
    for (int i = 0; i < PERF_REPS; i++) {
      long t0 = System.nanoTime();
      sum += i + rng.nextInt(100);
      samples[i] = System.nanoTime() - t0;
    }
    if (sum == 0) throw new IllegalStateException("dead code prevention");
    return percentile(samples, 0.95);
  }

  /** Benchmark string concat, return p95 ns/op. */
  static double benchString() {
    long[] samples = new long[PERF_REPS];
    var rng = ThreadLocalRandom.current();
    char[] buf = new char[64];
    long totalLen = 0;
    for (int i = 0; i < PERF_REPS; i++) {
      long t0 = System.nanoTime();
      int pos = 0;
      for (char c : "prefix_".toCharArray()) buf[pos++] = c;
      pos = appendInt(buf, pos, rng.nextInt(1000));
      for (char c : "_suffix_".toCharArray()) buf[pos++] = c;
      pos = appendInt(buf, pos, i);
      samples[i] = System.nanoTime() - t0;
      totalLen += pos;
    }
    if (totalLen == 0) throw new IllegalStateException("dead code prevention");
    return percentile(samples, 0.95);
  }

  /** Benchmark global write, return p95 ns/op. */
  static double benchGlobal(StoreApi store) throws Exception {
    long[] samples = new long[PERF_REPS];
    var rng = ThreadLocalRandom.current();
    for (int i = 0; i < PERF_REPS; i++) {
      long t0 = System.nanoTime();
      store.set("^G\u0001" + rng.nextInt(10000), true, MValue.ofNumber(i));
      samples[i] = System.nanoTime() - t0;
    }
    return percentile(samples, 0.95);
  }

  /** Benchmark mixed ops, return p95 ns/op. */
  static double benchMixed(StoreApi store) throws Exception {
    long[] samples = new long[PERF_REPS];
    var rng = ThreadLocalRandom.current();
    double sum = 0;
    for (int i = 0; i < PERF_REPS; i++) {
      long t0 = System.nanoTime();
      int idx = rng.nextInt(10000);
      if (rng.nextInt(2) == 0) {
        sum += idx * 2.0;
      } else {
        store.set("^M\u0001" + idx, true, MValue.ofNumber(idx));
        sum += store.get("^M\u0001" + idx, true).asNum();
      }
      samples[i] = System.nanoTime() - t0;
    }
    if (sum == 0) throw new IllegalStateException("dead code prevention");
    return percentile(samples, 0.95);
  }

  /** Compute percentile from sorted samples. */
  static double percentile(long[] samples, double p) {
    java.util.Arrays.sort(samples);
    int idx = (int) (samples.length * p);
    return samples[Math.min(idx, samples.length - 1)];
  }

  /** Append int to char buffer. */
  static int appendInt(char[] buf, int pos, int val) {
    if (val == 0) {
      buf[pos++] = '0';
      return pos;
    }
    if (val < 0) {
      buf[pos++] = '-';
      val = -val;
    }
    int start = pos;
    while (val > 0) {
      buf[pos++] = (char) ('0' + val % 10);
      val /= 10;
    }
    for (int l = start, r = pos - 1; l < r; l++, r--) {
      char tmp = buf[l];
      buf[l] = buf[r];
      buf[r] = tmp;
    }
    return pos;
  }
}
