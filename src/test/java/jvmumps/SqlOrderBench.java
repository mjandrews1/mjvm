package jvmumps;

import java.io.File;
import java.util.*;

public class SqlOrderBench {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("ord", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 20000; i++) {
      st.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    long t1 = System.currentTimeMillis();
    String k = "";
    int n = 0;
    while (true) {
      List<String> p = new ArrayList<>();
      p.add("^T");
      p.add(k);
      k = st.order(p, true, 1);
      if (k.isEmpty()) break;
      n++;
      if (n > 25000) break;
    }
    long t2 = System.currentTimeMillis();
    System.out.println(
        "SQL ORDER insert=" + (t1 - t0) + "ms walk=" + n + " keys in " + (t2 - t1) + "ms");
    if (n < 20000) throw new IllegalStateException("walked " + n);
    System.out.println("SQL ORDER LARGE OK");
    st.close();
  }
}
