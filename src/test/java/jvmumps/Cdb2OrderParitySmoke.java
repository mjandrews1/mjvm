package jvmumps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * $ORDER sequence must match SqlStore collation (Cdb2Store shim). Skips when no Comdb2 pipe (CI has
 * no testdb).
 */
public class Cdb2OrderParitySmoke {
  public static void main(String[] a) throws Exception {
    String pipe = cdb2Pipe(a);
    if (pipe == null) {
      System.out.println("SKIP Cdb2OrderParitySmoke (no cdb2pipe; set CDB2PIPE)");
      return;
    }
    String db = envOr("CDB2DB", "testdb");
    String type = envOr("CDB2TYPE", "local");
    int n = 200;
    File tmp = File.createTempFile("par", ".db");
    tmp.deleteOnExit();
    SqlStore sq = new SqlStore(tmp.getAbsolutePath());
    Cdb2Store cd;
    try {
      cd = new Cdb2Store(pipe, db, type);
    } catch (Exception e) {
      System.out.println("SKIP Cdb2OrderParitySmoke (comdb2 unreachable: " + e.getMessage() + ")");
      sq.close();
      return;
    }
    cd.kill(List.of("^T"), true);
    for (int i = 0; i < n; i++) {
      sq.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
      cd.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    cd.flush();
    List<String> s1 = walk(sq);
    List<String> s2 = walk(cd);
    if (s1.size() != n || s2.size() != n) {
      throw new IllegalStateException("size sqlite=" + s1.size() + " cdb2=" + s2.size());
    }
    if (!s1.equals(s2)) {
      throw new IllegalStateException("ORDER mismatch at " + firstDiff(s1, s2));
    }
    System.out.println(
        "CDB2 ORDER PARITY OK n=" + n + " first=" + s1.get(0) + " last=" + s1.get(n - 1));
    sq.close();
    cd.close();
  }

  /** Resolve cdb2pipe; ignore smoke-suite argv (CORPUS path). Null if absent. */
  static String cdb2Pipe(String[] a) {
    String pipe = System.getenv("CDB2PIPE");
    if (pipe == null || pipe.isEmpty()) pipe = "tools/cdb2pipe";
    // make smoke passes $(CORPUS) — never treat a data file as the pipe
    if (a != null && a.length > 0 && !a[0].endsWith(".json") && new File(a[0]).canExecute()) {
      pipe = a[0];
    }
    return new File(pipe).canExecute() ? pipe : null;
  }

  static String envOr(String k, String d) {
    String v = System.getenv(k);
    return v == null || v.isEmpty() ? d : v;
  }

  static String firstDiff(List<String> a, List<String> b) {
    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
      if (!a.get(i).equals(b.get(i))) return "i=" + i + " sqlite=" + a.get(i) + " cdb2=" + b.get(i);
    }
    return "length";
  }

  static List<String> walk(StoreApi st) {
    List<String> out = new ArrayList<>();
    String k = "";
    while (true) {
      k = st.order(List.of("^T", k), true, 1);
      if (k.isEmpty()) break;
      out.add(k);
      if (out.size() > 5000) break;
    }
    return out;
  }
}
