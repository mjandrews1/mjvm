package jvmumps;
import java.io.File;

/** J46–J50 jobs + W46–W50 patterns (M semantics). */
public final class J46W50Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw46", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        // J46: JOB + lock + TX rollback
        chk(m, "L +A TSTART S B=1 TROLLBACK J \"S C=1\" L -A W \"j46\",!", "j46\n", "J46");
        // J47: nested JOB + commit
        chk(m, "J \"S D=1\" L +E TSTART S F=1 TCOMMIT L -E W \"j47\",!", "j47\n", "J47");
        // J48: rollback + JOB + lock
        chk(m, "TSTART S G=1 TROLLBACK J \"S H=1\" L +I L -I W \"j48\",!", "j48\n", "J48");
        // J49: two JOB + lock timeout
        chk(m, "L +J:15 J \"S K=1\" J \"S L=1\" L -J W \"j49\",!", "j49\n", "J49");
        // J50: JOB + nested lock + commit
        chk(m, "L +M L +N J \"S O=1\" TSTART S P=1 TCOMMIT L -M L -N W \"j50\",!", "j50\n", "J50");

        // W46: literals
        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W46a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W46b");
        chk(m, "W (\"\"?0\"z\"),!", "1\n", "W46c");
        chk(m, "W (\"z\"?1\"z\"),!", "1\n", "W46d");

        // W47: classes
        chk(m, "W (\"A\"?1A),!", "1\n", "W47a");
        chk(m, "W (\"9\"?1N),!", "1\n", "W47b");
        chk(m, "W (\"?\"?1A),!", "0\n", "W47c");
        chk(m, "W (\"a\"?1A1A),!", "0\n", "W47d");

        // W48: counts
        chk(m, "W (\"aaaa\"?4A),!", "1\n", "W48a");
        chk(m, "W (\"aaa\"?4A),!", "0\n", "W48b");
        chk(m, "W (\"\"?.A),!", "1\n", "W48c");
        chk(m, "W (\"x\"?.A),!", "1\n", "W48d");

        // W49: mixed
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W49a");
        chk(m, "W (\"1a\"?1N1A),!", "1\n", "W49b");
        chk(m, "W (\"xy\"?1A1A),!", "1\n", "W49c");
        chk(m, "W (\"x\"?1A1A),!", "0\n", "W49d");

        // W50: optional
        chk(m, "W (\"a\"?.1A),!", "1\n", "W50a");
        chk(m, "W (\"\"?.1A),!", "1\n", "W50b");
        chk(m, "W (\"1\"?0N1N),!", "1\n", "W50c");
        chk(m, "W (\"\"?0N),!", "1\n", "W50d");
        chk(m, "W (\"1\"?0N),!", "0\n", "W50e");

        System.out.println("J46–J50 + W46–W50 OK cases="+n);
        st.close();
    }
}
