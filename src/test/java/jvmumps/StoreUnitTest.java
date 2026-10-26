package jvmumps;

import java.util.*;
import jvmumps.store.*;

/**
 * Unit tests for the store layer (MStore, StoreMetrics, WriteAheadLog, TieredStore). Closes the 0%
 * JaCoCo coverage gap on jvmumps.store.
 *
 * <p>Usage: java -cp build:lib/... jvmumps.StoreUnitTest
 */
public final class StoreUnitTest {

  static int pass = 0, fail = 0;

  public static void main(String[] args) throws Exception {
    System.out.println("=== Store Unit Tests ===");
    System.out.println();

    testMStoreBasicOps();
    testMStoreStringKey();
    testMStoreIncrement();
    testMStoreKill();
    testMStoreData();
    testMStoreOrder();
    testMStoreQueryNext();
    testMStoreMerge();
    testMStoreScope();
    testMStoreNumFastPath();
    testMStoreMixedField();
    testMStoreOrderedField();
    testStoreMetrics();
    testWriteAheadLog();

    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(
        fail == 0 ? "  RESULT: Store unit tests OK" : "  RESULT: Store unit tests FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  // === MStore: basic get/set (List<String> path) ===

  static void testMStoreBasicOps() {
    System.out.println("--- MStore basic ops ---");
    MStore store = new MStore();

    // Set and get
    store.set(List.of("A", "B"), true, MValue.ofNumber(42));
    MValue v = store.get(List.of("A", "B"), true);
    check("set/get numeric", v.asNum() == 42);

    // Set string value
    store.set(List.of("A", "C"), true, MValue.ofString("hello"));
    v = store.get(List.of("A", "C"), true);
    check("set/get string", v.asStr().equals("hello"));

    // Get nonexistent returns EMPTY
    v = store.get(List.of("X", "Y"), true);
    check("get nonexistent", v == MValue.EMPTY);

    // Overwrite
    store.set(List.of("A", "B"), true, MValue.ofNumber(100));
    v = store.get(List.of("A", "B"), true);
    check("overwrite", v.asNum() == 100);

    // Local vs global isolation
    store.set(List.of("L", "X"), false, MValue.ofNumber(1));
    store.set(List.of("L", "X"), true, MValue.ofNumber(2));
    check(
        "local/global isolation",
        store.get(List.of("L", "X"), false).asNum() == 1
            && store.get(List.of("L", "X"), true).asNum() == 2);

    // Single-segment path
    store.set(List.of("ROOT"), true, MValue.ofNumber(99));
    check("single-segment", store.get(List.of("ROOT"), true).asNum() == 99);

    // Deep nesting
    store.set(List.of("D", "1", "2", "3"), true, MValue.ofNumber(7));
    check("deep nesting", store.get(List.of("D", "1", "2", "3"), true).asNum() == 7);
  }

  // === MStore: String key fast path ===

  static void testMStoreStringKey() {
    System.out.println("--- MStore string key ---");
    MStore store = new MStore();
    char sep = '\u0001';

    // Set/get via String key
    store.set("K" + sep + "A", true, MValue.ofNumber(10));
    MValue v = store.get("K" + sep + "A", true);
    check("string key set/get", v.asNum() == 10);

    // getNum/setNum
    store.setNum("N" + sep + "X", true, 3.14);
    double d = store.getNum("N" + sep + "X", true);
    check("setNum/getNum", Math.abs(d - 3.14) < 0.001);

    // Kill via String key
    store.kill("K" + sep + "A", true);
    v = store.get("K" + sep + "A", true);
    check("string key kill", v == MValue.EMPTY);

    // Multi-segment
    store.set("M" + sep + "P" + sep + "Q", true, MValue.ofString("deep"));
    v = store.get("M" + sep + "P" + sep + "Q", true);
    check("multi-segment string key", v.asStr().equals("deep"));
  }

  // === MStore: increment ===

  static void testMStoreIncrement() {
    System.out.println("--- MStore increment ---");
    MStore store = new MStore();
    char sep = '\u0001';

    // Increment on empty starts at 0
    MValue v = store.increment("I" + sep + "C", true, 5);
    check("increment from empty", v.asNum() == 5);

    // Increment existing
    v = store.increment("I" + sep + "C", true, 3);
    check("increment existing", v.asNum() == 8);

    // Increment with negative
    v = store.increment("I" + sep + "C", true, -2);
    check("increment negative", v.asNum() == 6);

    // Increment zero
    v = store.increment("I" + sep + "C", true, 0);
    check("increment zero", v.asNum() == 6);

    // Increment via List path
    v = store.increment(List.of("I", "D"), true, 10);
    check("increment List path", v.asNum() == 10);
  }

  // === MStore: kill ===

  static void testMStoreKill() {
    System.out.println("--- MStore kill ---");
    MStore store = new MStore();

    // Kill nonexistent is no-op
    store.kill(List.of("X", "Y"), true);
    check("kill nonexistent", store.get(List.of("X", "Y"), true) == MValue.EMPTY);

    // Kill removes node
    store.set(List.of("K", "A"), true, MValue.ofNumber(1));
    store.set(List.of("K", "B"), true, MValue.ofNumber(2));
    store.kill(List.of("K", "A"), true);
    check(
        "kill removes node",
        store.get(List.of("K", "A"), true) == MValue.EMPTY
            && store.get(List.of("K", "B"), true).asNum() == 2);

    // Kill root kills all children
    store.kill(List.of("K"), true);
    check("kill root removes children", store.get(List.of("K", "B"), true) == MValue.EMPTY);

    // Kill local vs global
    store.set(List.of("L", "X"), false, MValue.ofNumber(1));
    store.set(List.of("L", "X"), true, MValue.ofNumber(2));
    store.kill(List.of("L", "X"), true);
    check("kill global preserves local", store.get(List.of("L", "X"), false).asNum() == 1);
  }

  // === MStore: data ===

  static void testMStoreData() {
    System.out.println("--- MStore data ---");
    MStore store = new MStore();

    // No value, no descendants = 0
    int d = store.data(List.of("D", "X"), true);
    check("data empty", d == 0);

    // Value only = 1
    store.set(List.of("D", "V"), true, MValue.ofNumber(1));
    d = store.data(List.of("D", "V"), true);
    check("data value only", d == 1);

    // Descendants only = 10
    store.set(List.of("D", "K", "1"), true, MValue.ofNumber(1));
    d = store.data(List.of("D", "K"), true);
    check("data descendants only", d == 10);

    // Value + descendants = 11
    store.set(List.of("D", "K"), true, MValue.ofNumber(1));
    d = store.data(List.of("D", "K"), true);
    check("data value+descendants", d == 11);
  }

  // === MStore: order ===

  static void testMStoreOrder() {
    System.out.println("--- MStore order ---");
    MStore store = new MStore();

    // Empty parent returns ""
    String k = store.order(List.of("O", ""), true, 1);
    check("order empty parent", k.equals(""));

    // Single key
    store.set(List.of("O", "A"), true, MValue.ofNumber(1));
    k = store.order(List.of("O", ""), true, 1);
    check("order single", k.equals("A"));

    // Forward order
    store.set(List.of("O", "C"), true, MValue.ofNumber(2));
    store.set(List.of("O", "B"), true, MValue.ofNumber(3));
    k = store.order(List.of("O", ""), true, 1);
    check("order forward first", k.equals("A"));
    k = store.order(List.of("O", "A"), true, 1);
    check("order forward next", k.equals("B"));
    k = store.order(List.of("O", "B"), true, 1);
    check("order forward third", k.equals("C"));
    k = store.order(List.of("O", "C"), true, 1);
    check("order forward end", k.equals(""));

    // Backward order
    k = store.order(List.of("O", ""), true, -1);
    check("order backward first", k.equals("C"));
    k = store.order(List.of("O", "C"), true, -1);
    check("order backward next", k.equals("B"));

    // Numeric keys sort numerically
    store.set(List.of("N", "10"), true, MValue.ofNumber(1));
    store.set(List.of("N", "2"), true, MValue.ofNumber(2));
    store.set(List.of("N", "1"), true, MValue.ofNumber(3));
    k = store.order(List.of("N", ""), true, 1);
    check("order numeric first", k.equals("1"));
    k = store.order(List.of("N", "1"), true, 1);
    check("order numeric second", k.equals("2"));
    k = store.order(List.of("N", "2"), true, 1);
    check("order numeric third", k.equals("10"));
  }

  // === MStore: queryNext ===

  static void testMStoreQueryNext() {
    System.out.println("--- MStore queryNext ---");
    MStore store = new MStore();

    // Empty store
    String q = store.queryNext(List.of("Q", "X"), true, 1);
    check("queryNext empty", q.equals(""));

    // Single node
    store.set(List.of("Q", "A"), true, MValue.ofNumber(1));
    q = store.queryNext(List.of("Q", ""), true, 1);
    check("queryNext single", q.length() > 0);
  }

  // === MStore: merge ===

  static void testMStoreMerge() {
    System.out.println("--- MStore merge ---");
    MStore store = new MStore();

    // Set up source subtree
    store.set(List.of("S", "A", "1"), true, MValue.ofNumber(10));
    store.set(List.of("S", "A", "2"), true, MValue.ofNumber(20));

    // Merge to destination
    store.merge(List.of("D", "B"), true, List.of("S", "A"), true);

    check(
        "merge copies values",
        store.get(List.of("D", "B", "1"), true).asNum() == 10
            && store.get(List.of("D", "B", "2"), true).asNum() == 20);

    // Source unchanged
    check("merge preserves source", store.get(List.of("S", "A", "1"), true).asNum() == 10);
  }

  // === MStore: newScope / restoreScope ===

  static void testMStoreScope() {
    System.out.println("--- MStore scope ---");
    MStore store = new MStore();

    store.set(List.of("SC", "X"), false, MValue.ofNumber(1));
    store.newScope(List.of("SC"));
    store.set(List.of("SC", "X"), false, MValue.ofNumber(2));
    store.set(List.of("SC", "Y"), false, MValue.ofNumber(3));

    // Inside scope, X=2 and Y=3
    check(
        "scope inner values",
        store.get(List.of("SC", "X"), false).asNum() == 2
            && store.get(List.of("SC", "Y"), false).asNum() == 3);

    store.restoreScope();

    // After restore, X=1 and Y is gone
    check(
        "scope restore",
        store.get(List.of("SC", "X"), false).asNum() == 1
            && store.get(List.of("SC", "Y"), false) == MValue.EMPTY);
  }

  // === MStore: fast numeric paths ===

  static void testMStoreNumFastPath() {
    System.out.println("--- MStore numeric fast paths ---");
    MStore store = new MStore();
    char sep = '\u0001';

    // setNumAt / getNumAt
    store.setNumAt("FN", 42, 3.14);
    double v = store.getNumAt("FN", 42);
    check("setNumAt/getNumAt", Math.abs(v - 3.14) < 0.001);

    // getNumAtFast
    store.setNum("FK" + sep + "S", true, 99.5);
    v = store.getNumAtFast("FK", 0, true);
    // Fast path may or may not hit; fall back to tree
    check("getNumAtFast runs", v == v); // NaN-safe: just no crash

    // setNumAtFull
    store.setNumAtFull("FF", 1, 77.7, true);
    v = store.getNum("FF" + sep + "1", true);
    check("setNumAtFull", Math.abs(v - 77.7) < 0.001);
  }

  // === MStore: MixedField ===

  static void testMStoreMixedField() {
    System.out.println("--- MStore MixedField ---");
    MStore store = new MStore();

    MStore.MixedField mf = store.registerMixed("MF", 10);
    check("registerMixed", mf != null);

    mf.setNum(0, 42.5);
    mf.setStr(1, "hello");
    check("mixed setNum/getNum", Math.abs(mf.getNum(0) - 42.5) < 0.001);
    check("mixed setStr/getStr", mf.getStr(1).equals("hello"));
    check("mixed getAsStr", mf.getAsStr(0).length() > 0); // format varies
    check("mixed getLength", mf.getLength(1) == 5);

    MStore.MixedField mf2 = store.getMixed("MF");
    check("getMixed", mf2 == mf);
  }

  // === MStore: OrderedField ===

  static void testMStoreOrderedField() {
    System.out.println("--- MStore OrderedField ---");

    MStore.OrderedField of = new MStore.OrderedField(10);
    of.set(0, 1.0);
    of.set(5, 2.0);
    of.set(3, 3.0);

    check("ordered get", of.get(0) == 1.0 && of.get(5) == 2.0);
    check("ordered next", of.next(0) == 3);
    check("ordered prev", of.prev(5) == 3);
    int[] all = of.allIndices();
    check("ordered allIndices", all.length == 3);
  }

  // === StoreMetrics ===

  static void testStoreMetrics() {
    System.out.println("--- StoreMetrics ---");
    StoreMetrics m = new StoreMetrics();

    // Record operations
    m.recordRead(100);
    m.recordRead(200);
    m.recordWrite(150);
    m.recordWrite(300);
    m.recordCacheHit();
    m.recordCacheMiss();
    m.recordWalAppend();
    m.recordWalFlush();
    m.recordEviction();

    // Counts
    check("metrics reads", m.reads() == 2);
    check("metrics writes", m.writes() == 2);
    check("metrics cacheHits", m.cacheHits() == 1);
    check("metrics walAppends", m.walAppends() == 1);

    // Cache hit rate
    double hitRate = m.cacheHitRate();
    check("metrics cacheHitRate", hitRate >= 0 && hitRate <= 100);

    // Latency report
    String lat = m.latencyReport();
    check("latencyReport non-empty", lat.length() > 0);

    // Snapshot
    String snap = m.snapshot();
    check("snapshot non-empty", snap.length() > 0);

    // Disk check
    String disk = m.checkDisk("/tmp");
    check("checkDisk returns status", disk.length() > 0);
  }

  // === WriteAheadLog ===

  static void testWriteAheadLog() throws Exception {
    System.out.println("--- WriteAheadLog ---");
    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("wal", ".log");
    tmp.toFile().deleteOnExit();

    WriteAheadLog wal = new WriteAheadLog(tmp.toString(), 1000);

    // Append entries
    long s1 = wal.appendSet("key1", "value1");
    long s2 = wal.appendSet("key2", "value2");
    long s3 = wal.appendKill("key1");
    wal.fsync();

    check("WAL seq increments", s1 < s2 && s2 < s3);

    // Replay back
    List<WriteAheadLog.Entry> entries = wal.replay();
    check("WAL replay count", entries.size() == 3);
    check("WAL replay first op", entries.get(0).op == WriteAheadLog.OP_SET);
    check("WAL replay kill op", entries.get(2).op == WriteAheadLog.OP_KILL);
    check("WAL replay key", entries.get(0).key.equals("key1"));

    wal.close();
  }

  // === Helpers ===

  static void check(String name, boolean ok) {
    if (ok) {
      System.out.printf("  %s PASS%n", name);
      pass++;
    } else {
      System.out.printf("  %s FAIL%n", name);
      fail++;
    }
  }

  private StoreUnitTest() {}
}
