package jvmumps;

import java.io.File;

public class J21W21Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j21w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +G:20 J \"S ^G=1\" L +H L -G,H TSTART S C=1 TCOMMIT W \"j21\",!");
    if (!"j21\n".equals(out)) throw new IllegalStateException("j21 " + out);
    check(m, "W (\"a\"?1A),!", "1\n");
    check(m, "W (\"0\"?1N),!", "1\n");
    check(m, "W (\"\"?0.A),!", "1\n");
    check(m, "W (\"xy\"?1A1A),!", "1\n");
    check(m, "W (\"x\"?2A),!", "0\n");
    System.out.println("J21 JOB+TX+LOCK + W21 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
