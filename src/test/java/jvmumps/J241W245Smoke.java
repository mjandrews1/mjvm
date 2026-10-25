package jvmumps;

import java.io.File;

/** J241–J245 jobs + W241–W245 patterns (M semantics). */
public final class J241W245Smoke {
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
    File db = File.createTempFile("jw241", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j241\",!", "j241\n", "J241");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j242\",!", "j242\n", "J242");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j243\",!", "j243\n", "J243");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j244\",!", "j244\n", "J244");
    chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j245\",!", "j245\n", "J245");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W241a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W241b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W241c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W242a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W242b");
    chk(m, "W (\"\"?0A),!", "1\n", "W242c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W242d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W243a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W243b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W243c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W243d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W244a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W244b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W244c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W244d");
    chk(m, "W (\"\"?.A),!", "1\n", "W244e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W245a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W245b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W245c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W245d");

    System.out.println("J241–J245 + W241–W245 OK cases=" + n);
    st.close();
  }
}
