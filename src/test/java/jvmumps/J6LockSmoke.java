package jvmumps;

/** J6 LOCK smoke (C108–C112 direct-mode form). */
public final class J6LockSmoke {
    public static void main(String[] args) {
        LineInterp m = new LineInterp();
        String a = m.run("L +^L1 W \"got\",! L -^L1 W \"done\",!");
        check(a, "got\ndone\n", "lock-simple");
        m = new LineInterp();
        check(m.run("L +X W \"got\",!"), "got\n", "lock-local");
        m = new LineInterp();
        check(m.run("L +X:0 W \"got\",! L +X:0 W \"twice\",!"), "got\ntwice\n", "lock-relock");
        m = new LineInterp();
        check(m.run("L +X L -X W \"off\",!"), "off\n", "lock-minus");
        m = new LineInterp();
        check(m.run("L +X,Y W \"both\",!"), "both\n", "lock-comma");
        System.out.println("J6 LOCK OK");
    }

    static void check(String got, String want, String name) {
        if (!want.equals(got)) {
            throw new IllegalStateException("J6 " + name + " got=" + q(got) + " want=" + q(want));
        }
    }

    static String q(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}
