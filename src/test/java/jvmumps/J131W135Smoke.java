package jvmumps;

import java.io.File;

/** J131–J135 jobs + W131–W135 patterns (M semantics). */
public final class J131W135Smoke {
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
    File db = File.createTempFile("jw131", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j131\",!", "j131\n", "J131");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j132\",!", "j132\n", "J132");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j133\",!", "j133\n", "J133");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j134\",!", "j134\n", "J134");
    chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j135\",!", "j135\n", "J135");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W131a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W131b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W131c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W132a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W132b");
    chk(m, "W (\"\"?0A),!", "1\n", "W132c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W132d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W133a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W133b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W133c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W133d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W134a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W134b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W134c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W134d");
    chk(m, "W (\"\"?.A),!", "1\n", "W134e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W135a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W135b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W135c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W135d");

    System.out.println("J131–J135 + W131–W135 OK cases=" + n);
    st.close();
  }
}
