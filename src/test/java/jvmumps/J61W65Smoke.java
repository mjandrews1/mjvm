package jvmumps;
import java.io.File;

/** J61–J65 jobs + W61–W65 patterns (M semantics). */
public final class J61W65Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw61", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j61\",!", "j61\n", "J61");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT J \"S F=1\" W \"j62\",!", "j62\n", "J62");
        chk(m, "L +G L +H J \"S I=1\" L -G L -H W \"j63\",!", "j63\n", "J63");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j64\",!", "j64\n", "J64");
        chk(m, "L +L:25 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j65\",!", "j65\n", "J65");

        // W61: literal full-match
        chk(m, "W (\"hello\"?1\"hello\"),!", "1\n", "W61a");
        chk(m, "W (\"hell\"?1\"hello\"),!", "0\n", "W61b");
        chk(m, "W (\"hello!\"?1\"hello\"),!", "0\n", "W61c");

        // W62: 0-count
        chk(m, "W (\"\"?0\"h\"),!", "1\n", "W62a");
        chk(m, "W (\"h\"?0\"h\"),!", "0\n", "W62b");
        chk(m, "W (\"\"?0A),!", "1\n", "W62c");
        chk(m, "W (\"h\"?0A),!", "0\n", "W62d");

        // W63: counts
        chk(m, "W (\"hh\"?2A),!", "1\n", "W63a");
        chk(m, "W (\"h\"?2A),!", "0\n", "W63b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W63c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W63d");

        // W64: ranges
        chk(m, "W (\"h\"?.1A),!", "1\n", "W64a");
        chk(m, "W (\"hh\"?.1A),!", "0\n", "W64b");
        chk(m, "W (\"hhh\"?2.A),!", "1\n", "W64c");
        chk(m, "W (\"h\"?2.A),!", "0\n", "W64d");
        chk(m, "W (\"\"?.A),!", "1\n", "W64e");

        // W65: mixed
        chk(m, "W (\"h1\"?1A1N),!", "1\n", "W65a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W65b");
        chk(m, "W (\"h\"?1\"h\"),!", "1\n", "W65c");
        chk(m, "W (\"z\"?1\"h\"),!", "0\n", "W65d");

        System.out.println("J61–J65 + W61–W65 OK cases="+n);
        st.close();
    }
}
