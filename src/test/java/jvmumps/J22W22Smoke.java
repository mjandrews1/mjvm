package jvmumps;
import java.io.File;
public class J22W22Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j22w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +N:15 J \"S ^N=1\" L +O TSTART S D=1 TROLLBACK L -N L -O W \"j22\",!");
    if (!"j22\n".equals(out)) throw new IllegalStateException("j22 "+out);
    check(m, "W (\"q\"?1A),!", "1\n");
    check(m, "W (\"9\"?1N),!", "1\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"ab\"?1A1A),!", "1\n");
    check(m, "W (\"a\"?2A),!", "0\n");
    System.out.println("J22 JOB+TX+LOCK + W22 PATTERNS OK");
    st.close();
  }
  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c+" got "+g+" want "+w);
  }
}
