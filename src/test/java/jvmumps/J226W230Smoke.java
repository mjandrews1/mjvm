package jvmumps;
import java.io.File;

/** J226–J230 jobs + W226–W230 patterns (M semantics). */
public final class J226W230Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw226", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j226\",!", "j226\n", "J226");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j227\",!", "j227\n", "J227");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j228\",!", "j228\n", "J228");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j229\",!", "j229\n", "J229");
        chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j230\",!", "j230\n", "J230");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W226a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W226b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W226c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W227a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W227b");
        chk(m, "W (\"\"?0N),!", "1\n", "W227c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W227d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W228a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W228b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W228c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W228d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W229a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W229b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W229c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W229d");
        chk(m, "W (\"\"?.A),!", "1\n", "W229e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W230a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W230b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W230c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W230d");

        System.out.println("J226–J230 + W226–W230 OK cases="+n);
        st.close();
    }
}
