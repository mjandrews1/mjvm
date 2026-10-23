package jvmumps;
import java.io.File;

/** J296–J300 jobs + W296–W300 patterns (M semantics). */
public final class J296W300Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw296", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j296\",!", "j296\n", "J296");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j297\",!", "j297\n", "J297");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j298\",!", "j298\n", "J298");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j299\",!", "j299\n", "J299");
        chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j300\",!", "j300\n", "J300");

        chk(m, "W (\"xyz\"?3A),!", "1\n", "W296a");
        chk(m, "W (\"xy\"?3A),!", "0\n", "W296b");
        chk(m, "W (\"xyZ\"?3A),!", "1\n", "W296c");
        chk(m, "W (\"\"?.3N),!", "1\n", "W297a");
        chk(m, "W (\"123\"?.3N),!", "1\n", "W297b");
        chk(m, "W (\"1234\"?.3N),!", "0\n", "W297c");
        chk(m, "W (\"ab\"?2.2A),!", "1\n", "W298a");
        chk(m, "W (\"a\"?2.2A),!", "0\n", "W298b");
        chk(m, "W (\"abc\"?2.2A),!", "0\n", "W298c");
        chk(m, "W (\"1\"?1.N),!", "1\n", "W299a");
        chk(m, "W (\"12345\"?1.N),!", "1\n", "W299b");
        chk(m, "W (\"\"?1.N),!", "0\n", "W299c");
        chk(m, "W (\"\"?0.1N),!", "1\n", "W300a");
        chk(m, "W (\"9\"?0.1N),!", "1\n", "W300b");
        chk(m, "W (\"99\"?0.1N),!", "0\n", "W300c");

        System.out.println("J296–J300 + W296–W300 OK cases="+n);
        st.close();
    }
}
