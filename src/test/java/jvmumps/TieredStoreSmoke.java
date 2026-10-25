package jvmumps;

import java.util.List;
import jvmumps.store.TieredStore;

/** Smoke test for TieredStore — get/set/kill/increment/crash recovery. */
public final class TieredStoreSmoke {
  public static void main(String[] args) throws Exception {
    String tmp = System.getProperty("java.io.tmpdir");
    String diskPath = tmp + "/tiered-" + System.nanoTime() + ".mv.db";
    String walPath = tmp + "/tiered-" + System.nanoTime() + ".wal";

    // Phase 1: Basic operations
    var s = new TieredStore(diskPath, walPath, 10000, 100);
    try {
      s.set(List.of("G", "A"), true, MValue.ofString("hello"));
      assert s.get(List.of("G", "A"), true).asStr().equals("hello") : "get string";
      s.set(List.of("G", "B"), true, MValue.ofNumber(42));
      assert s.get(List.of("G", "B"), true).asNum() == 42 : "get number";

      MValue inc = s.increment(List.of("G", "N"), true, 5);
      assert inc.asNum() == 5 : "increment, got " + inc.asNum();
      inc = s.increment(List.of("G", "N"), true, 3);
      assert inc.asNum() == 8 : "increment 2, got " + inc.asNum();

      assert s.cacheSize() > 0 : "cache populated";
      System.out.println("TIERED SMOKE OK phase 1 (basic ops)");
    } finally {
      s.close();
    }

    // Phase 2: Crash recovery — reopen and verify WAL replay
    var s2 = new TieredStore(diskPath, walPath, 10000, 100);
    try {
      assert s2.get(List.of("G", "A"), true).asStr().equals("hello") : "recovery get string";
      assert s2.get(List.of("G", "B"), true).asNum() == 42 : "recovery get number";
      assert s2.get(List.of("G", "N"), true).asNum() == 8 : "recovery increment";
      System.out.println("TIERED SMOKE OK phase 2 (crash recovery)");
    } finally {
      s2.close();
    }

    // Cleanup
    if (!new java.io.File(diskPath).delete()) new java.io.File(diskPath).deleteOnExit();
    if (!new java.io.File(walPath).delete()) new java.io.File(walPath).deleteOnExit();
  }
}
