package jvmumps;

import java.io.File;

/** J66–J70 jobs + W66–W70 patterns (M semantics). */
public final class J66W70Smoke {
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
    File db = File.createTempFile("jw66", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j66\",!", "j66\n", "J66");
    chk(m, "TSTART S D=1 TROLLBACK J \"S E=1\" W \"j67\",!", "j67\n", "J67");
    chk(m, "L +F:15 J \"S G=1\" L -F W \"j68\",!", "j68\n", "J68");
    chk(m, "J \"S H=1\" J \"S I=1\" TSTART S J=1 TCOMMIT W \"j69\",!", "j69\n", "J69");
    chk(m, "L +K L +L J \"S M=1\" TSTART S N=1 TROLLBACK L -K L -L W \"j70\",!", "j70\n", "J70");

    // W66: literal full
    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W66a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W66b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W66c");

    // W67: 0-count empty-only
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W67a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W67b");
    chk(m, "W (\"\"?0N),!", "1\n", "W67c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W67d");

    // W68: counts
    chk(m, "W (\"ab\"?2A),!", "1\n", "W68a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W68b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W68c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W68d");

    // W69: ranges
    chk(m, "W (\"a\"?.1A),!", "1\n", "W69a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W69b");
    chk(m, "W (\"aa\"?2.A),!", "1\n", "W69c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W69d");
    chk(m, "W (\"\"?.A),!", "1\n", "W69e");

    // W70: mixed
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W70a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W70b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W70c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W70d");

    System.out.println("J66–J70 + W66–W70 OK cases=" + n);
    st.close();
  }
}
