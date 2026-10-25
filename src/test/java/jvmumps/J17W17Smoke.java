package jvmumps;

import java.io.File;

public class J17W17Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j17w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +M J \"W 1\" TSTART S Q=1 TCOMMIT L -M W \"j17\",!");
    if (!"j17\n".equals(out)) throw new IllegalStateException("j17 " + out);
    check(m, "W (\"xyz\"?1A1A1A),!", "1\n");
    check(m, "W (\"xy\"?1A1A1A),!", "0\n");
    check(m, "W (\"\"?0.A),!", "1\n");
    check(m, "W (\"12a\"?2N1A),!", "1\n");
    System.out.println("J17 JOB+TX+LOCK + W17 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
