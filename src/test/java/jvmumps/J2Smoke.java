package jvmumps;

/** J2 smoke: SET/IF/ELSE/FOR/WRITE/KILL/NEW/MERGE/QUIT (C01–C25 style). */
public final class J2Smoke {
    static LineInterp m;

    public static void main(String[] args) {
        m = new LineInterp();
        // C01 / C20 / C25
        chk("W 1+1,\" \",7/2,\" \",7#2,\" \",2**10,\" \",-5+8,!", "2 3.5 1 1024 3\n");
        chk("W 0,\" \",1,\" \",100,\" \",1.5,\" \",-0.5,!", "0 1 100 1.5 -.5\n");
        chk("W 0.5,\" \",-0.5,\" \",0.25,\" \",0,!", ".5 -.5 .25 0\n");
        // C30–C32 LTR
        chk("W 1+2*3,\" \",2*3+1,!", "9 7\n");
        chk("W 12/2/2,\" \",2**3**2,\" \",3-2-1,!", "3 64 0\n");
        // C15–C18
        chk("W (10>9),\" \",(1=1),\" \",(1=2),!", "1 1 0\n");
        chk("W \"ab\"_\"cd\",!", "abcd\n");
        // C18 concat-empty: W "["_""_"]",!
        String q = "\"";
        chk("W " + q + "[" + q + "_" + q + q + "_" + q + "]" + q + ",!", "[]\n");
        chk("W " + q + "[" + q + "_1_" + q + "]" + q + ",!", "[1]\n");
        // C22
        chk("W -7#3,\" \",7#-3,\" \",-7#-3,!", "2 -2 -1\n");
        // C24 ISO = : both numeric → numeric compare (J17; matches Py/C3)
        chk("W (\"1.0\"=1),\" \",(\"+1\"=1),!", "1 1\n");
        // C13 IF
        m = new LineInterp();
        chk("S X=1 I X=1 W \"ok\",!", "ok\n");
        // T12 ELSE
        m = new LineInterp();
        chk("S X=0 I X=1 W \"THEN\",! E  W \"ELSE\",!", "ELSE\n");
        // C12 postcond
        m = new LineInterp();
        chk("S X=1 W:X=1 \"yes\",! W:X=2 \"no\",!", "yes\n");
        // C09 FOR
        m = new LineInterp();
        chk("S S=\"\" F I=1:1:5 S S=S_I W S,!", "1\n12\n123\n1234\n12345\n");
        // C10 FOR list
        m = new LineInterp();
        chk("S S=\"\" F I=5,3,1 S S=S_I W S,!", "5\n53\n531\n");
        // C11 NEW
        m = new LineInterp();
        chk("S X=1 N X S X=99 W X,!", "99\n");
        System.out.println("J2 SMOKE OK");
    }

    private static void chk(String code, String want) {
        String got = m.run(code);
        if (!want.equals(got)) {
            throw new IllegalStateException("J2 fail: " + code + "\n got  " + q(got) + "\n want " + q(want));
        }
    }

    private static String q(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}
