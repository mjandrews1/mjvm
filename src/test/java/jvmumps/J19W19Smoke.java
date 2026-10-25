package jvmumps;

import java.io.File;

public class J19W19Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j19w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +K:100 J \"S ^K=1\" L +L L -K,L TSTART S A=1 TCOMMIT W \"j19\",!");
    if (!"j19\n".equals(out)) throw new IllegalStateException("j19 " + out);
    check(m, "W (\"a\"?1A),!", "1\n");
    check(m, "W (\"1\"?1N),!", "1\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"xy\"?1A1A),!", "1\n");
    check(m, "W (\"x\"?2A),!", "0\n");
    System.out.println("J19 JOB+TX+LOCK + W19 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
