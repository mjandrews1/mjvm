package jvmumps;

import jvmumps.store.TieredStore;

/**
 * Single-writer + read-only reader test. Writer owns WAL (exclusive lock). Reader opens without
 * lock.
 *
 * <p>Usage: java jvmumps.TieredStoreRwSmoke
 */
public final class TieredStoreRwSmoke {
  public static void main(String[] args) throws Exception {
    String tmp = System.getProperty("java.io.tmpdir");
    String diskPath = tmp + "/rw-" + System.nanoTime() + ".mv.db";
    String walPath = diskPath + ".wal";

    // Phase 1: Writer opens and writes
    var writer = new TieredStore(diskPath, walPath, 10000, 100, false);
    writer.set("KEY1", true, MValue.ofString("value1"));
    writer.setNum("KEY2", true, 42);
    writer.increment("KEY3", true, 1);
    System.out.println("Phase 1 OK: writer opened and wrote");

    // Phase 2: Reader opens (no WAL lock conflict)
    var reader = TieredStore.openReadOnly(diskPath);
    System.out.println("Phase 2 OK: reader opened (no lock conflict)");

    // Phase 3: Reader can read what writer wrote
    assert reader.get("KEY1", true).asStr().equals("value1") : "reader sees KEY1";
    assert reader.getNum("KEY2", true) == 42 : "reader sees KEY2";
    assert reader.getNum("KEY3", true) == 1 : "reader sees KEY3";
    System.out.println("Phase 3 OK: reader sees writer's data");

    // Phase 4: Reader rejects writes
    try {
      reader.set("KEY4", true, MValue.ofString("nope"));
      System.out.println("FAIL: reader accepted write");
      System.exit(1);
    } catch (MError e) {
      System.out.println("Phase 4 OK: reader rejects write: " + e.getMessage());
    }

    // Phase 5: Writer still owns WAL (exclusive)
    try {
      // Removed dead store: writer2
      System.out.println("FAIL: second writer opened");
      System.exit(1);
    } catch (Exception e) {
      System.out.println("Phase 5 OK: second writer blocked: " + e.getMessage());
    }

    // Phase 6: Close writer, reader still works
    writer.close();
    var reader2 = TieredStore.openReadOnly(diskPath);
    assert reader2.get("KEY1", true).asStr().equals("value1") : "reader2 sees KEY1";
    System.out.println("Phase 6 OK: reader works after writer close");
    reader.close();
    reader2.close();

    // Cleanup
    new java.io.File(diskPath).delete();
    new java.io.File(walPath).delete();
    System.out.println("TIERED STORE RW SMOKE OK");
  }
}
