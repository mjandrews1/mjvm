package jvmumps;

/** J9 JOB/tx + W9 patterns + huge $PIECE. */
public final class J9W9Smoke {
    public static void main(String[] args) {
        // huge $PIECE
        LineInterp m = new LineInterp();
        String out = m.run("S s=\"a\" F i=1:1:2000 S s=s_\",y\"\nW $P(s,\",\",1000),!");
        if (!"y\n".equals(out)) {
            throw new IllegalStateException("piece got " + out);
        }
        out = m.run("S s=\"x\" F i=1:1:500 S s=s_\"|z\"\nW $P(s,\"|\",300),!");
        if (!"z\n".equals(out)) {
            throw new IllegalStateException("piece2 " + out);
        }
        // LOCK + TSTART rollback
        m = new LineInterp();
        out = m.run("S X=1 TSTART S X=2 TROLLBACK W X,!");
        if (!"1\n".equals(out)) {
            throw new IllegalStateException("rollback " + out);
        }
        out = m.run("S X=1 TSTART S X=2 TCOMMIT W X,!");
        if (!"2\n".equals(out)) {
            throw new IllegalStateException("commit " + out);
        }
        out = m.run("L +X W \"got\",! L -X W \"off\",!");
        if (!"got\noff\n".equals(out)) {
            throw new IllegalStateException("lock " + out);
        }
        out = m.run("J \"S Z=1\" W \"after\",!");
        if (!"after\n".equals(out)) {
            throw new IllegalStateException("job " + out);
        }
        // W9 patterns
        out = m.run("W (\"a\"?1A),\" \",(\"1\"?1N),\" \",(\"hello\"?1\"hello\"),!");
        if (!"1 1 1\n".equals(out)) {
            throw new IllegalStateException("pattern " + out);
        }
        System.out.println("J9 JOB/TX + W9 PATTERNS OK");
    }
}
