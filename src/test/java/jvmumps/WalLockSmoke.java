package jvmumps;

import jvmumps.store.WriteAheadLog;

/**
 * Multi-process flock test: second process must fail to open WAL while first holds the lock.
 *
 * <p>Usage: java jvmumps.WalLockSmoke
 */
public final class WalLockSmoke {
  public static void main(String[] args) throws Exception {
    String walPath =
        System.getProperty("java.io.tmpdir") + "/wallock-" + System.nanoTime() + ".wal";

    // Phase 1: First process acquires lock
    var wal1 = new WriteAheadLog(walPath, 100);
    wal1.appendSet("KEY1", "VAL1");
    System.out.println("Phase 1 OK: first WAL opened and wrote");

    // Phase 2: Second process (same JVM, different WriteAheadLog) must fail
    try {
      var wal2 = new WriteAheadLog(walPath, 100);
      System.out.println("FAIL: second WAL opened (should have been blocked)");
      wal2.close();
      wal1.close();
      System.exit(1);
    } catch (Exception e) {
      System.out.println("Phase 2 OK: second WAL blocked: " + e.getMessage());
    }

    // Phase 3: After close, lock released, third process can open
    wal1.close();
    var wal3 = new WriteAheadLog(walPath, 100);
    var entries = wal3.replay();
    System.out.println(
        "Phase 3 OK: WAL reopened after close, " + entries.size() + " entries recovered");
    wal3.close();

    // Cleanup
    new java.io.File(walPath).delete();
    System.out.println("WAL LOCK SMOKE OK");
  }
}
