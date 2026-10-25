package jvmumps;

import java.io.File;

/** J306–J310 jobs + W306–W310 patterns (M semantics). */
public final class J306W310Smoke {
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
    File db = File.createTempFile("jw306", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j306\",!", "j306\n", "J306");
    chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j307\",!", "j307\n", "J307");
    chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j308\",!", "j308\n", "J308");
    chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j309\",!", "j309\n", "J309");
    chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j310\",!", "j310\n", "J310");

    chk(m, "W (\"Hello\"?1\"H\".1\"e\"3A),!", "1\n", "W306a");
    chk(m, "W (\"H\"?1\"H\".1\"e\"3A),!", "0\n", "W306b");
    chk(m, "W (\"Heello\"?1\"H\".1\"e\"3A),!", "0\n", "W306c");
    chk(m, "W (\"\"?0.0A),!", "1\n", "W307a");
    chk(m, "W (\"\"?0.1A),!", "1\n", "W307b");
    chk(m, "W (\"a\"?0.0A),!", "0\n", "W307c");
    chk(m, "W (\"ab1\"?2A1N),!", "1\n", "W308a");
    chk(m, "W (\"a1b\"?2A1N),!", "0\n", "W308b");
    chk(m, "W (\"ab12\"?2A1N),!", "0\n", "W308c");
    chk(m, "W (\"\"?0.3A),!", "1\n", "W309a");
    chk(m, "W (\"xyz\"?0.3A),!", "1\n", "W309b");
    chk(m, "W (\"wxyz\"?0.3A),!", "0\n", "W309c");
    chk(m, "W (\"\"?1.1\"x\"),!", "0\n", "W310a");
    chk(m, "W (\"x\"?1.1\"x\"),!", "1\n", "W310b");
    chk(m, "W (\"xx\"?1.1\"x\"),!", "0\n", "W310c");

    System.out.println("J306–J310 + W306–W310 OK cases=" + n);
    st.close();
  }
}
