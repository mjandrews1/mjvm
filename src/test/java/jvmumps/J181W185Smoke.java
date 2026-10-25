package jvmumps;

import java.io.File;

/** J181–J185 jobs + W181–W185 patterns (M semantics). */
public final class J181W185Smoke {
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
    File db = File.createTempFile("jw181", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j181\",!", "j181\n", "J181");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j182\",!", "j182\n", "J182");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j183\",!", "j183\n", "J183");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j184\",!", "j184\n", "J184");
    chk(m, "L +L:20 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j185\",!", "j185\n", "J185");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W181a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W181b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W181c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W182a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W182b");
    chk(m, "W (\"\"?0A),!", "1\n", "W182c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W182d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W183a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W183b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W183c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W183d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W184a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W184b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W184c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W184d");
    chk(m, "W (\"\"?.A),!", "1\n", "W184e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W185a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W185b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W185c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W185d");

    System.out.println("J181–J185 + W181–W185 OK cases=" + n);
    st.close();
  }
}
