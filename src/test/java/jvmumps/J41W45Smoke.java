package jvmumps;
import java.io.File;

/** J41–J45 jobs + W41–W45 patterns (M semantics). */
public final class J41W45Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw41", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        // J41: JOB + TSTART/TCOMMIT + lock
        chk(m, "L +A:5 TSTART S B=1 TCOMMIT J \"S C=1\" L -A W \"j41\",!", "j41\n", "J41");
        // J42: nested JOB with shared store
        chk(m, "J \"S D=1\" J \"S E=1\" TSTART S F=1 TROLLBACK W \"j42\",!", "j42\n", "J42");
        // J43: lock + rollback + JOB
        chk(m, "L +G TSTART S H=1 TROLLBACK J \"S I=1\" L -G W \"j43\",!", "j43\n", "J43");
        // J44: two locks + JOB + commit
        chk(m, "L +K L +L J \"S M=1\" TSTART S N=1 TCOMMIT L -K L -L W \"j44\",!", "j44\n", "J44");
        // J45: JOB + timeout lock
        chk(m, "L +O:20 J \"S P=1\" L -O W \"j45\",!", "j45\n", "J45");

        // W41: literals
        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W41a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W41b");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W41c");

        // W42: classes
        chk(m, "W (\"a\"?1A),!", "1\n", "W42a");
        chk(m, "W (\"1\"?1N),!", "1\n", "W42b");
        chk(m, "W (\"!\"?1N),!", "0\n", "W42c");
        chk(m, "W (\"a\"?1A1A),!", "0\n", "W42d");

        // W43: counts
        chk(m, "W (\"aaa\"?3A),!", "1\n", "W43a");
        chk(m, "W (\"aa\"?3A),!", "0\n", "W43b");
        chk(m, "W (\"\"?.A),!", "1\n", "W43c");
        chk(m, "W (\"x\"?.A),!", "1\n", "W43d");

        // W44: mixed
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W44a");
        chk(m, "W (\"1a\"?1N1A),!", "1\n", "W44b");
        chk(m, "W (\"aa\"?1A1A),!", "1\n", "W44c");
        chk(m, "W (\"a\"?1A1A),!", "0\n", "W44d");

        // W45: optional
        chk(m, "W (\"a\"?.1A),!", "1\n", "W45a");
        chk(m, "W (\"\"?.1A),!", "1\n", "W45b");
        chk(m, "W (\"1\"?0N1N),!", "1\n", "W45c");
        chk(m, "W (\"\"?0N1N),!", "0\n", "W45d");

        System.out.println("J41–J45 + W41–W45 OK cases="+n);
        st.close();
    }
}
