package jvmumps;
import java.io.File;

/** J166–J170 jobs + W166–W170 patterns (M semantics). */
public final class J166W170Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw166", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        chk(m, "L +A J \"S B=1\" TSTART S C=1 TROLLBACK L -A W \"j166\",!", "j166\n", "J166");
        chk(m, "J \"S D=1\" TSTART S E=1 TCOMMIT L +F L -F W \"j167\",!", "j167\n", "J167");
        chk(m, "L +G L +H TSTART S I=1 TCOMMIT J \"S J=1\" L -G L -H W \"j168\",!", "j168\n", "J168");
        chk(m, "TSTART S K=1 TROLLBACK J \"S L=1\" W \"j169\",!", "j169\n", "J169");
        chk(m, "L +M:25 J \"S N=1\" TSTART S O=1 TCOMMIT L -M W \"j170\",!", "j170\n", "J170");

        chk(m, "W (\"xy\"?1\"xy\"),!", "1\n", "W166a");
        chk(m, "W (\"x\"?1\"xy\"),!", "0\n", "W166b");
        chk(m, "W (\"xyz\"?1\"xy\"),!", "0\n", "W166c");
        chk(m, "W (\"\"?0\"x\"),!", "1\n", "W167a");
        chk(m, "W (\"x\"?0\"x\"),!", "0\n", "W167b");
        chk(m, "W (\"\"?0N),!", "1\n", "W167c");
        chk(m, "W (\"5\"?0N),!", "0\n", "W167d");
        chk(m, "W (\"ab\"?2A),!", "1\n", "W168a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W168b");
        chk(m, "W (\"12\"?2N),!", "1\n", "W168c");
        chk(m, "W (\"1\"?2N),!", "0\n", "W168d");
        chk(m, "W (\"a\"?.1A),!", "1\n", "W169a");
        chk(m, "W (\"aa\"?.1A),!", "0\n", "W169b");
        chk(m, "W (\"aaa\"?2.A),!", "1\n", "W169c");
        chk(m, "W (\"a\"?2.A),!", "0\n", "W169d");
        chk(m, "W (\"\"?.A),!", "1\n", "W169e");
        chk(m, "W (\"a1\"?1A1N),!", "1\n", "W170a");
        chk(m, "W (\"1\"?1A1N),!", "0\n", "W170b");
        chk(m, "W (\"x\"?1\"x\"),!", "1\n", "W170c");
        chk(m, "W (\"y\"?1\"x\"),!", "0\n", "W170d");

        System.out.println("J166–J170 + W166–W170 OK cases="+n);
        st.close();
    }
}
