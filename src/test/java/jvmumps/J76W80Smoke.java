package jvmumps;

import java.io.File;

/** J76–J80 jobs + W76–W80 patterns (M semantics). */
public final class J76W80Smoke {
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
    File db = File.createTempFile("jw76", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j76\",!", "j76\n", "J76");
    chk(m, "TSTART S D=1 J \"S E=1\" TROLLBACK W \"j77\",!", "j77\n", "J77");
    chk(m, "L +F:10 J \"S G=1\" L -F W \"j78\",!", "j78\n", "J78");
    chk(m, "J \"S H=1\" J \"S I=1\" TSTART S J=1 TROLLBACK W \"j79\",!", "j79\n", "J79");
    chk(m, "L +K L +L J \"S M=1\" TSTART S N=1 TCOMMIT L -K L -L W \"j80\",!", "j80\n", "J80");

    // W76: literal
    chk(m, "W (\"hello\"?1\"hello\"),!", "1\n", "W76a");
    chk(m, "W (\"hell\"?1\"hello\"),!", "0\n", "W76b");
    chk(m, "W (\"helloo\"?1\"hello\"),!", "0\n", "W76c");

    // W77: 0-count
    chk(m, "W (\"\"?0\"h\"),!", "1\n", "W77a");
    chk(m, "W (\"h\"?0\"h\"),!", "0\n", "W77b");
    chk(m, "W (\"\"?0N),!", "1\n", "W77c");
    chk(m, "W (\"9\"?0N),!", "0\n", "W77d");

    // W78: counts
    chk(m, "W (\"hh\"?2A),!", "1\n", "W78a");
    chk(m, "W (\"h\"?2A),!", "0\n", "W78b");
    chk(m, "W (\"99\"?2N),!", "1\n", "W78c");
    chk(m, "W (\"9\"?2N),!", "0\n", "W78d");

    // W79: ranges
    chk(m, "W (\"h\"?.1A),!", "1\n", "W79a");
    chk(m, "W (\"hh\"?.1A),!", "0\n", "W79b");
    chk(m, "W (\"hhh\"?2.A),!", "1\n", "W79c");
    chk(m, "W (\"h\"?2.A),!", "0\n", "W79d");
    chk(m, "W (\"\"?.A),!", "1\n", "W79e");

    // W80: mixed
    chk(m, "W (\"h1\"?1A1N),!", "1\n", "W80a");
    chk(m, "W (\"1\"?1A1N),!", "0\n", "W80b");
    chk(m, "W (\"h\"?1\"h\"),!", "1\n", "W80c");
    chk(m, "W (\"z\"?1\"h\"),!", "0\n", "W80d");

    System.out.println("J76–J80 + W76–W80 OK cases=" + n);
    st.close();
  }
}
