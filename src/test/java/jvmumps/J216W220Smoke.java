package jvmumps;

import java.io.File;

/** J216–J220 jobs + W216–W220 patterns (M semantics). */
public final class J216W220Smoke {
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
    File db = File.createTempFile("jw216", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j216\",!", "j216\n", "J216");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j217\",!", "j217\n", "J217");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j218\",!", "j218\n", "J218");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j219\",!", "j219\n", "J219");
    chk(m, "L +M:20 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j220\",!", "j220\n", "J220");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W216a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W216b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W216c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W217a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W217b");
    chk(m, "W (\"\"?0N),!", "1\n", "W217c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W217d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W218a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W218b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W218c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W218d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W219a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W219b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W219c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W219d");
    chk(m, "W (\"\"?.A),!", "1\n", "W219e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W220a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W220b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W220c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W220d");

    System.out.println("J216–J220 + W216–W220 OK cases=" + n);
    st.close();
  }
}
