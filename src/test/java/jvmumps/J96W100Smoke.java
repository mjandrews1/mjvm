package jvmumps;

import java.io.File;

/** J96–J100 jobs + W96–W100 patterns (M semantics). */
public final class J96W100Smoke {
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
    File db = File.createTempFile("jw96", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j96\",!", "j96\n", "J96");
    chk(m, "TSTART S D=1 TROLLBACK J \"S E=1\" W \"j97\",!", "j97\n", "J97");
    chk(m, "L +F:10 J \"S G=1\" L -F W \"j98\",!", "j98\n", "J98");
    chk(m, "J \"S H=1\" J \"S I=1\" TSTART S J=1 TCOMMIT W \"j99\",!", "j99\n", "J99");
    chk(m, "L +K L +L J \"S M=1\" TSTART S N=1 TROLLBACK L -K L -L W \"j100\",!", "j100\n", "J100");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W96a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W96b");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W97a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W97b");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W98a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W98b");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W99a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W99b");
    chk(m, "W (\"\"?.A),!", "1\n", "W99c");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W100a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W100b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W100c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W100d");

    System.out.println("J96–J100 + W96–W100 OK cases=" + n);
    st.close();
  }
}
