package jvmumps;
import java.io.File;
public class J25W25Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j25w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +S J \"S ^S=1\" TSTART S G=1 TROLLBACK L -S W \"j25\",!");
    if (!"j25\n".equals(out)) throw new IllegalStateException("j25 "+out);
    check(m, "W (\"ab\"?1A1A),!", "1\n");
    check(m, "W (\"\"?0.A),!", "1\n");
    check(m, "W (\"8\"?1N),!", "1\n");
    check(m, "W (\"z\"?2A),!", "0\n");
    System.out.println("J25 JOB+TX+LOCK + W25 PATTERNS OK");
    st.close();
  }
  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c+" got "+g+" want "+w);
  }
}
