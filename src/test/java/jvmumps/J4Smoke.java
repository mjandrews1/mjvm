package jvmumps;

/** J4 smoke: $F $G $J $RE $TR $NA $I $FN $RANDOM $SELECT */
public final class J4Smoke {
  static LineInterp m;

  public static void main(String[] args) {
    m = new LineInterp();
    chk("W $F(\"abc\",\"b\"),\" \",$F(\"abc\",\"b\",2),\" \",$F(\"abc\",\"x\"),!", "3 3 0\n");
    chk("W $G(X),\" \",$G(X,\"d\"),!", " d\n");
    chk("W $RE(\"abc\"),!", "cba\n");
    chk("W $TR(\"abc\",\"ab\",\"xy\"),!", "xyc\n");
    chk("W $J(\"ab\",5),!", "   ab\n");
    chk("W $FN(1234.567,\",\",2),!", "1,234.57\n");
    chk("W $S(0:1,1:2,1:3),!", "2\n");
    chk("W $S(1:10,1:20),!", "10\n");
    chk("S X=2 W $S(X=1:\"one\",X=2:\"two\",1:\"other\"),!", "two\n");
    m = new LineInterp();
    chk("S A(1,\"b\")=2 W $NA(A(1,\"b\")),!", "A(1,\"b\")\n");
    m = new LineInterp();
    chk("K Z W $I(Z),\" \",$I(Z,2),\" \",Z,!", "1 3 3\n");
    System.out.println("J4 SMOKE OK");
  }

  private static void chk(String code, String want) {
    String got = m.run(code);
    if (!want.equals(got)) {
      throw new IllegalStateException(
          "J4 fail: " + code + "\n got  " + q(got) + "\n want " + q(want));
    }
  }

  private static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }
}
