package jvmumps;
import java.io.File;

/** J256–J260 jobs + W256–W260 patterns (M semantics). */
public final class J256W260Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw256", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j256\",!", "j256\n", "J256");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j257\",!", "j257\n", "J257");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j258\",!", "j258\n", "J258");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j259\",!", "j259\n", "J259");
        chk(m, "L +M:15 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j260\",!", "j260\n", "J260");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W256a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W256b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W256c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W257a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W257b");
        chk(m, "W (\"\"?0N),!", "1\n", "W257c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W257d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W258a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W258b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W258c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W258d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W259a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W259b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W259c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W259d");
        chk(m, "W (\"\"?.A),!", "1\n", "W259e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W260a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W260b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W260c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W260d");

        System.out.println("J256–J260 + W256–W260 OK cases="+n);
        st.close();
    }
}
