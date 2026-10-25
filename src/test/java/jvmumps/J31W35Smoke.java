package jvmumps;

import java.io.File;

/** J31–J35 jobs + W31–W35 patterns. */
public final class J31W35Smoke {
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
    File db = File.createTempFile("jw31", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());

    // J31: JOB + nested locks + commit
    LineInterp m = new LineInterp(st);
    chk(m, "L +X L +Y J \"S ^A=1\" TSTART S B=1 TCOMMIT L -X L -Y W \"j31\",!", "j31\n", "J31");

    // J32: rollback after JOB
    chk(m, "TSTART S C=1 J \"S D=1\" TROLLBACK W \"j32\",!", "j32\n", "J32");

    // J33: lock + JOB + rollback + release
    chk(m, "L +E TSTART S F=1 TROLLBACK J \"S G=1\" L -E W \"j33\",!", "j33\n", "J33");

    // J34: multi JOB with TX
    chk(m, "J \"S H=1\" TSTART S I=1 TCOMMIT J \"S J=1\" W \"j34\",!", "j34\n", "J34");

    // J35: lock timeout then JOB
    chk(m, "L +K:5 J \"S K2=1\" L -K W \"j35\",!", "j35\n", "J35");

    // W31: literals + counts
    chk(m, "W (\"abc\"?1\"a\"1\"b\"1\"c\"),!", "1\n", "W31a");
    chk(m, "W (\"a\"?1\"a\"1\"a\"),!", "0\n", "W31b");
    chk(m, "W (\"\"?0\"a\"0\"b\"),!", "1\n", "W31c");
    chk(m, "W (\"xyz\"?3A),!", "1\n", "W31d");

    // W32: class + optional
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W32a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W32b");
    chk(m, "W (\"\"?1A.1N),!", "0\n", "W32c"); // 1A needs a letter
    chk(m, "W (\"x\"?.1A),!", "1\n", "W32d");

    // W33: multi-count
    chk(m, "W (\"aaaa\"?4A),!", "1\n", "W33a");
    chk(m, "W (\"aaa\"?4A),!", "0\n", "W33b");
    chk(m, "W (\"12\"?2N),!", "1\n", "W33c");
    chk(m, "W (\"1\"?0N1N),!", "1\n", "W33d");

    // W34: literals
    chk(m, "W (\"hello\"?1\"hello\"),!", "1\n", "W34a");
    chk(m, "W (\"hell\"?1\"hello\"),!", "0\n", "W34b");
    chk(m, "W (\"\"?0\"z\"),!", "1\n", "W34c");

    // W35: mixed
    chk(m, "W (\"a1b\"?1A1N1A),!", "1\n", "W35a");
    chk(m, "W (\"ab\"?1A1A1A),!", "0\n", "W35b");
    chk(m, "W (\"1\"?1N),!", "1\n", "W35c");
    chk(m, "W (\"a\"?1A),!", "1\n", "W35d");

    System.out.println("J31–J35 + W31–W35 OK cases=" + n);
    st.close();
  }
}
