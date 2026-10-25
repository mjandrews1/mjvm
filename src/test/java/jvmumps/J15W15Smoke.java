package jvmumps;

import java.io.File;

public class J15W15Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j15w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    // J15: nested lock + JOB + rollback interplay
    String out = m.run("L +A TSTART S X=1 TROLLBACK L -A W \"ok\",!");
    if (!"ok\n".equals(out)) throw new IllegalStateException("j15 " + out);
    out = m.run("L +A L +A L -A W \"re\",!");
    if (!"re\n".equals(out)) throw new IllegalStateException("relock " + out);
    // W15: long pattern + literals
    check(m, "W (\"a\"?1\"a\"),!", "1\n");
    check(m, "W (\"ab\"?1\"a\"1\"b\"),!", "1\n");
    check(m, "W (\"ab\"?1\"a\"1\"c\"),!", "0\n");
    check(m, "W (\"\"?0\"x\"),!", "1\n");
    check(m, "W (\"5\"?1N),!", "1\n");
    System.out.println("J15 LOCK+TX + W15 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
