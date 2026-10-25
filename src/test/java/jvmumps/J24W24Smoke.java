package jvmumps;

import java.io.File;

public class J24W24Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j24w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +Q J \"S ^Q=1\" TSTART S F=1 TCOMMIT L -Q W \"j24\",!");
    if (!"j24\n".equals(out)) throw new IllegalStateException("j24 " + out);
    check(m, "W (\"hi\"?1A1A),!", "1\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"2\"?1N),!", "1\n");
    check(m, "W (\"a\"?2A),!", "0\n");
    System.out.println("J24 JOB+TX+LOCK + W24 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
