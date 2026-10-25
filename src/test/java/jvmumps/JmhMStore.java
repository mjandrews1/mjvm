package jvmumps;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH microbenchmarks for mJVM hot paths. Replaces hand-rolled timing with proper warmup, forking,
 * and statistical rigor.
 *
 * <p>Run: java -cp build:lib/jmh-core-1.37.jar:... jvmumps.JmhMStore Or: make jmh
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class JmhMStore {

  private MStore store;

  @Setup
  public void setup() {
    store = new MStore();
    for (int i = 0; i < 1000; i++) {
      store.set("KEY" + (char) 1 + i, true, MValue.ofNumber(i));
    }
  }

  @Benchmark
  public MValue getNum() {
    return store.get("KEY" + (char) 1 + "42", true);
  }

  @Benchmark
  public void setNum() {
    store.set("KEY" + (char) 1 + "42", true, MValue.ofNumber(3.14));
  }

  @Benchmark
  public MValue increment() {
    return store.increment("COUNTER" + (char) 1 + "x", true, 1.0);
  }

  @Benchmark
  public void setAndGet() {
    store.set("TMP" + (char) 1 + "a", true, MValue.ofNumber(99));
    store.get("TMP" + (char) 1 + "a", true);
  }

  @Benchmark
  public int orderTraversal() {
    int count = 0;
    String k = "";
    for (int i = 0; i < 100; i++) {
      k = store.order(java.util.List.of("KEY", k), true, 0);
      if (k.isEmpty()) break;
      count++;
    }
    return count;
  }

  @Benchmark
  public double evalExpr() {
    mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(store);
    jvmumps.MValue v = rt.evalExpr("1+2*3");
    return v.asNum();
  }

  // === Insertion-heavy workloads — measure sortedKeys cache invalidation ===

  @Benchmark
  public void setNumExisting() {
    // Write to existing key — should NOT invalidate cache (but does currently)
    store.set("KEY" + (char) 1 + "42", true, MValue.ofNumber(3.14));
  }

  @Benchmark
  public void insertNewKey() {
    // Insert a brand-new key — MUST invalidate cache
    store.set("NEW" + (char) 1 + String.valueOf(System.nanoTime()), true, MValue.ofNumber(1));
  }

  @Benchmark
  public void insertHeavy() {
    // Rapid inserts — worst case for cache invalidation
    for (int i = 0; i < 10; i++) {
      store.set("INS" + (char) 1 + String.valueOf(System.nanoTime() + i), true, MValue.ofNumber(i));
    }
  }

  @Benchmark
  public int insertThenOrder() {
    // Mixed: insert then order — forces cache rebuild each time
    store.set("MX" + (char) 1 + String.valueOf(System.nanoTime()), true, MValue.ofNumber(1));
    String k = "";
    int count = 0;
    for (int i = 0; i < 50; i++) {
      k = store.order(java.util.List.of("KEY", k), true, 0);
      if (k.isEmpty()) break;
      count++;
    }
    return count;
  }

  @Benchmark
  public int orderOnly() {
    // Baseline: order without any writes — cache should stay warm
    String k = "";
    int count = 0;
    for (int i = 0; i < 100; i++) {
      k = store.order(java.util.List.of("KEY", k), true, 0);
      if (k.isEmpty()) break;
      count++;
    }
    return count;
  }

  public static void main(String[] args) throws RunnerException {
    new Runner(new OptionsBuilder().include(JmhMStore.class.getSimpleName()).build()).run();
  }
}
