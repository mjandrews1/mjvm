package jvmumps;

import java.io.File;

/** J206–J210 jobs + W206–W210 patterns (M semantics). */
public final class J206W210Smoke {
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
    File db = File.createTempFile("jw206", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j206\",!", "j206\n", "J206");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j207\",!", "j207\n", "J207");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j208\",!", "j208\n", "J208");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j209\",!", "j209\n", "J209");
    chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j210\",!", "j210\n", "J210");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W206a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W206b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W206c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W207a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W207b");
    chk(m, "W (\"\"?0N),!", "1\n", "W207c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W207d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W208a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W208b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W208c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W208d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W209a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W209b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W209c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W209d");
    chk(m, "W (\"\"?.A),!", "1\n", "W209e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W210a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W210b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W210c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W210d");

    System.out.println("J206–J210 + W206–W210 OK cases=" + n);
    st.close();
  }
}
