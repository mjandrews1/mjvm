package jvmumps;
import java.io.File;

/** J186–J190 jobs + W186–W190 patterns (M semantics). */
public final class J186W190Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw186", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j186\",!", "j186\n", "J186");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j187\",!", "j187\n", "J187");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j188\",!", "j188\n", "J188");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j189\",!", "j189\n", "J189");
        chk(m, "L +M:15 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j190\",!", "j190\n", "J190");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W186a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W186b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W186c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W187a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W187b");
        chk(m, "W (\"\"?0N),!", "1\n", "W187c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W187d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W188a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W188b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W188c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W188d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W189a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W189b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W189c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W189d");
        chk(m, "W (\"\"?.A),!", "1\n", "W189e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W190a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W190b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W190c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W190d");

        System.out.println("J186–J190 + W186–W190 OK cases="+n);
        st.close();
    }
}
