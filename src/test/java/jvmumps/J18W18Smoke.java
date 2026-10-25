package jvmumps;

import java.io.File;

public class J18W18Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j18w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +R J \"S ^R=2\" TSTART S Z=1 TROLLBACK L -R W \"j18\",!");
    if (!"j18\n".equals(out)) throw new IllegalStateException("j18 " + out);
    check(m, "W (\"hi\"?1A1A),!", "1\n");
    check(m, "W (\"\"?0.A),!", "1\n");
    check(m, "W (\"7\"?1N),!", "1\n");
    check(m, "W (\"ab\"?1A1N),!", "0\n");
    System.out.println("J18 JOB+TX+LOCK + W18 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
