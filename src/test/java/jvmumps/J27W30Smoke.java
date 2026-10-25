package jvmumps;

import java.io.File;
import java.util.*;

/** J27–J30 jobs + W27–W30 patterns (higher J##/W##). */
public final class J27W30Smoke {
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

  public static void main(String[] args) throws Exception {
    File db = File.createTempFile("jw27", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());

    // J27: nested JOB + parent lock + TX rollback
    LineInterp m = new LineInterp(st);
    chk(m, "L +A TSTART S X=1 J \"S Y=1\" TROLLBACK L -A W \"j27\",!", "j27\n", "J27");

    // J28: two JOBs + shared store lock contention
    chk(m, "L +B J \"S P=1\" J \"S P=2\" L -B W \"j28\",!", "j28\n", "J28");

    // J29: TSTART nested-ish + JOB after commit
    chk(m, "TSTART S Z=1 TCOMMIT J \"S Z=2\" W \"j29\",!", "j29\n", "J29");

    // J30: lock timeout + JOB + release
    chk(m, "L +C:50 J \"W 1\" L -C W \"j30\",!", "j30\n", "J30");

    // W27: literals, classes, counts
    chk(m, "W (\"hello\"?1\"he\"1\"llo\"),!", "1\n", "W27a");
    chk(m, "W (\"a1b\"?1A1N1A),!", "1\n", "W27b");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W27c");
    chk(m, "W (\"a\"?2A),!", "0\n", "W27d");
    chk(m, "W (\"\"?0A0N),!", "1\n", "W27e");

    // W28: alternation-like / multi-count
    chk(m, "W (\"aaa\"?.A),!", "1\n", "W28a");
    chk(m, "W (\"aaa\"?3A),!", "1\n", "W28b");
    chk(m, "W (\"aa\"?3A),!", "0\n", "W28c");
    chk(m, "W (\"xyz\"?1A1A1A),!", "1\n", "W28d");
    chk(m, "W (\"xy\"?1A1A1A),!", "0\n", "W28e");

    // W29: mixed literals + classes
    chk(m, "W (\"a1\"?1\"a\"1N),!", "1\n", "W29a");
    chk(m, "W (\"1a\"?1N1\"a\"),!", "1\n", "W29b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W29c");
    chk(m, "W (\"x\"?1\"y\"),!", "0\n", "W29d");

    // W30: optional / zero-or-more
    chk(m, "W (\"ab\"?.A.1A),!", "1\n", "W30a");
    chk(m, "W (\"a\"?.A),!", "1\n", "W30b");
    chk(m, "W (\"\"?.A),!", "1\n", "W30c");
    chk(m, "W (\"1\"?0N1N),!", "1\n", "W30d");
    chk(m, "W (\"1\"?0N),!", "0\n", "W30e");

    System.out.println("J27–J30 + W27–W30 OK cases=" + n);
    st.close();
  }
}
