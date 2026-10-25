package jvmumps;

import java.io.File;

/** J276–J280 jobs + W276–W280 patterns (M semantics). */
public final class J276W280Smoke {
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
    File db = File.createTempFile("jw276", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j276\",!", "j276\n", "J276");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j277\",!", "j277\n", "J277");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j278\",!", "j278\n", "J278");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j279\",!", "j279\n", "J279");
    chk(m, "L +M:20 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j280\",!", "j280\n", "J280");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W276a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W276b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W276c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W277a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W277b");
    chk(m, "W (\"\"?0N),!", "1\n", "W277c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W277d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W278a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W278b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W278c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W278d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W279a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W279b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W279c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W279d");
    chk(m, "W (\"\"?.A),!", "1\n", "W279e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W280a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W280b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W280c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W280d");

    System.out.println("J276–J280 + W276–W280 OK cases=" + n);
    st.close();
  }
}
