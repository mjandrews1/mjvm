package jvmumps;

import java.io.File;

/**
 * J51–J55 jobs + W51–W55 patterns. Expectations are derived from ISO/RSM pattern rules — not from
 * engine output.
 *
 * <p>Rules used (M / ISO 11756-style): - `?` is a FULL-string match - `n A` exactly n of class A;
 * `.m` = 0..m; `n.` = n..inf; `.` = 0..inf - `0 X` means zero occurrences of X (matches empty only)
 * - literals `"str"` match exactly that substring
 */
public final class J51W55Smoke {
  static int n = 0;

  static void chk(LineInterp m, String code, String want, String tag) {
    String got = m.run(code);
    if (!want.equals(got)) {
      throw new IllegalStateException(tag + " " + code + " got=" + q(got) + " want=" + q(want));
    }
    n++;
  }

  static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }

  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("jw51", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    // J51
    chk(m, "L +A TSTART S B=1 TROLLBACK J \"S C=1\" L -A W \"j51\",!", "j51\n", "J51");
    // J52
    chk(m, "J \"S D=1\" L +E TSTART S F=1 TCOMMIT L -E W \"j52\",!", "j52\n", "J52");
    // J53
    chk(m, "TSTART S G=1 TCOMMIT J \"S H=1\" W \"j53\",!", "j53\n", "J53");
    // J54
    chk(m, "L +I:20 J \"S J=1\" L -I W \"j54\",!", "j54\n", "J54");
    // J55
    chk(m, "L +K L +L J \"S M=1\" TSTART S N=1 TROLLBACK L -K L -L W \"j55\",!", "j55\n", "J55");

    // W51: full-string literals
    chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W51a"); // exact
    chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W51b"); // shorter subject
    chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W51c"); // longer subject — full match fails

    // W52: 0-count is empty-only
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W52a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W52b"); // 0 occurrences leaves "a" unmatched
    chk(m, "W (\"\"?0A),!", "1\n", "W52c");
    chk(m, "W (\"a\"?0A),!", "0\n", "W52d");

    // W53: exact counts
    chk(m, "W (\"ab\"?2A),!", "1\n", "W53a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W53b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W53c");
    chk(m, "W (\"1\"?2N),!", "0\n", "W53d");

    // W54: ranges .m / n. / .
    chk(m, "W (\"a\"?.1A),!", "1\n", "W54a"); // 0..1 A, "a" fits
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W54b"); // 0..1 A cannot cover 2 letters
    chk(m, "W (\"aa\"?2.A),!", "1\n", "W54c"); // 2+ A
    chk(m, "W (\"a\"?2.A),!", "0\n", "W54d");
    chk(m, "W (\"\"?.A),!", "1\n", "W54e");
    chk(m, "W (\"xy\"?.A),!", "1\n", "W54f");

    // W55: mixed class + literal
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W55a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W55b"); // missing N
    chk(m, "W (\"a\"?1\"a\"),!", "1\n", "W55c");
    chk(m, "W (\"b\"?1\"a\"),!", "0\n", "W55d");

    System.out.println("J51–J55 + W51–W55 OK cases=" + n);
    st.close();
  }
}
