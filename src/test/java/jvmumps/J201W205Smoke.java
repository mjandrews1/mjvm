package jvmumps;
import java.io.File;

/** J201–J205 jobs + W201–W205 patterns (M semantics). */
public final class J201W205Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw201", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j201\",!", "j201\n", "J201");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j202\",!", "j202\n", "J202");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j203\",!", "j203\n", "J203");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j204\",!", "j204\n", "J204");
        chk(m, "L +L:15 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j205\",!", "j205\n", "J205");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W201a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W201b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W201c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W202a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W202b");
        chk(m, "W (\"\"?0A),!", "1\n", "W202c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W202d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W203a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W203b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W203c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W203d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W204a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W204b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W204c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W204d");
        chk(m, "W (\"\"?.A),!", "1\n", "W204e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W205a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W205b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W205c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W205d");

        System.out.println("J201–J205 + W201–W205 OK cases="+n);
        st.close();
    }
}
