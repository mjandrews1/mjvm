package jvmumps;

import java.util.List;

/**
 * Regression: $ORDER must skip subtrees (ydb-mesh related() hang). Forward walk must yield
 * top-level siblings only, not re-enter a child's subtree. Reverse walk must also work (was broken:
 * inverted skLo/skHi for dir&lt;0).
 */
public class OrderHangSmoke {
  public static void main(String[] args) throws Exception {
    String pipe = System.getenv("CDB2PIPE");
    if (pipe == null || pipe.isEmpty()) pipe = "tools/cdb2pipe";
    if (!new java.io.File(pipe).exists()) {
      System.out.println("SKIP OrderHangSmoke (no cdb2pipe; set CDB2PIPE)");
      return;
    }
    var s = new Cdb2Store(pipe, "testdb", "local");
    try {
      // Build tree: ^G("A") with children, ^G("B"), ^G("B","X") with children, ^G("C")
      s.set(List.of("G", "A"), true, MValue.ofString("a"));
      s.set(List.of("G", "A", "1"), true, MValue.ofString("a1"));
      s.set(List.of("G", "A", "2"), true, MValue.ofString("a2"));
      s.set(List.of("G", "B"), true, MValue.ofString("b"));
      s.set(List.of("G", "B", "X"), true, MValue.ofString("bx"));
      s.set(List.of("G", "B", "X", "1"), true, MValue.ofString("bx1"));
      s.set(List.of("G", "B", "X", "2"), true, MValue.ofString("bx2"));
      s.set(List.of("G", "C"), true, MValue.ofString("c"));
      s.flush();

      // Walk forward: must yield A, B, C exactly (skip subtrees)
      StringBuilder fwd = new StringBuilder();
      String cur = "";
      int n = 0;
      while (n < 10) {
        String next = s.order(List.of("G", cur), true, 1);
        if (next.isEmpty()) break;
        fwd.append(next).append(",");
        cur = next;
        n++;
      }
      if (!fwd.toString().equals("A,B,C,")) {
        System.out.println("FAIL forward walk got: " + fwd);
        System.exit(1);
      }

      // Walk backward: C, B, A
      StringBuilder rev = new StringBuilder();
      cur = "";
      n = 0;
      while (n < 10) {
        String next = s.order(List.of("G", cur), true, -1);
        if (next.isEmpty()) break;
        rev.append(next).append(",");
        cur = next;
        n++;
      }
      if (!rev.toString().equals("C,B,A,")) {
        System.out.println("FAIL reverse walk got: " + rev);
        System.exit(1);
      }

      // Walk children of B,X: 1, 2
      StringBuilder sx = new StringBuilder();
      String sc = "";
      n = 0;
      while (n < 10) {
        String nx = s.order(List.of("G", "B", "X", sc), true, 1);
        if (nx.isEmpty()) break;
        sx.append(nx).append(",");
        sc = nx;
        n++;
      }
      if (!sx.toString().equals("1,2,")) {
        System.out.println("FAIL B,X walk got: " + sx);
        System.exit(1);
      }

      System.out.println("ORDER HANG SMOKE OK fwd=" + fwd + " rev=" + rev);
    } finally {
      s.close();
    }
  }
}
