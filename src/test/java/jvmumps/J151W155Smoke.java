package jvmumps;
import java.io.File;

/** J151–J155 jobs + W151–W155 patterns (M semantics). */
public final class J151W155Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw151", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:8 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j151\",!", "j151\n", "J151");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j152\",!", "j152\n", "J152");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j153\",!", "j153\n", "J153");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j154\",!", "j154\n", "J154");
        chk(m, "L +L:12 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j155\",!", "j155\n", "J155");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W151a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W151b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W151c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W152a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W152b");
        chk(m, "W (\"\"?0A),!", "1\n", "W152c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W152d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W153a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W153b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W153c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W153d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W154a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W154b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W154c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W154d");
        chk(m, "W (\"\"?.A),!", "1\n", "W154e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W155a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W155b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W155c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W155d");

        System.out.println("J151–J155 + W151–W155 OK cases="+n);
        st.close();
    }
}
