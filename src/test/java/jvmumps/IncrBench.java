package jvmumps;

import java.util.List;

/** Benchmark atomic increment across all stores. */
public final class IncrBench {
  public static void main(String[] args) throws Exception {
    int n = 10000;
    String tmp = System.getProperty("java.io.tmpdir");
    List<String> key = List.of("BANK", "N");

    // MStore
    {
      var s = new MStore();
      for (int i = 0; i < 100; i++) s.increment(key, true, 1);
      long t0 = System.nanoTime();
      for (int i = 0; i < n; i++) s.increment(key, true, 1);
      long t1 = System.nanoTime();
      System.out.printf("MStore incr:    %.2f us/op (n=%d)%n", (t1 - t0) / 1e3 / n, n);
    }

    // H2Store
    {
      String path = tmp + "/incr-" + System.nanoTime() + ".mv.db";
      var s = new H2Store(path);
      for (int i = 0; i < 100; i++) s.increment(key, true, 1);
      long t0 = System.nanoTime();
      for (int i = 0; i < n; i++) s.increment(key, true, 1);
      long t1 = System.nanoTime();
      System.out.printf("H2Store incr:   %.2f us/op (n=%d)%n", (t1 - t0) / 1e3 / n, n);
      s.close();
    }

    // MapDBStore
    {
      String path = tmp + "/incr-" + System.nanoTime() + ".mapdb";
      var s = new MapDBStore(path);
      for (int i = 0; i < 100; i++) s.increment(key, true, 1);
      long t0 = System.nanoTime();
      for (int i = 0; i < n; i++) s.increment(key, true, 1);
      long t1 = System.nanoTime();
      System.out.printf("MapDBStore incr: %.2f us/op (n=%d)%n", (t1 - t0) / 1e3 / n, n);
      s.close();
    }
  }
}
