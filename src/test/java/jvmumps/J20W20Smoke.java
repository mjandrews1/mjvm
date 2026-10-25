package jvmumps;

import java.io.File;

public class J20W20Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j20w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +F:10 J \"W 1\" TSTART S B=1 TROLLBACK L -F W \"j20\",!");
    if (!"j20\n".equals(out)) throw new IllegalStateException("j20 " + out);
    check(m, "W (\"hi\"?1A1A),!", "1\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"3\"?1N),!", "1\n");
    check(m, "W (\"ab\"?1A1A1A),!", "0\n");
    System.out.println("J20 JOB+TX+LOCK + W20 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
