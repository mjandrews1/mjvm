package jvmumps;
import java.io.File;

/** J191–J195 jobs + W191–W195 patterns (M semantics). */
public final class J191W195Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw191", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j191\",!", "j191\n", "J191");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j192\",!", "j192\n", "J192");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j193\",!", "j193\n", "J193");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j194\",!", "j194\n", "J194");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j195\",!", "j195\n", "J195");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W191a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W191b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W191c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W192a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W192b");
        chk(m, "W (\"\"?0A),!", "1\n", "W192c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W192d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W193a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W193b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W193c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W193d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W194a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W194b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W194c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W194d");
        chk(m, "W (\"\"?.A),!", "1\n", "W194e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W195a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W195b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W195c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W195d");

        System.out.println("J191–J195 + W191–W195 OK cases="+n);
        st.close();
    }
}
