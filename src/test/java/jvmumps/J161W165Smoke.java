package jvmumps;

import java.io.File;

/** J161–J165 jobs + W161–W165 patterns (M semantics). */
public final class J161W165Smoke {
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
    File db = File.createTempFile("jw161", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j161\",!", "j161\n", "J161");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j162\",!", "j162\n", "J162");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j163\",!", "j163\n", "J163");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j164\",!", "j164\n", "J164");
    chk(m, "L +L:15 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j165\",!", "j165\n", "J165");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W161a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W161b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W161c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W162a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W162b");
    chk(m, "W (\"\"?0A),!", "1\n", "W162c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W162d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W163a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W163b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W163c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W163d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W164a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W164b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W164c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W164d");
    chk(m, "W (\"\"?.A),!", "1\n", "W164e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W165a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W165b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W165c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W165d");

    System.out.println("J161–J165 + W161–W165 OK cases=" + n);
    st.close();
  }
}
