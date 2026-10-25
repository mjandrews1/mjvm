package jvmumps;

import java.io.File;

/** J286–J290 jobs + W286–W290 patterns (M semantics). */
public final class J286W290Smoke {
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
    File db = File.createTempFile("jw286", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j286\",!", "j286\n", "J286");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j287\",!", "j287\n", "J287");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j288\",!", "j288\n", "J288");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j289\",!", "j289\n", "J289");
    chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j290\",!", "j290\n", "J290");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W286a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W286b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W286c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W287a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W287b");
    chk(m, "W (\"\"?0N),!", "1\n", "W287c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W287d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W288a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W288b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W288c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W288d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W289a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W289b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W289c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W289d");
    chk(m, "W (\"\"?.A),!", "1\n", "W289e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W290a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W290b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W290c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W290d");

    System.out.println("J286–J290 + W286–W290 OK cases=" + n);
    st.close();
  }
}
