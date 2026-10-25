package jvmumps;

/**
 * J12 (catch-up): symbols, literals, and quoted strings.
 *
 * <p>Foundation for the JvMumps 131/8/0 push — name tokens (local/global/%/$), numeric literals
 * (int/frac/exp/mstr display), and quoted strings (empty, embedded "", concat).
 */
public final class J12SymLitSmoke {
  static int pass, fail;

  public static void main(String[] args) {
    Engine e = new LineEngine();

    // ---- quoted strings ----
    check(e, "W \"hello\",!", "hello\n");
    check(e, "W \"\",!", "\n"); // empty
    check(e, "W \" \",!", " \n"); // space
    check(e, "W \"say \"\"hi\"\"\",!", "say \"hi\"\n"); // embedded quotes
    check(e, "W \"\"\"\",!", "\"\n"); // one quote char
    check(e, "W \"\"\"\"\"\",!", "\"\"\n"); // two quote chars
    check(e, "W \"a\"_\"b\"_\"c\",!", "abc\n");
    check(e, "W \"[\"_\"\"_\"]\",!", "[]\n");
    check(e, "W \"x\"_\"\"_\"y\",!", "xy\n");
    check(e, "S S=\"a\"\"b\" W S,!", "a\"b\n");
    check(e, "S S=\"\"\"\"\"\" W S,!", "\"\"\n");

    // ---- numeric literals ----
    check(e, "W 0,!", "0\n");
    check(e, "W 1,!", "1\n");
    check(e, "W 100,!", "100\n");
    check(e, "W 1.5,!", "1.5\n");
    check(e, "W 0.5,!", ".5\n");
    check(e, "W .5,!", ".5\n");
    check(e, "W -0.5,!", "-.5\n");
    check(e, "W -0.25,!", "-.25\n");
    check(e, "W 1E2,!", "100\n");
    check(e, "W 1e2,!", "100\n");
    check(e, "W 1E+2,!", "100\n");
    check(e, "W 1.5E-2,!", ".015\n");
    check(e, "W 0.05,!", ".05\n");
    check(e, "W 0.25,!", ".25\n");
    check(e, "W 2.0,!", "2\n");
    check(e, "W 123456789,!", "123456789\n");

    // ---- symbols: locals ----
    check(e, "S A=1 W A,!", "1\n");
    check(e, "S B1=2 W B1,!", "2\n");
    check(e, "S %A=3 W %A,!", "3\n");
    check(e, "S %Z1=4 W %Z1,!", "4\n");
    check(e, "S X=1,Y=2,Z=3 W X_Y_Z,!", "123\n");
    check(e, "S Abc=9 W Abc,!", "9\n");
    check(e, "S abc=1 W abc,!", "1\n");

    // ---- symbols: globals ----
    check(e, "K ^G S ^G=7 W ^G,!", "7\n");
    check(e, "K ^%G S ^%G=8 W ^%G,!", "8\n");
    check(e, "K ^G S ^G(1)=9 W ^G(1),!", "9\n");
    check(e, "K ^G S ^G(1,\"b\")=10 W ^G(1,\"b\"),!", "10\n");
    check(e, "K ^G S ^G(\"x\")=11 W ^G(\"x\"),!", "11\n");

    // ---- symbols: subscripts / $NAME ----
    check(e, "S A(1)=2 W A(1),!", "2\n");
    check(e, "S A(1,\"b\")=3 W A(1,\"b\"),!", "3\n");
    check(e, "S A(1)=1 W $NA(A(1)),!", "A(1)\n");

    // ---- mixed: quoted string + symbol + number ----
    check(e, "S N=5 W \"n=\"_N,!", "n=5\n");
    check(e, "S N=5 W \"n=\"_N_\"!\",!", "n=5!\n");
    check(e, "W \"a\"_1_\"b\",!", "a1b\n");
    check(e, "W 1_2_3,!", "123\n");
    check(e, "S S=\"AB\" W S,!", "AB\n");
    check(e, "S S=\"\" W \"[\"_S_\"]\",!", "[]\n");

    // ---- specials lex as names (value host-dependent) ----
    e.reset();
    try {
      String h = e.evalLine("W $HOROLOG");
      if (h != null && h.contains(",")) {
        pass++;
      } else {
        fail++;
        System.out.println("FAIL $HOROLOG -> " + h);
      }
    } catch (RuntimeException ex) {
      fail++;
      System.out.println("FAIL $HOROLOG threw " + ex);
    }

    System.out.println("J12 SYM/LIT/QUOTE OK pass=" + pass + " fail=" + fail);
    if (fail > 0) {
      throw new IllegalStateException("J12 fail=" + fail);
    }
  }

  private static void check(Engine e, String code, String want) {
    e.reset();
    String got;
    try {
      got = e.evalLine(code);
    } catch (RuntimeException ex) {
      fail++;
      System.out.println("FAIL " + code + " threw " + ex);
      return;
    }
    if (!want.equals(got)) {
      fail++;
      System.out.println("FAIL " + code + " -> " + q(got) + " want " + q(want));
      return;
    }
    pass++;
  }

  private static String q(String s) {
    return "\"" + s.replace("\n", "\\n").replace("\"", "\\\"") + "\"";
  }
}
