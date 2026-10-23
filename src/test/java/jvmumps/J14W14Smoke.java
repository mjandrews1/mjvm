package jvmumps;
import java.io.File;
public class J14W14Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j14w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    // J14: two locks + JOB + release
    String out = m.run("L +X,Y J \"W 1\" L -X,Y W \"done\",!");
    if (!"done\n".equals(out)) throw new IllegalStateException("j14 "+out);
    // after L -X,Y both locks are free
    if (!st.lockAcquire("X", "other", 20)) throw new IllegalStateException("X not free");
    st.lockRelease("X", "other");
    // W14 patterns
    check(m, "W (\"ab\"?1A1A),!", "1\n");
    check(m, "W (\"ab\"?1A.1A),!", "1\n");
    check(m, "W (\"\"?.1A),!", "1\n");
    check(m, "W (\"z9\"?1.AN1N),!", "1\n");
    System.out.println("J14 JOB+LOCK + W14 PATTERNS OK");
    st.close();
  }
  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c+" got "+g+" want "+w);
  }
}
