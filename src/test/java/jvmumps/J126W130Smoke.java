package jvmumps;
import java.io.File;

/** J126–J130 jobs + W126–W130 patterns (M semantics). */
public final class J126W130Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw126", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j126\",!", "j126\n", "J126");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j127\",!", "j127\n", "J127");
        chk(m, "L +G L +H J \"S I=1\" TSTART S J=1 TCOMMIT L -G L -H W \"j128\",!", "j128\n", "J128");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j129\",!", "j129\n", "J129");
        chk(m, "L +M:15 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j130\",!", "j130\n", "J130");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W126a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W126b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W126c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W127a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W127b");
        chk(m, "W (\"\"?0N),!", "1\n", "W127c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W127d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W128a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W128b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W128c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W128d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W129a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W129b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W129c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W129d");
        chk(m, "W (\"\"?.A),!", "1\n", "W129e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W130a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W130b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W130c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W130d");

        System.out.println("J126–J130 + W126–W130 OK cases="+n);
        st.close();
    }
}
