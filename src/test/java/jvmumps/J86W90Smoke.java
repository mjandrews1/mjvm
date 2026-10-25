package jvmumps;

import java.io.File;

/**
 * J86–J90 jobs + W86–W90 patterns. Expectations follow ISO/RSM M rules (full-string match, count
 * forms). If a case fails, fix the engine — do not edit `want`.
 */
public final class J86W90Smoke {
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
    File db = File.createTempFile("jw86", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    // J86–J90: JOB + LOCK + TX
    chk(m, "L +A TSTART S B=1 TCOMMIT J \"S C=1\" L -A W \"j86\",!", "j86\n", "J86");
    chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j87\",!", "j87\n", "J87");
    chk(m, "L +F:20 J \"S G=1\" L -F W \"j88\",!", "j88\n", "J88");
    chk(m, "L +H L +I J \"S J=1\" TSTART S K=1 TCOMMIT L -H L -I W \"j89\",!", "j89\n", "J89");
    chk(m, "TSTART S L=1 J \"S M=1\" TROLLBACK W \"j90\",!", "j90\n", "J90");

    // W86: literal full-string
    chk(m, "W (\"hello\"?1\"hello\"),!", "1\n", "W86a");
    chk(m, "W (\"hell\"?1\"hello\"),!", "0\n", "W86b");
    chk(m, "W (\"hello!\"?1\"hello\"),!", "0\n", "W86c"); // longer subject fails full match

    // W87: 0-count matches empty only
    chk(m, "W (\"\"?0\"h\"),!", "1\n", "W87a");
    chk(m, "W (\"h\"?0\"h\"),!", "0\n", "W87b");
    chk(m, "W (\"\"?0A),!", "1\n", "W87c");
    chk(m, "W (\"h\"?0A),!", "0\n", "W87d");

    // W88: exact counts
    chk(m, "W (\"hh\"?2A),!", "1\n", "W88a");
    chk(m, "W (\"h\"?2A),!", "0\n", "W88b");
    chk(m, "W (\"11\"?2N),!", "1\n", "W88c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W88d");

    // W89: ranges .m = 0..m ; n. = n..inf
    chk(m, "W (\"h\"?.1A),!", "1\n", "W89a");
    chk(m, "W (\"hh\"?.1A),!", "0\n", "W89b");
    chk(m, "W (\"hhh\"?2.A),!", "1\n", "W89c");
    chk(m, "W (\"h\"?2.A),!", "0\n", "W89d");
    chk(m, "W (\"\"?.A),!", "1\n", "W89e");

    // W90: mixed class + literal
    chk(m, "W (\"h1\"?1A1N),!", "1\n", "W90a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W90b");
    chk(m, "W (\"h\"?1\"h\"),!", "1\n", "W90c");
    chk(m, "W (\"z\"?1\"h\"),!", "0\n", "W90d");

    System.out.println("J86–J90 + W86–W90 OK cases=" + n);
    st.close();
  }
}
