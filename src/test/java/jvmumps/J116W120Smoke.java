package jvmumps;
import java.io.File;

/** J116–J120 jobs + W116–W120 patterns (M semantics). */
public final class J116W120Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw116", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j116\",!", "j116\n", "J116");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j117\",!", "j117\n", "J117");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j118\",!", "j118\n", "J118");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j119\",!", "j119\n", "J119");
        chk(m, "L +M:15 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j120\",!", "j120\n", "J120");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W116a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W116b");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W117a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W117b");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W118a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W118b");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W119a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W119b");
        chk(m, "W (\"\"?.A),!", "1\n", "W119c");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W120a");
        chk(m, "W (\"a\"?1A1N),!", "0\n", "W120b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W120c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W120d");

        System.out.println("J116–J120 + W116–W120 OK cases="+n);
        st.close();
    }
}
