package jvmumps;

import java.io.File;

/** J81–J85 jobs + W81–W85 patterns (M semantics). */
public final class J81W85Smoke {
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
    File db = File.createTempFile("jw81", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);

    chk(m, "L +A:20 J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j81\",!", "j81\n", "J81");
    chk(m, "J \"S D=1\" L +E TSTART S F=1 TCOMMIT L -E W \"j82\",!", "j82\n", "J82");
    chk(m, "TSTART S G=1 TCOMMIT J \"S H=1\" W \"j83\",!", "j83\n", "J83");
    chk(m, "L +I J \"S J=1\" TSTART S K=1 TROLLBACK L -I W \"j84\",!", "j84\n", "J84");
    chk(m, "L +L L +M J \"S N=1\" TSTART S O=1 TCOMMIT L -L L -M W \"j85\",!", "j85\n", "J85");

    chk(m, "W (\"ab\"?1\"ab\"),!", "1\n", "W81a");
    chk(m, "W (\"a\"?1\"ab\"),!", "0\n", "W81b");
    chk(m, "W (\"\"?0\"a\"),!", "1\n", "W82a");
    chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W82b");
    chk(m, "W (\"ab\"?2A),!", "1\n", "W83a");
    chk(m, "W (\"a\"?2A),!", "0\n", "W83b");
    chk(m, "W (\"a\"?.1A),!", "1\n", "W84a");
    chk(m, "W (\"aa\"?.1A),!", "0\n", "W84b");
    chk(m, "W (\"a1\"?1A1N),!", "1\n", "W85a");
    chk(m, "W (\"a\"?1A1N),!", "0\n", "W85b");
    chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W85c");
    chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W85d");

    System.out.println("J81–J85 + W81–W85 OK cases=" + n);
    st.close();
  }
}
