package jvmumps;

import java.util.concurrent.ThreadLocalRandom;
import jvmumps.store.*;

/**
 * Latency percentile benchmark — criterion 2 of the three performance metrics. Measures p50/p95/p99
 * response time for store operations. Uses StoreMetrics histogram for accurate percentiles.
 */
public final class LatencyBench {

  static final int WARMUP = 100_000;
  static final int MEASURE = 1_000_000;
  static final int SIZE = 10_000;

  public static void main(String[] args) throws Exception {
    int n = args.length > 0 ? Integer.parseInt(args[0]) : MEASURE;

    System.out.println("=== Latency Percentile Benchmark ===");
    System.out.println("warmup=" + WARMUP + " measure=" + n + " keys=" + SIZE);
    System.out.println();

    // Test MStore
    var memStore = new MStore();
    var memMetrics = new StoreMetrics();
    runBench(memStore, memMetrics, n, "MStore (memory)");

    // Test TieredStore
    String tmp = System.getProperty("java.io.tmpdir");
    var tieredStore =
        new jvmumps.store.TieredStore(
            tmp + "/lat-" + System.nanoTime() + ".mv.db",
            tmp + "/lat-" + System.nanoTime() + ".wal",
            10000,
            100);
    var tieredMetrics = new StoreMetrics();
    runBench(tieredStore, tieredMetrics, n, "TieredStore (cache+WAL)");

    // Test H2Store
    var h2Store = new H2Store(tmp + "/lat-" + System.nanoTime() + ".mv.db");
    var h2Metrics = new StoreMetrics();
    runBench(h2Store, h2Metrics, n, "H2Store (disk)");

    System.out.println();
    System.out.println("=== Three Performance Metrics Summary ===");
    System.out.println("(1) Sustained throughput, (2) Response time p50/p95/p99, (3) Accuracy");
    System.out.println();

    tieredStore.close();
    System.out.println("DONE");
    System.exit(0);
  }

  static void runBench(StoreApi store, StoreMetrics metrics, int n, String label) throws Exception {
    // Build keys
    String[] keys = new String[SIZE];
    for (int i = 0; i < SIZE; i++) keys[i] = "^LAT\u0001" + i;

    // Warmup
    var rng = ThreadLocalRandom.current();
    for (int i = 0; i < WARMUP; i++) {
      String key = keys[rng.nextInt(SIZE)];
      if (rng.nextInt(2) == 0) {
        store.set(key, true, MValue.ofNumber(rng.nextInt(1000)));
      } else {
        store.get(key, true);
      }
    }

    // Measure with latency tracking
    long totalNs = 0;
    int ops = 0;
    long startNs = System.nanoTime();

    for (int i = 0; i < n; i++) {
      String key = keys[rng.nextInt(SIZE)];
      long t0 = System.nanoTime();
      if (rng.nextInt(2) == 0) {
        store.set(key, true, MValue.ofNumber(rng.nextInt(1000)));
        metrics.recordWrite(System.nanoTime() - t0);
      } else {
        store.get(key, true);
        metrics.recordRead(System.nanoTime() - t0);
      }
      totalNs += System.nanoTime() - t0;
      ops++;
    }

    long elapsed = System.nanoTime() - startNs;
    double throughputK = ops / (elapsed / 1e6); // K ops/sec

    System.out.println("--- " + label + " ---");
    System.out.printf(
        "  Throughput: %,.0fK ops/sec  (%.1f ms total)%n", throughputK, elapsed / 1e6);
    System.out.printf("  Read  %s%n", metrics.latencyReport().split(" \\| ")[0]);
    System.out.printf("  Write %s%n", metrics.latencyReport().split(" \\| ")[1]);
    System.out.printf("  Cache: %.1f%% hit rate%n", metrics.cacheHitRate());
    System.out.println();
  }
}
