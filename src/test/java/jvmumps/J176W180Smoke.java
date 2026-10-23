package jvmumps;
import java.io.File;

/** J176–J180 jobs + W176–W180 patterns (M semantics). */
public final class J176W180Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw176", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j176\",!", "j176\n", "J176");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j177\",!", "j177\n", "J177");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j178\",!", "j178\n", "J178");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j179\",!", "j179\n", "J179");
        chk(m, "L +M:30 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j180\",!", "j180\n", "J180");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W176a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W176b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W176c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W177a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W177b");
        chk(m, "W (\"\"?0N),!", "1\n", "W177c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W177d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W178a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W178b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W178c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W178d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W179a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W179b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W179c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W179d");
        chk(m, "W (\"\"?.A),!", "1\n", "W179e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W180a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W180b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W180c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W180d");

        System.out.println("J176–J180 + W176–W180 OK cases="+n);
        st.close();
    }
}
