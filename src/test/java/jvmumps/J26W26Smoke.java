package jvmumps;

import java.io.File;

public class J26W26Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j26w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    // J26: JOB writes to SQL store; parent lock held through TX
    String out = m.run("L +J1 J \"S ^J=1\" TSTART S H=1 TCOMMIT L -J1 W \"j26\",!");
    if (!"j26\n".equals(out)) throw new IllegalStateException("j26 " + out);
    // W26: literals + optional counts + classes
    check(m, "W (\"abc\"?1\"abc\"),!", "1\n");
    check(m, "W (\"\"?0\"x\"),!", "1\n");
    check(m, "W (\"xyz\"?1A.1A.1A),!", "1\n");
    check(m, "W (\"x\"?.A),!", "1\n");
    check(m, "W (\"12\"?2N),!", "1\n");
    check(m, "W (\"1\"?2N),!", "0\n");
    check(m, "W (\"a\"?1\"a\"),!", "1\n");
    check(m, "W (\"aa\"?0\"a\"1\"a\"),!", "0\n");
    check(m, "W (\"\"?0\"a\"),!", "1\n");
    // ISO R3: exact-0 matches empty only — "a"?0"a" is 0 (restored after drop; rule-cited)
    check(m, "W (\"a\"?0\"a\"),!", "0\n");
    System.out.println("J26 JOB+TX+LOCK + W26 PATTERNS OK");
    st.close();
  }

  static void check(LineInterp m, String c, String w) {
    String g = m.run(c);
    if (!w.equals(g)) throw new IllegalStateException(c + " got " + g + " want " + w);
  }
}
