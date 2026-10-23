package jvmumps;
import java.io.File;
public class J12W12Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j12w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    // J12: parent lock, JOB child that would take same lock (must not steal)
    if (!st.lockAcquire("^J12", "parent", 50)) throw new IllegalStateException("parent lock");
    String out = m.run("J \"S ^C=1\" W \"spawned\",!");
    if (!"spawned\n".equals(out)) throw new IllegalStateException(out);
    if (st.lockAcquire("^J12", "child", 20)) throw new IllegalStateException("child stole lock");
    st.lockRelease("^J12", "parent");
    if (!st.lockAcquire("^J12", "child", 40)) throw new IllegalStateException("child after release");
    st.lockRelease("^J12", "child");

    // W12: pattern edge cases
    LineInterp p = new LineInterp();
    check(p, "W (\"aaa\"?.A),!", "1\n");
    check(p, "W (\"aaa\"?1A.A),!", "1\n");
    check(p, "W (\"a\"?0A1A),!", "1\n");
    check(p, "W (\"\"?0A0N),!", "1\n");
    check(p, "W (\"x1y\"?1A1N1A),!", "1\n");
    check(p, "W (\"x1\"?1A1N1A),!", "0\n");
    System.out.println("J12 LOCK-JOB + W12 PATTERNS OK");
    st.close();
  }
  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c+" got "+g+" want "+w);
  }
}
