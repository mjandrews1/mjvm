package jvmumps;
import java.io.File;

/** J136–J140 jobs + W136–W140 patterns (M semantics). */
public final class J136W140Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw136", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j136\",!", "j136\n", "J136");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j137\",!", "j137\n", "J137");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j138\",!", "j138\n", "J138");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j139\",!", "j139\n", "J139");
        chk(m, "L +M:30 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j140\",!", "j140\n", "J140");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W136a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W136b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W136c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W137a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W137b");
        chk(m, "W (\"\"?0N),!", "1\n", "W137c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W137d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W138a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W138b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W138c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W138d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W139a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W139b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W139c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W139d");
        chk(m, "W (\"\"?.A),!", "1\n", "W139e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W140a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W140b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W140c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W140d");

        System.out.println("J136–J140 + W136–W140 OK cases="+n);
        st.close();
    }
}
