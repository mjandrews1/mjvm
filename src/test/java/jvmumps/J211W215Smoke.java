package jvmumps;
import java.io.File;

/** J211–J215 jobs + W211–W215 patterns (M semantics). */
public final class J211W215Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw211", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j211\",!", "j211\n", "J211");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j212\",!", "j212\n", "J212");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j213\",!", "j213\n", "J213");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j214\",!", "j214\n", "J214");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j215\",!", "j215\n", "J215");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W211a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W211b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W211c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W212a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W212b");
        chk(m, "W (\"\"?0A),!", "1\n", "W212c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W212d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W213a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W213b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W213c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W213d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W214a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W214b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W214c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W214d");
        chk(m, "W (\"\"?.A),!", "1\n", "W214e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W215a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W215b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W215c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W215d");

        System.out.println("J211–J215 + W211–W215 OK cases="+n);
        st.close();
    }
}
