package jvmumps;
import java.io.File;

/** J71–J75 jobs + W71–W75 patterns (M semantics). */
public final class J71W75Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw71", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A:5 J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j71\",!", "j71\n", "J71");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j72\",!", "j72\n", "J72");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j73\",!", "j73\n", "J73");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j74\",!", "j74\n", "J74");
        chk(m, "L +M:30 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j75\",!", "j75\n", "J75");

        // W71: literal
        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W71a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W71b");
        chk(m, "W (\"abcd\"?1\"abc\"),!", "0\n", "W71c");

        // W72: 0-count
        chk(m, "W (\"\"?0\"a\"),!", "1\n", "W72a");
        chk(m, "W (\"a\"?0\"a\"),!", "0\n", "W72b");
        chk(m, "W (\"\"?0A),!", "1\n", "W72c");
        chk(m, "W (\"a\"?0A),!", "0\n", "W72d");

        // W73: counts
        chk(m, "W (\"aa\"?2A),!", "1\n", "W73a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W73b");
        chk(m, "W (\"11\"?2N),!", "1\n", "W73c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W73d");

        // W74: ranges
        chk(m, "W (\"a\"?.1A),!", "1\n", "W74a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W74b");
        chk(m, "W (\"aa\"?2.A),!", "1\n", "W74c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W74d");
        chk(m, "W (\"\"?.A),!", "1\n", "W74e");

        // W75: mixed
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W75a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W75b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W75c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W75d");

        System.out.println("J71–J75 + W71–W75 OK cases="+n);
        st.close();
    }
}
