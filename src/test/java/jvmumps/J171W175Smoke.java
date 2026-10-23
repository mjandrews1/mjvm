package jvmumps;
import java.io.File;

/** J171–J175 jobs + W171–W175 patterns (M semantics). */
public final class J171W175Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw171", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j171\",!", "j171\n", "J171");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j172\",!", "j172\n", "J172");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j173\",!", "j173\n", "J173");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j174\",!", "j174\n", "J174");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j175\",!", "j175\n", "J175");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W171a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W171b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W171c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W172a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W172b");
        chk(m, "W (\"\"?0A),!", "1\n", "W172c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W172d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W173a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W173b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W173c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W173d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W174a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W174b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W174c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W174d");
        chk(m, "W (\"\"?.A),!", "1\n", "W174e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W175a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W175b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W175c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W175d");

        System.out.println("J171–J175 + W171–W175 OK cases="+n);
        st.close();
    }
}
