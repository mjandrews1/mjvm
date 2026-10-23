package jvmumps;
import java.io.File;

/** J271–J275 jobs + W271–W275 patterns (M semantics). */
public final class J271W275Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw271", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j271\",!", "j271\n", "J271");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j272\",!", "j272\n", "J272");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j273\",!", "j273\n", "J273");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j274\",!", "j274\n", "J274");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j275\",!", "j275\n", "J275");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W271a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W271b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W271c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W272a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W272b");
        chk(m, "W (\"\"?0A),!", "1\n", "W272c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W272d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W273a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W273b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W273c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W273d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W274a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W274b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W274c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W274d");
        chk(m, "W (\"\"?.A),!", "1\n", "W274e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W275a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W275b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W275c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W275d");

        System.out.println("J271–J275 + W271–W275 OK cases="+n);
        st.close();
    }
}
