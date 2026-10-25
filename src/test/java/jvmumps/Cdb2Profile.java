package jvmumps;

import java.util.List;

/**
 * Micro-profiler for mJVM + Cdb2Store stack on U02. Isolates: pipe round-trip, store op count, IR
 * overhead, allocation cost.
 */
public final class Cdb2Profile {

  static final List<String> P1 = List.of("BANK", "C", "0");
  static final List<String> P2 = List.of("BANK", "B", "0");
  static final List<String> P3 = List.of("BANK", "N");

  public static void main(String[] args) throws Exception {
    String pipe = System.getenv("CDB2PIPE");
    if (pipe == null) pipe = "tools/cdb2pipe";

    System.out.println("=== mJVM + Cdb2Store micro-profiler (U02) ===");
    var store = new Cdb2Store(pipe, "testdb", "local");
    double getUs, setUs, depUs, rawUs, irUs;

    try {
      // Warmup
      for (int i = 0; i < 50; i++) {
        store.set(List.of("PROF", "W", String.valueOf(i)), true, MValue.ofNumber(i));
      }
      store.flush();

      // 1. Single GET round-trip
      long t0 = System.nanoTime();
      int n = 1000;
      for (int i = 0; i < n; i++) {
        store.get(List.of("PROF", "W", String.valueOf(i % 50)), true);
      }
      long t1 = System.nanoTime();
      getUs = (t1 - t0) / 1e3 / n;
      System.out.printf("GET round-trip:      %.1f us/op (n=%d)%n", getUs, n);

      // 2. Single SET (buffered)
      t0 = System.nanoTime();
      for (int i = 0; i < n; i++) {
        store.set(List.of("PROF", "S", String.valueOf(i % 50)), true, MValue.ofNumber(i));
      }
      t1 = System.nanoTime();
      setUs = (t1 - t0) / 1e3 / n;
      System.out.printf("SET (buffered):      %.1f us/op (n=%d)%n", setUs, n);

      // 3. Flush (SETB batch)
      t0 = System.nanoTime();
      store.flush();
      t1 = System.nanoTime();
      System.out.printf("FLUSH (SETB batch):  %.1f us%n", (t1 - t0) / 1e3);

      // 4. Bank DEP pattern (6 store ops)
      System.out.println();
      System.out.println("=== Bank DEP pattern (6 store ops) ===");
      for (int i = 0; i < 10; i++) depPattern(store, i);
      store.flush();

      int nTxn = 100;
      t0 = System.nanoTime();
      for (int i = 0; i < nTxn; i++) depPattern(store, i);
      t1 = System.nanoTime();
      depUs = (t1 - t0) / 1e3 / nTxn;
      System.out.printf("DEP (6 ops):         %.1f us/txn (n=%d)%n", depUs, nTxn);
      System.out.printf("DEP per store op:    %.1f us%n", depUs / 6);
      long ops = store.storeOps();
      System.out.printf("Total store ops:     %d (expect ~%d)%n", ops, nTxn * 6);
    } finally {
      store.close();
    }

    // 5. IR overhead (MStore)
    System.out.println();
    System.out.println("=== IR interpretation overhead (MStore) ===");
    var mstore = new MStore();
    int n2 = 10000;
    long t0 = System.nanoTime();
    for (int i = 0; i < n2; i++) {
      mstore.set(P1, true, MValue.ofNumber(i));
      mstore.get(P1, true);
    }
    long t1 = System.nanoTime();
    rawUs = (t1 - t0) / 1e3 / n2;
    System.out.printf("Raw set+get (MStore): %.2f us/op (n=%d)%n", rawUs, n2);

    var rt = new mjvm.runtime.MRuntime(mstore);
    var fe = new mjvm.compiler.MFrontend();
    var prog = fe.compileProgram("S ^X=1 S Y=$G(^X)\n");
    for (int i = 0; i < 100; i++) {
      rt.reset();
      mjvm.runtime.IrMachine.run(prog, rt, fe.lastTags());
    }
    t0 = System.nanoTime();
    for (int i = 0; i < n2; i++) {
      rt.reset();
      mjvm.runtime.IrMachine.run(prog, rt, fe.lastTags());
    }
    t1 = System.nanoTime();
    irUs = (t1 - t0) / 1e3 / n2;
    System.out.printf("Via IR (S+G):        %.2f us/op (n=%d)%n", irUs, n2);
    System.out.printf("IR overhead:         %.2f us/op (%.0fx raw)%n", irUs - rawUs, irUs / rawUs);

    // 6. Summary
    System.out.println();
    System.out.println("=== SUMMARY (per bank txn = 6 store ops) ===");
    double pipeCost = getUs * 3 + 50; // 3 GETs + 1 SETB flush
    double irCost = (irUs - rawUs) * 6;
    double allocCost = 30;
    System.out.printf("  Pipe round-trip:     %.0f us (3 GETs + 1 SETB)%n", pipeCost);
    System.out.printf("  IR overhead:         %.0f us (6 ops x %.1f us)%n", irCost, irUs - rawUs);
    System.out.printf("  Alloc overhead:      ~%.0f us%n", allocCost);
    System.out.printf("  TOTAL ESTIMATED:     ~%.0f us/txn%n", pipeCost + irCost + allocCost);
    System.out.printf("  MEASURED (DEP):      %.0f us/txn%n", depUs);
    System.out.println();
    System.out.println("  KEY INSIGHT: Pipe round-trips dominate.");
    System.out.println("  Each GET = 1 write+read through cdb2pipe subprocess.");
    System.out.println("  Bank DEP = 3 GETs (flush+round-trip each) + 1 SETB flush.");
  }

  /** Simulate DEP: GET C, SET C, GET B, SET B, INCR N, SET J */
  static void depPattern(StoreApi store, int i) {
    List<String> acct = List.of("BANK", "C", "0");
    List<String> bank = List.of("BANK", "B", "0");
    List<String> n = List.of("BANK", "N");
    List<String> j = List.of("BANK", "J", String.valueOf(i));
    double cb = store.get(acct, true).asNum() + 10;
    store.set(acct, true, MValue.ofNumber(cb));
    double bb = store.get(bank, true).asNum() + 10;
    store.set(bank, true, MValue.ofNumber(bb));
    store.increment(n, true, 1);
    store.set(j, true, MValue.ofString("D,0,10," + (int) cb));
  }

  private Cdb2Profile() {}
}
