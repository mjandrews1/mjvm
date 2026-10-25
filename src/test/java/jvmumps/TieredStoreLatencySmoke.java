package jvmumps;

import java.util.*;
import java.util.concurrent.*;

/**
 * CI regression guard: TieredStore p99 must not exceed H2Store p99.
 *
 * <p>Runs the same bank workload on both stores, measures latency percentiles, and fails if
 * TieredStore p99 > H2Store p99.
 *
 * <p>Usage: java jvmumps.TieredStoreLatencySmoke
 */
public final class TieredStoreLatencySmoke {

  static final int WARMUP = 500;
  static final int MEASURE = 5000;

  static final class LatencyRecorder {
    final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    void record(long nanos) {
      latencies.add(nanos);
    }

    double percentile(double p) {
      if (latencies.isEmpty()) return 0;
      long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
      int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
      return sorted[Math.max(0, idx)] / 1e3; // microseconds
    }
  }

  public static void main(String[] args) throws Exception {
    String tmp = System.getProperty("java.io.tmpdir");

    // Run H2Store
    double h2p99 = runWorkload(new H2Store(tmp + "/lat-" + System.nanoTime() + ".mv.db"));
    System.out.printf("H2Store p99:      %.1f us%n", h2p99);

    // Run TieredStore
    double tieredP99 =
        runWorkload(
            new jvmumps.store.TieredStore(
                tmp + "/lat-" + System.nanoTime() + ".mv.db",
                tmp + "/lat-" + System.nanoTime() + ".wal",
                10000,
                100));
    System.out.printf("TieredStore p99:  %.1f us%n", tieredP99);

    // Regression guard
    if (tieredP99 > h2p99) {
      System.out.println(
          "FAIL: TieredStore p99 ("
              + String.format("%.1f", tieredP99)
              + " us) exceeds H2Store p99 ("
              + String.format("%.1f", h2p99)
              + " us)");
      System.exit(1);
    }
    System.out.println("PASS: TieredStore p99 <= H2Store p99");
  }

  static double runWorkload(Object storeObj) throws Exception {
    StoreApi store = (StoreApi) storeObj;
    try {
      // Initialize bank
      for (int i = 0; i < 10; i++) {
        String a = String.valueOf(i);
        store.setNum("BANK\001C\001" + a, true, 0);
        store.setNum("BANK\001B\001" + a, true, 0);
        store.setNum("BANK\001FEE\001" + a, true, 0);
      }
      store.setNum("BANK\001N", true, 0);

      // Warmup
      for (int i = 0; i < WARMUP; i++) bankTxn(store, i);

      // Measure
      LatencyRecorder lat = new LatencyRecorder();
      for (int i = 0; i < MEASURE; i++) {
        long t0 = System.nanoTime();
        bankTxn(store, i);
        lat.record(System.nanoTime() - t0);
      }
      return lat.percentile(99);
    } finally {
      if (storeObj instanceof AutoCloseable ac) ac.close();
    }
  }

  static void bankTxn(StoreApi store, int seed) {
    Random rng = new Random(seed);
    String acct = String.valueOf(rng.nextInt(10));
    double amount = rng.nextInt(100) + 1;
    double cb = store.getNum("BANK\001C\001" + acct, true) + amount;
    store.setNum("BANK\001C\001" + acct, true, cb);
    store.setNum("BANK\001B\001" + acct, true, store.getNum("BANK\001B\001" + acct, true) + amount);
    store.increment("BANK\001N", true, 1);
  }
}
