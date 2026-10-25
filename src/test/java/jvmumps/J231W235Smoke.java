package jvmumps;

import java.io.File;

/** J231–J235 jobs + W231–W235 patterns (M semantics). */
public final class J231W235Smoke {
  static int n = 0;

  static void chk(LineInterp m, String code, String want, String tag) {
    String got = m.run(code);
    if (!want.equals(got))
      throw new IllegalStateException(tag + " " + code + " got=" + q(got) + " want=" + q(want));
    n++;
  }

  static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }

  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("jw231", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j231\",!", "j231\n", "J231");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j232\",!", "j232\n", "J232");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j233\",!", "j233\n", "J233");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j234\",!", "j234\n", "J234");
    chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j235\",!", "j235\n", "J235");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W231a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W231b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W231c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W232a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W232b");
    chk(m, "W (\"\"?0A),!", "1\n", "W232c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W232d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W233a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W233b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W233c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W233d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W234a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W234b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W234c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W234d");
    chk(m, "W (\"\"?.A),!", "1\n", "W234e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W235a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W235b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W235c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W235d");

    System.out.println("J231–J235 + W231–W235 OK cases=" + n);
    st.close();
  }
}
