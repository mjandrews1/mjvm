package jvmumps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Store limit probes (jvmumps#33): deep $ORDER, large kill, long keys. Prints PASS/FAIL lines; does
 * not throw on soft limits (records them).
 */
public final class LimitProbeSmoke {
  public static void main(String[] args) throws Exception {
    File db = File.createTempFile("lim", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    int n = 10_000;
    long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) {
      st.set(List.of("^H", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    double setMs = (System.nanoTime() - t0) / 1e6;
    System.out.printf("LIMIT wide-global n=%d set_ms=%.0f%n", n, setMs);

    t0 = System.nanoTime();
    int w = 0;
    String k = "";
    while (w <= n + 10) {
      k = st.order(List.of("^H", k), true, 1);
      if (k.isEmpty()) break;
      w++;
    }
    System.out.printf("LIMIT deep-order walked=%d in %.0fms%n", w, (System.nanoTime() - t0) / 1e6);
    if (w < n) System.out.println("LIMIT deep-order FAIL walked=" + w);
    else System.out.println("LIMIT deep-order PASS");

    // long subscript (near 512-byte key budget on Comdb2; SQLite TEXT is freer)
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 400; i++) sb.append('x');
    String longSub = sb.toString();
    try {
      st.set(List.of("^L", longSub), true, MValue.ofString("ok"));
      MValue v = st.get(List.of("^L", longSub), true);
      System.out.println("LIMIT long-subscript " + (v != MValue.EMPTY ? "PASS" : "FAIL"));
    } catch (Exception e) {
      System.out.println("LIMIT long-subscript FAIL " + e);
    }

    t0 = System.nanoTime();
    st.kill(List.of("^H"), true);
    System.out.printf("LIMIT kill-wide n=%d in %.0fms%n", n, (System.nanoTime() - t0) / 1e6);

    // deep path (10 subscripts)
    List<String> deep = new ArrayList<>();
    deep.add("^D");
    for (int i = 0; i < 10; i++) deep.add("s" + i);
    try {
      st.set(deep, true, MValue.ofString("deep"));
      MValue v = st.get(deep, true);
      System.out.println("LIMIT deep-path " + (v != MValue.EMPTY ? "PASS" : "FAIL"));
    } catch (Exception e) {
      System.out.println("LIMIT deep-path FAIL " + e);
    }
    st.close();
    System.out.println("LIMIT PROBE OK");
  }
}
