package jvmumps;

import java.io.File;

/** J261–J265 jobs + W261–W265 patterns (M semantics). */
public final class J261W265Smoke {
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
    File db = File.createTempFile("jw261", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j261\",!", "j261\n", "J261");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j262\",!", "j262\n", "J262");
    chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j263\",!", "j263\n", "J263");
    chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j264\",!", "j264\n", "J264");
    chk(m, "L +L:20 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j265\",!", "j265\n", "J265");

    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W261a");
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W261b");
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W261c");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W262a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W262b");
    chk(m, "W (\"\"?0A),!", "1\n", "W262c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W262d");
    chk(m, "W (\"aa\"?2A),!", "1\n", "W263a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W263b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W263c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W263d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W264a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W264b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W264c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W264d");
    chk(m, "W (\"\"?.A),!", "1\n", "W264e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W265a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W265b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W265c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W265d");

    System.out.println("J261–J265 + W261–W265 OK cases=" + n);
    st.close();
  }
}
