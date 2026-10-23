package jvmumps;
import java.io.File;
public class J16W16Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j16w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("L +P:50 J \"S ^Z=1\" L -P W \"j16\",!");
    if (!"j16\n".equals(out)) throw new IllegalStateException("j16 "+out);
    check(m, "W (\"aa\"?1\"aa\"),!", "1\n");
    check(m, "W (\"a\"?2A),!", "0\n");
    check(m, "W (\"\"?0A),!", "1\n");
    check(m, "W (\"ok\"?1A1A),!", "1\n");
    System.out.println("J16 JOB+LOCK + W16 PATTERNS OK");
    st.close();
  }
  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c+" got "+g+" want "+w);
  }
}
