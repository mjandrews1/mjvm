package jvmumps;
import java.io.File;

/** J36–J40 jobs + W36–W40 patterns (M semantics). */
public final class J36W40Smoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag) {
        String got = m.run(code);
        if (!want.equals(got)) throw new IllegalStateException(tag+" "+code+" got="+q(got)+" want="+q(want));
        n++;
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jw36", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        // J36: lock + JOB + rollback
        chk(m, "L +P TSTART S Q=1 TROLLBACK J \"S R=1\" L -P W \"j36\",!", "j36\n", "J36");
        // J37: JOB + nested lock + commit
        chk(m, "L +A L +B J \"S ^X=1\" TSTART S Y=1 TCOMMIT L -A L -B W \"j37\",!", "j37\n", "J37");
        // J38: rollback + JOB after
        chk(m, "TSTART S Z=1 TROLLBACK J \"S Z=2\" W \"j38\",!", "j38\n", "J38");
        // J39: JOB + lock timeout
        chk(m, "L +M:10 J \"S N=1\" L -M W \"j39\",!", "j39\n", "J39");
        // J40: two jobs + commit
        chk(m, "J \"S O=1\" J \"S P=1\" TSTART S Q=1 TCOMMIT W \"j40\",!", "j40\n", "J40");

        // W36: literals
        chk(m, "W (\"abc\"?1\"abc\"),!", "1\n", "W36a");
        chk(m, "W (\"ab\"?1\"abc\"),!", "0\n", "W36b");
        chk(m, "W (\"\"?0\"q\"),!", "1\n", "W36c");
        chk(m, "W (\"q\"?1\"q\"),!", "1\n", "W36d");

        // W37: classes
        chk(m, "W (\"A\"?1A),!", "1\n", "W37a");
        chk(m, "W (\"1\"?1N),!", "1\n", "W37b");
        chk(m, "W (\"!\"?1A),!", "0\n", "W37c");
        chk(m, "W (\"a\"?1N),!", "0\n", "W37d");

        // W38: counts
        chk(m, "W (\"aa\"?2A),!", "1\n", "W38a");
        chk(m, "W (\"a\"?2A),!", "0\n", "W38b");
        chk(m, "W (\"aaa\"?.A),!", "1\n", "W38c");
        chk(m, "W (\"\"?.A),!", "1\n", "W38d");

        // W39: mixed literals + classes
        chk(m, "W (\"a1\"?1\"a\"1N),!", "1\n", "W39a");
        chk(m, "W (\"1a\"?1N1\"a\"),!", "1\n", "W39b");
        chk(m, "W (\"xy\"?1A1A),!", "1\n", "W39c");
        chk(m, "W (\"x\"?1A1A),!", "0\n", "W39d");

        // W40: optional
        chk(m, "W (\"a\"?.A),!", "1\n", "W40a");
        chk(m, "W (\"\"?.A),!", "1\n", "W40b");
        chk(m, "W (\"1\"?0N1N),!", "1\n", "W40c");
        chk(m, "W (\"\"?0N),!", "1\n", "W40d");
        chk(m, "W (\"1\"?0N),!", "0\n", "W40e");

        System.out.println("J36–J40 + W36–W40 OK cases="+n);
        st.close();
    }
}
