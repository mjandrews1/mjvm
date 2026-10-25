package jvmumps;

import java.io.File;

/** J266–J270 jobs + W266–W270 patterns (M semantics). */
public final class J266W270Smoke {
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
    File db = File.createTempFile("jw266", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j266\",!", "j266\n", "J266");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j267\",!", "j267\n", "J267");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j268\",!", "j268\n", "J268");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j269\",!", "j269\n", "J269");
    chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j270\",!", "j270\n", "J270");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W266a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W266b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W266c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W267a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W267b");
    chk(m, "W (\"\"?0N),!", "1\n", "W267c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W267d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W268a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W268b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W268c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W268d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W269a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W269b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W269c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W269d");
    chk(m, "W (\"\"?.A),!", "1\n", "W269e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W270a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W270b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W270c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W270d");

    System.out.println("J266–J270 + W266–W270 OK cases=" + n);
    st.close();
  }
}
