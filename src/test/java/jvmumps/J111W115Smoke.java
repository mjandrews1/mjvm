package jvmumps;

import java.io.File;

/**
 * J111–J115 jobs + W111–W115 patterns. want() values are M/ISO semantics. On failure: fix engine,
 * not the test.
 */
public final class J111W115Smoke {
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
    File db = File.createTempFile("jw111", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    // J111–J115
    chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j111\",!", "j111\n", "J111");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j112\",!", "j112\n", "J112");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j113\",!", "j113\n", "J113");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j114\",!", "j114\n", "J114");
    chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j115\",!", "j115\n", "J115");

    // W111: full-string literals
    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W111a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W111b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W111c");

    // W112: 0-count = empty only
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W112a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W112b");
    chk(m, "W (\"\"?0A),!", "1\n", "W112c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W112d");

    // W113: exact counts
    chk(m, "W (\"aa\"?2A),!", "1\n", "W113a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W113b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W113c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W113d");

    // W114: ranges
    chk(m, "W (\"a\"?.1A),!", "1\n", "W114a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W114b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W114c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W114d");
    chk(m, "W (\"\"?.A),!", "1\n", "W114e");

    // W115: mixed
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W115a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W115b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W115c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W115d");

    System.out.println("J111–J115 + W111–W115 OK cases=" + n);
    st.close();
  }
}
