package jvmumps;

import java.util.List;

/** Smoke test for H2Store — basic get/set/kill/order/data. */
public final class H2StoreSmoke {
  public static void main(String[] args) throws Exception {
    String path = System.getProperty("java.io.tmpdir") + "/h2smoke-" + System.nanoTime() + ".mv.db";
    var s = new H2Store(path);
    try {
      // SET / GET
      s.set(List.of("G", "A"), true, MValue.ofString("hello"));
      assert s.get(List.of("G", "A"), true).asStr().equals("hello") : "get string";
      s.set(List.of("G", "B"), true, MValue.ofNumber(42));
      assert s.get(List.of("G", "B"), true).asNum() == 42 : "get number";

      // DATA
      assert s.data(List.of("G", "A"), true) == 1 : "data value only";
      assert s.data(List.of("G"), true) == 10 : "data descendants only";

      // ORDER forward
      String o1 = s.order(List.of("G", ""), true, 1);
      assert o1.equals("A") : "order fwd first, got " + o1;
      String o2 = s.order(List.of("G", "A"), true, 1);
      assert o2.equals("B") : "order fwd second, got " + o2;
      String o3 = s.order(List.of("G", "B"), true, 1);
      assert o3.isEmpty() : "order fwd end, got " + o3;

      // ORDER reverse
      String r1 = s.order(List.of("G", ""), true, -1);
      assert r1.equals("B") : "order rev first, got " + r1;
      String r2 = s.order(List.of("G", "B"), true, -1);
      assert r2.equals("A") : "order rev second, got " + r2;

      // Subtree
      s.set(List.of("G", "A", "X"), true, MValue.ofString("sub"));
      String sub = s.order(List.of("G", "A", ""), true, 1);
      assert sub.equals("X") : "order subtree, got " + sub;

      // KILL subtree
      s.kill(List.of("G", "A"), true);
      assert s.get(List.of("G", "A"), true) == MValue.EMPTY : "kill node";
      assert s.get(List.of("G", "A", "X"), true) == MValue.EMPTY : "kill descendant";
      assert s.get(List.of("G", "B"), true).asNum() == 42 : "kill sibling preserved";

      // INCREMENT
      s.set(List.of("G", "N"), true, MValue.ofNumber(10));
      MValue inc = s.increment(List.of("G", "N"), true, 5);
      assert inc.asNum() == 15 : "increment, got " + inc.asNum();

      System.out.println("H2STORE SMOKE OK");
    } finally {
      s.close();
      // Cleanup
      new java.io.File(path).delete();
      new java.io.File(path.replace(".mv.db", ".mv.db.temp")).delete();
    }
  }
}
