package jvmumps;

/** J1 smoke: arith, concat, compare, mstr, truthiness (C01/C15–C25/C30–C32 style). */
public final class J1Smoke {
    public static void main(String[] args) {
        Engine e = new ExprEngine();
        check(e, "W 1+1,!", "2\n");
        check(e, "W 7/2,!", "3.5\n");
        check(e, "W 7#2,!", "1\n");
        check(e, "W 2**10,!", "1024\n");
        check(e, "W -5+8,!", "3\n");
        check(e, "W 1+2*3,!", "9\n");
        check(e, "W 2*3+1,!", "7\n");
        check(e, "W 12/2/2,!", "3\n");
        check(e, "W 2**3**2,!", "64\n");
        check(e, "W 3-2-1,!", "0\n");
        check(e, "W 0.5,\" \",-0.5,!", ".5 -.5\n");
        check(e, "W -0.25,!", "-.25\n");
        check(e, "W (10>9),\" \",(1=1),!", "1 1\n");
        check(e, "W (\"a\"<\"b\"),!", "1\n");
        check(e, "W (\"a\"=\"A\"),!", "0\n");
        check(e, "W (\"abc\"[\"b\"),!", "1\n");
        check(e, "W (\"abc\"[\"x\"),!", "0\n");
        check(e, "W \"ab\"_\"cd\",!", "abcd\n");
        check(e, "W \"[\"_\"\"_\"]\",!", "[]\n");
        check(e, "W (1_2=12),!", "1\n");
        check(e, "W (1&1),(0!1),!", "11\n");
        check(e, "W ('0),('1),!", "10\n");
        check(e, "W -7#3,!", "2\n");
        check(e, "W 7#-3,!", "-2\n");
        // dependent pair: do not reset between
        e.reset();
        String a = e.evalLine("S X=1");
        String b = e.evalLine("W X+1,!");
        if (!a.isEmpty() || !"2\n".equals(b)) {
            throw new IllegalStateException("J1 fail: S X=1 / W X+1 -> " + b);
        }
        System.out.println("J1 SMOKE OK");
    }

    private static void check(Engine e, String code, String want) {
        e.reset();
        String got = e.evalLine(code);
        if (!want.equals(got)) {
            throw new IllegalStateException("J1 fail: " + code + " -> " + quote(got) + " want " + quote(want));
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}
