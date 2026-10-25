package jvmumps;

import java.io.File;

/** J196–J200 jobs + W196–W200 patterns (M semantics). */
public final class J196W200Smoke {
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
    File db = File.createTempFile("jw196", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j196\",!", "j196\n", "J196");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j197\",!", "j197\n", "J197");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j198\",!", "j198\n", "J198");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j199\",!", "j199\n", "J199");
    chk(m, "L +M:20 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j200\",!", "j200\n", "J200");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W196a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W196b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W196c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W197a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W197b");
    chk(m, "W (\"\"?0N),!", "1\n", "W197c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W197d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W198a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W198b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W198c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W198d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W199a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W199b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W199c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W199d");
    chk(m, "W (\"\"?.A),!", "1\n", "W199e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W200a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W200b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W200c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W200d");

    System.out.println("J196–J200 + W196–W200 OK cases=" + n);
    st.close();
  }
}
