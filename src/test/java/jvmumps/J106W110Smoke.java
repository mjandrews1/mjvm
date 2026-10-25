package jvmumps;

import java.io.File;

/** J106–J110 jobs + W106–W110 patterns (M semantics). */
public final class J106W110Smoke {
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
    File db = File.createTempFile("jw106", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j106\",!", "j106\n", "J106");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j107\",!", "j107\n", "J107");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j108\",!", "j108\n", "J108");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j109\",!", "j109\n", "J109");
    chk(m, "L +M:30 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j110\",!", "j110\n", "J110");

    chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W106a");
    chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W106b");
    chk(m, "W (\"\"?0\"x\"),!", "1\n", "W107a");
    chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W107b");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W108a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W108b");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W109a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W109b");
    chk(m, "W (\"\"?.A),!", "1\n", "W109c");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W110a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W110b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W110c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W110d");

    System.out.println("J106–J110 + W106–W110 OK cases=" + n);
    st.close();
  }
}
