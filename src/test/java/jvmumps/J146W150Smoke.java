package jvmumps;

import java.io.File;

/** J146–J150 jobs + W146–W150 patterns (M semantics). */
public final class J146W150Smoke {
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
    File db = File.createTempFile("jw146", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j146\",!", "j146\n", "J146");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j147\",!", "j147\n", "J147");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j148\",!", "j148\n", "J148");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j149\",!", "j149\n", "J149");
    chk(m, "L +M:15 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j150\",!", "j150\n", "J150");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W146a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W146b");
    chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W146c");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W147a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W147b");
    chk(m, "W (\"\"?0N),!", "1\n", "W147c");
    chk(m, "W (\"5\"?0N),!", "0\n", "W147d");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W148a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W148b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W148c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W148d");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W149a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W149b");
    chk(m, "W (\"aaa\"?2.A),!", "1\n", "W149c");
    chk(m, "W (\"a\"?2.A),!", "0\n", "W149d");
    chk(m, "W (\"\"?.A),!", "1\n", "W149e");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W150a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W150b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W150c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W150d");

    System.out.println("J146–J150 + W146–W150 OK cases=" + n);
    st.close();
  }
}
