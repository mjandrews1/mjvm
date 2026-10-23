package jvmumps;
import java.io.File;

/** J221–J225 jobs + W221–W225 patterns (M semantics). */
public final class J221W225Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw221", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j221\",!", "j221\n", "J221");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j222\",!", "j222\n", "J222");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j223\",!", "j223\n", "J223");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j224\",!", "j224\n", "J224");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j225\",!", "j225\n", "J225");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W221a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W221b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W221c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W222a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W222b");
        chk(m, "W (\"\"?0A),!", "1\n", "W222c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W222d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W223a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W223b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W223c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W223d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W224a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W224b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W224c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W224d");
        chk(m, "W (\"\"?.A),!", "1\n", "W224e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W225a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W225b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W225c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W225d");

        System.out.println("J221–J225 + W221–W225 OK cases="+n);
        st.close();
    }
}
