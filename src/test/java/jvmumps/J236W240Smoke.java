package jvmumps;
import java.io.File;

/** J236–J240 jobs + W236–W240 patterns (M semantics). */
public final class J236W240Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw236", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j236\",!", "j236\n", "J236");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j237\",!", "j237\n", "J237");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j238\",!", "j238\n", "J238");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j239\",!", "j239\n", "J239");
        chk(m, "L +M:20 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j240\",!", "j240\n", "J240");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W236a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W236b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W236c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W237a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W237b");
        chk(m, "W (\"\"?0N),!", "1\n", "W237c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W237d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W238a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W238b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W238c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W238d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W239a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W239b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W239c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W239d");
        chk(m, "W (\"\"?.A),!", "1\n", "W239e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W240a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W240b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W240c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W240d");

        System.out.println("J236–J240 + W236–W240 OK cases="+n);
        st.close();
    }
}
