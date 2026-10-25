package jvmumps;

import java.io.File;

/** J301–J305 jobs + W301–W305 patterns (M semantics). */
public final class J301W305Smoke {
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
    File db = File.createTempFile("jw301", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j301\",!", "j301\n", "J301");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j302\",!", "j302\n", "J302");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j303\",!", "j303\n", "J303");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j304\",!", "j304\n", "J304");
    chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j305\",!", "j305\n", "J305");

    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W301a");
    chk(m, "W (\"1a\"?1A1N),!", "0\n", "W301b");
    chk(m, "W (\"a12\"?1A1N),!", "0\n", "W301c");
    chk(m, "W (\"x\"?1\"x\"0\"y\"),!", "1\n", "W302a");
    chk(m, "W (\"xy\"?1\"x\"0\"y\"),!", "0\n", "W302b");
    chk(m, "W (\"\"?0\"x\"0\"y\"),!", "1\n", "W302c");
    chk(m, "W (\"ab\"?2.3A),!", "1\n", "W303a");
    chk(m, "W (\"abc\"?2.3A),!", "1\n", "W303b");
    chk(m, "W (\"abcd\"?2.3A),!", "0\n", "W303c");
    chk(m, "W (\"aa\"?0.2\"a\"),!", "1\n", "W304a");
    chk(m, "W (\"zzz\"?0.\"z\"),!", "1\n", "W304b");
    chk(m, "W (\"zy\"?0.\"z\"),!", "0\n", "W304c");
    chk(m, "W (\"\"?0P),!", "1\n", "W305a");
    chk(m, "W (\"!\"?1P),!", "1\n", "W305b");
    chk(m, "W (\"a\"?1P),!", "0\n", "W305c");

    System.out.println("J301–J305 + W301–W305 OK cases=" + n);
    st.close();
  }
}
