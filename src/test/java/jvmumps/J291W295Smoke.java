package jvmumps;
import java.io.File;

/** J291–J295 jobs + W291–W295 patterns (M semantics). */
public final class J291W295Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw291", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j291\",!", "j291\n", "J291");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j292\",!", "j292\n", "J292");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j293\",!", "j293\n", "J293");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j294\",!", "j294\n", "J294");
        chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j295\",!", "j295\n", "J295");

        chk(m, "W (\"ab\"?1\"a\"1\"b\"),!", "1\n", "W291a");
        chk(m, "W (\"a\"?1\"a\"1\"b\"),!", "0\n", "W291b");
        chk(m, "W (\"abc\"?1\"a\"1\"b\"),!", "0\n", "W291c");
        chk(m, "W (\"\"?0\"xy\"),!", "1\n", "W292a");
        chk(m, "W (\"x\"?0\"xy\"),!", "0\n", "W292b");
        chk(m, "W (\"xy\"?0\"xy\"),!", "0\n", "W292c");
        chk(m, "W (\"a\"?1.2A),!", "1\n", "W293a");
        chk(m, "W (\"ab\"?1.2A),!", "1\n", "W293b");
        chk(m, "W (\"abc\"?1.2A),!", "0\n", "W293c");
        chk(m, "W (\"a\"?1.A),!", "1\n", "W294a");
        chk(m, "W (\"aaa\"?1.A),!", "1\n", "W294b");
        chk(m, "W (\"\"?1.A),!", "0\n", "W294c");
        chk(m, "W (\"1\"?1.A),!", "0\n", "W294d");
        chk(m, "W (\"A1x\"?1A1N1\"x\"),!", "1\n", "W295a");
        chk(m, "W (\"A1y\"?1A1N1\"x\"),!", "0\n", "W295b");
        chk(m, "W (\"A1xyz\"?1A1N1\"x\"),!", "0\n", "W295c");

        System.out.println("J291–J295 + W291–W295 OK cases="+n);
        st.close();
    }
}
