package jvmumps;
import java.io.File;

/**
 * J101–J105 jobs + W101–W105 patterns.
 * Expected values are M/ISO semantics. On mismatch, fix the engine.
 */
public final class J101W105Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw101", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        // J101–J105: JOB + LOCK + TX
        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j101\",!", "j101\n", "J101");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j102\",!", "j102\n", "J102");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j103\",!", "j103\n", "J103");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j104\",!", "j104\n", "J104");
        chk(m, "L +L:20 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j105\",!", "j105\n", "J105");

        // W101: full-string literals
        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W101a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W101b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W101c");

        // W102: 0-count = empty only
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W102a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W102b");
        chk(m, "W (\"\"?0A),!", "1\n", "W102c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W102d");

        // W103: exact counts
        chk(m, "W (\"aa\"?2A),!", "1\n", "W103a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W103b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W103c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W103d");

        // W104: ranges
        chk(m, "W (\"a\"?.1A),!", "1\n", "W104a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W104b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W104c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W104d");
        chk(m, "W (\"\"?.A),!", "1\n", "W104e");

        // W105: mixed
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W105a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W105b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W105c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W105d");

        System.out.println("J101–J105 + W101–W105 OK cases="+n);
        st.close();
    }
}
