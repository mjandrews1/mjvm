package jvmumps;

import java.util.ArrayList;
import java.util.List;

/**
 * $ORDER walk bench over {@link Cdb2Store} — mirror of {@link SqlOrderBench}
 * so the Comdb2 vs SQLite walk comparison is apples-to-apples (same collation,
 * same 20k numeric subscripts, same cursor loop).
 *
 * Usage: Cdb2OrderBench [pipePath] [db] [type] [n]
 * Defaults: tools/cdb2pipe · testdb · local · 20000
 */
public class Cdb2OrderBench {
  public static void main(String[] a) throws Exception {
    String pipe = a.length > 0 ? a[0] : "tools/cdb2pipe";
    String db = a.length > 1 ? a[1] : "testdb";
    String type = a.length > 2 ? a[2] : "local";
    int n = a.length > 3 ? Integer.parseInt(a[3]) : 20000;

    Cdb2Store st = new Cdb2Store(pipe, db, type);
    // clear prior run
    st.kill(List.of("^T"), true);

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      st.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    long t1 = System.currentTimeMillis();

    String k = "";
    int cnt = 0;
    while (true) {
      List<String> p = new ArrayList<>();
      p.add("^T");
      p.add(k);
      k = st.order(p, true, 1);
      if (k.isEmpty()) break;
      cnt++;
      if (cnt > n + 5000) break;
    }
    long t2 = System.currentTimeMillis();

    System.out.println("CDB2 ORDER insert=" + (t1 - t0) + "ms walk=" + cnt + " keys in " + (t2 - t1) + "ms");
    if (cnt < n) throw new IllegalStateException("walked " + cnt);
    System.out.println("CDB2 ORDER LARGE OK");
    st.close();
  }
}
