package jvmumps;
import java.io.File;

/** J281–J285 jobs + W281–W285 patterns (M semantics). */
public final class J281W285Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw281", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:10 J \"S B=1\" TSTART S C=1 TCOMMIT L -A W \"j281\",!", "j281\n", "J281");
        chk(m, "J \"S D=1\" TSTART S E=1 TROLLBACK W \"j282\",!", "j282\n", "J282");
        chk(m, "L +F L +G J \"S H=1\" TSTART S I=1 TCOMMIT L -F L -G W \"j283\",!", "j283\n", "J283");
        chk(m, "TSTART S J=1 TROLLBACK J \"S K=1\" W \"j284\",!", "j284\n", "J284");
        chk(m, "L +L:15 J \"S M=1\" TSTART S N=1 TCOMMIT L -L W \"j285\",!", "j285\n", "J285");

        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W281a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W281b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W281c");
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W282a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W282b");
        chk(m, "W (\"\"?0A),!", "1\n", "W282c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W282d");
        chk(m, "W (\"aa\"?2A),!", "1\n", "W283a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W283b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W283c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W283d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W284a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W284b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W284c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W284d");
        chk(m, "W (\"\"?.A),!", "1\n", "W284e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W285a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W285b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W285c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W285d");

        System.out.println("J281–J285 + W281–W285 OK cases="+n);
        st.close();
    }
}
