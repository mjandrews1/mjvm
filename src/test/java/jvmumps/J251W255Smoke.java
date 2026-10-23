package jvmumps;
import java.io.File;

/** J251–J255 jobs + W251–W255 patterns (M semantics). */
public final class J251W255Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw251", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j251\",!", "j251\n", "J251");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j252\",!", "j252\n", "J252");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j253\",!", "j253\n", "J253");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j254\",!", "j254\n", "J254");
        chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j255\",!", "j255\n", "J255");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W251a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W251b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W251c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W252a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W252b");
        chk(m, "W (\"\"?0N),!", "1\n", "W252c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W252d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W253a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W253b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W253c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W253d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W254a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W254b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W254c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W254d");
        chk(m, "W (\"\"?.A),!", "1\n", "W254e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W255a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W255b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W255c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W255d");

        System.out.println("J251–J255 + W251–W255 OK cases="+n);
        st.close();
    }
}
