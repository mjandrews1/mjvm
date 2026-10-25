package jvmumps;

/** Micro-benchmark SETB flush strategies on U02. */
public final class SetbBench {
  public static void main(String[] args) throws Exception {
    String pipe = System.getenv("CDB2PIPE");
    if (pipe == null) pipe = "tools/cdb2pipe";

    // Test 1: SETB with N rows via Cdb2Store
    for (int batchSize : new int[] {10, 50, 100, 300}) {
      var store = new Cdb2Store(pipe, "testdb", "local");
      // Warmup
      for (int i = 0; i < batchSize; i++) {
        store.set(java.util.List.of("SB", "W", String.valueOf(i)), true, MValue.ofNumber(i));
      }
      store.flush();
      // Measure
      long t0 = System.nanoTime();
      int trials = 5;
      for (int t = 0; t < trials; t++) {
        for (int i = 0; i < batchSize; i++) {
          store.set(java.util.List.of("SB", "B" + t, String.valueOf(i)), true, MValue.ofNumber(i));
        }
        store.flush();
      }
      long t1 = System.nanoTime();
      double ms = (t1 - t0) / 1e6 / trials;
      System.out.printf(
          "SETB %3d rows: %8.1f ms/batch (%.2f ms/row)%n", batchSize, ms, ms / batchSize);
      store.close();
    }

    // Test 2: Compare SETB vs individual SETs in transaction
    System.out.println();
    {
      var store = new Cdb2Store(pipe, "testdb", "local");
      // SETB 50
      long t0 = System.nanoTime();
      for (int i = 0; i < 50; i++)
        store.set(java.util.List.of("SB", "T1", String.valueOf(i)), true, MValue.ofNumber(i));
      store.flush();
      long t1 = System.nanoTime();
      System.out.printf("SETB 50 rows:    %.1f ms%n", (t1 - t0) / 1e6);
      store.close();
    }
  }
}
