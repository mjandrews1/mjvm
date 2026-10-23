package jvmumps;
import java.io.File;

/** J121–J125 jobs + W121–W125 patterns (M semantics). */
public final class J121W125Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw121", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j121\",!", "j121\n", "J121");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j122\",!", "j122\n", "J122");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j123\",!", "j123\n", "J123");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j124\",!", "j124\n", "J124");
        chk(m, "L +L:20 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j125\",!", "j125\n", "J125");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W121a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W121b");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W122a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W122b");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W123a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W123b");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W124a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W124b");
        chk(m, "W (\"\"?.A),!", "1\n", "W124c");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W125a");
        chk(m, "W (\"a\"?1A1N),!", "0\n", "W125b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W125c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W125d");

        System.out.println("J121–J125 + W121–W125 OK cases="+n);
        st.close();
    }
}
