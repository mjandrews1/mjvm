package jvmumps;

import java.io.File;

public class J23W23Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j23w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +P J \"W 1\" TSTART S E=1 TCOMMIT L -P W \"j23\",!");
    if (!"j23\n".equals(out)) throw new IllegalStateException("j23 " + out);
    check(m, "W (\"ok\"?1A1A),!", "1\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"5\"?1N),!", "1\n");
    check(m, "W (\"x\"?2A),!", "0\n");
    System.out.println("J23 JOB+TX+LOCK + W23 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
