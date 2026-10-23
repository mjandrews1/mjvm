package jvmumps;
import java.io.File;

/** J91–J95 jobs + W91–W95 patterns (M semantics). */
public final class J91W95Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw91", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:15 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j91\",!", "j91\n", "J91");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK L +F L -F W \"j92\",!", "j92\n", "J92");
        chk(m, "L +G L +H J \"S I=1\" TSTART S J=1 TCOMMIT L -G L -H W \"j93\",!", "j93\n", "J93");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j94\",!", "j94\n", "J94");
        chk(m, "L +M:25 J \"S N=1\" L -M W \"j95\",!", "j95\n", "J95");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W91a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W91b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W91c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W92a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W92b");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W93a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W93b");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W94a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W94b");
        chk(m, "W (\"aa\"?2.A),!", "1\n", "W94c");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W95a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W95b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W95c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W95d");

        System.out.println("J91–J95 + W91–W95 OK cases="+n);
        st.close();
    }
}
