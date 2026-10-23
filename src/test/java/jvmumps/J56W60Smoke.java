package jvmumps;
import java.io.File;

/** J56–J60 jobs + W56–W60 patterns (M semantics). */
public final class J56W60Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw56", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j56\",!", "j56\n", "J56");
        chk(m, "TSTART S D=1 J \"S E=1\" TROLLBACK W \"j57\",!", "j57\n", "J57");
        chk(m, "L +F:30 J \"S G=1\" L -F W \"j58\",!", "j58\n", "J58");
        chk(m, "J \"S H=1\" J \"S I=1\" TSTART S J=1 TCOMMIT W \"j59\",!", "j59\n", "J59");
        chk(m, "L +K L +L TSTART S M=1 TROLLBACK J \"S N=1\" L -K L -L W \"j60\",!", "j60\n", "J60");

        // W56: exact literal full match
        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W56a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W56b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W56c");

        // W57: 0-count = empty only
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W57a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W57b");
        chk(m, "W (\"\"?0N),!", "1\n", "W57c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W57d");

        // W58: exact class counts
        chk(m, "W (\"ab\"?2A),!", "1\n", "W58a");
        chk(m, "W (\"abc\"?2A),!", "0\n", "W58b"); // 3 letters vs 2A
        chk(m, "W (\"123\"?3N),!", "1\n", "W58c");
        chk(m, "W (\"12\"?3N),!", "0\n", "W58d");

        // W59: ranges
        chk(m, "W (\"a\"?.1A),!", "1\n", "W59a");
        chk(m, "W (\"ab\"?.1A),!", "0\n", "W59b");
        chk(m, "W (\"abc\"?2.A),!", "1\n", "W59c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W59d");
        chk(m, "W (\"\"?.A),!", "1\n", "W59e");

        // W60: mixed
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W60a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W60b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W60c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W60d");

        System.out.println("J56–J60 + W56–W60 OK cases="+n);
        st.close();
    }
}
