package jvmumps;
import java.io.File;

/** J141–J145 jobs + W141–W145 patterns (M semantics). */
public final class J141W145Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw141", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j141\",!", "j141\n", "J141");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j142\",!", "j142\n", "J142");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j143\",!", "j143\n", "J143");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j144\",!", "j144\n", "J144");
        chk(m, "L +L:20 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j145\",!", "j145\n", "J145");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W141a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W141b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W141c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W142a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W142b");
        chk(m, "W (\"\"?0A),!", "1\n", "W142c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W142d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W143a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W143b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W143c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W143d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W144a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W144b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W144c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W144d");
        chk(m, "W (\"\"?.A),!", "1\n", "W144e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W145a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W145b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W145c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W145d");

        System.out.println("J141–J145 + W141–W145 OK cases="+n);
        st.close();
    }
}
