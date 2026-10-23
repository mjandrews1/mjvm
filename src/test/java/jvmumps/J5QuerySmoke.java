package jvmumps;

/** J5 $QUERY walk — corpus C51–C53 (closed refs, not invented strings). */
public final class J5QuerySmoke {
    public static void main(String[] args) {
        LineInterp m = new LineInterp();
        // C51.query-fwd
        String c51 =
            "K ^QM S ^QM(1)=\"a\" S ^QM(2)=\"b\" S ^QM(2,1)=\"c\" S ^QM(2,1,\"x\")=\"d\" S ^QM(10)=\"e\" "
                + "W $Q(^QM),\" \",$Q(^QM(2)),\" \",$Q(^QM(2,1)),\" \",$Q(^QM(2,1,\"x\")),\" \",$Q(^QM(10)),!";
        String g51 = m.run(c51);
        String want51 = "^QM(1) ^QM(2,1) ^QM(2,1,\"x\") ^QM(10) \n";
        if (!want51.equals(g51)) {
            throw new IllegalStateException("C51 got=" + q(g51) + " want=" + q(want51));
        }

        // C52.query-back
        m = new LineInterp();
        String c52 =
            "K ^QM S ^QM(1)=\"a\" S ^QM(2)=\"b\" S ^QM(2,1)=\"c\" S ^QM(2,1,\"x\")=\"d\" S ^QM(10)=\"e\" "
                + "W $Q(^QM(2),-1),\" \",$Q(^QM(2,1),-1),\" \",$Q(^QM(10),-1),!";
        String g52 = m.run(c52);
        String want52 = "^QM(1) ^QM(2) ^QM(2,1,\"x\")\n";
        if (!want52.equals(g52)) {
            throw new IllegalStateException("C52 got=" + q(g52) + " want=" + q(want52));
        }

        // C53.query-local
        m = new LineInterp();
        String c53 =
            "K L S L(1)=\"x\" S L(1,2)=\"y\" S L(3)=\"z\" W $Q(L),\" \",$Q(L(1,2)),\" \",$Q(L(3)),!";
        String g53 = m.run(c53);
        String want53 = "L(1) L(3) \n";
        if (!want53.equals(g53)) {
            throw new IllegalStateException("C53 got=" + q(g53) + " want=" + q(want53));
        }

        System.out.println("J5 QUERY OK C51-C53");
    }

    static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }
}
