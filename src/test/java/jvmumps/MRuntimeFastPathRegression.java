package jvmumps;

import mjvm.runtime.MRuntime;

/**
 * MRuntime fast-path regression tests (mjvm#6/#7/#8) — the hot paths must be semantically
 * transparent: identical results and identical $ETRAP behavior whether or not a fast path fires.
 *
 * <p>Bug 1: evalExpr's fast gate skipped single-token expressions, so every bare-variable read (the
 * most common hot expression) took the full parse; widening it also exposed that the fast path sat
 * outside the trap-firing try — an undefined-variable read bypassed $ETRAP (C115/C116). Fixed:
 * fast-path errors fall through to the trap-aware evaluator.
 *
 * <p>Bug 2: arithmetic chains were folded with * / \ # precedence; M is strictly left-to-right
 * (C30: 1+2*3 = 9). Fixed: single-pass left-to-right chain.
 *
 * <p>Bug 3: subscripted names (a(i)) re-parsed their name string on every setVar/getVar
 * (~200ns/call); the SubRef cache parses once and evaluates NAME subscripts per call — including
 * the null-rt raw-text semantics of the static parse paths.
 *
 * <p>Usage: java -cp build:lib/* jvmumps.MRuntimeFastPathRegression
 */
public final class MRuntimeFastPathRegression {

  static int pass = 0;
  static int fail = 0;

  public static void main(String[] args) throws Exception {

    // --- single-token fast path (was full-parse) ---
    eval("S X=7 W X", "7\n");
    eval("W 5", "5\n");

    // --- chain: strictly left-to-right, no precedence (C30) ---
    eval("W 1+2*3,! ", "9\n");
    eval("W 2+3*4,!", "20\n");
    eval("W 2*3+1,!", "7\n");
    eval("W 3-2-1,!", "0\n");
    eval("W 10\\3*2,!", "6\n");
    eval("W 7#3+1,!", "2\n"); // (7#3)+1
    eval("S A=2 W A*3-1,!", "5\n");
    eval("S A=2 W A#100+1,!", "3\n");
    eval("S A=2 W 2=A,!", "1\n"); // comparison stays on the 3-token path

    // --- $P fast pattern ---
    eval("S S=\"one two three four five\" W $P(S,\" \",2),!", "two\n");
    eval("S S=\"one two three\" S I=3 W $P(S,\" \",I),!", "three\n");
    eval("S S=\"one two three\" S I=1 W $P(S,\" \",I#10+1),!", "two\n");
    eval("S S=\"a,b,c\" W $P(S,\",\",3),!", "c\n");
    eval("S S=\"one two\" W $PIECE(S,\" \",1),!", "one\n");
    err(
        "S S=\"x-y-z\" W $P(S,\"-\",I),!",
        "Undefined local variable"); // undefined I must error, not piece

    // --- subscripted names: SubRef cache (#7) ---
    eval("S I=1 S A(I)=\"one\" S I=2 S A(I)=\"two\" W A(1),A(2),!", "onetwo\n");
    // subscripted local with a CHANGED subscript must re-resolve (raw-key locals cache bug)
    eval("S I=1 S A(I)=\"one\" S I=2 S A(I)=\"two\" W A(I),!", "two\n");
    eval("S A(3.50)=\"x\" W $O(A(\"\")),!", "3.5\n"); // numeric literal canonicalizes
    // quoted literal sub — mJVM keeps doubled quotes (matches subOf; fast path is transparent)
    eval("S A(\"k\"\"q\")=1 S K=$O(A(\"\")) W K,!", "k\"\"q\n");
    eval("S ^G(5)=\"g\" W ^G(5),!", "g\n");
    eval("S I=4 S ^G(I*2)=\"h\" W ^G(8),!", "h\n"); // complex sub falls to full parse
    // mjvm#15: repeated subscripted global reads with a CHANGING subscript in ONE
    // program must resolve live (preRead used to cache the raw name's run-start
    // value for the whole run). Line mode can't express this (crammed loops are a
    // separate defect family) — run it as a routine.
    evalProgram(
        "RTN15\n K ^P\n F I=1:1:3 S ^P(I)=I*11\n S R=0\n F I=1:1:3 S R=R+^P(I)\n W R\n Q", "66\n");
    eval("S A(1,2)=\"m\" W A(1,2),!", "m\n"); // multi-sub

    // --- $ETRAP transparency of the fast paths (C115 family) ---
    eval("S $ETRAP=\"W \"\"T\"\",!\" S Z=NOPE W \"after\",!", "T\nafter\n");
    eval("S $ETRAP=\"W \"\"T\"\",!\" S Z=NOPE1+NOPE2 W \"after\",!", "T\nafter\n");
    eval(
        "S $ETRAP=\"S Y=9 W Y,!\" S Z=NOPE W Y,!",
        "9\n9\n"); // trap state persists after fast-path error

    // --- undefined local without trap stays an error ---
    err("W NOPE", "Undefined local variable");

    System.out.println();
    System.out.println("  summary: pass=" + pass + "  fail=" + fail);
    System.out.println(fail == 0 ? "RESULT: OK" : "RESULT: FAILURES");
    if (fail > 0) System.exit(1);
  }

  static void eval(String code, String want) throws Exception {
    MRuntime rt =
        new MRuntime(new MStore()); // fresh state per case: $ETRAP and store must not leak
    try {
      run(rt, code);
      String got = rt.output();
      if (want != null && got.equals(want)) {
        pass++;
        System.out.println("  ok   " + code);
      } else if (want == null) {
        pass++;
      } else {
        fail++;
        System.out.println("  FAIL " + code + " got=[" + got.replace("\n", "\\n") + "]");
      }
    } catch (jvmumps.MError e) {
      fail++;
      System.out.println("  FAIL " + code + " threw: " + e.getMessage());
    }
  }

  static void err(String code, String wantMsg) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    try {
      run(rt, code);
      fail++;
      System.out.println("  FAIL " + code + " (no error; want [" + wantMsg + "])");
    } catch (jvmumps.MError e) {
      if (e.getMessage() != null && e.getMessage().startsWith(wantMsg)) {
        pass++;
        System.out.println("  ok   " + code);
      } else {
        fail++;
        System.out.println("  FAIL " + code + " wrong error: " + e.getMessage());
      }
    }
  }

  static void run(MRuntime rt, String code) throws Exception {
    var prog = new mjvm.compiler.MFrontend().compileLine(code);
    mjvm.runtime.IrMachine.run(prog, rt);
  }

  /** Routine-mode case: multi-line program, ONE IrMachine.run (preRead applies per run). */
  static void evalProgram(String code, String want) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    try {
      var prog = new mjvm.compiler.MFrontend().compileProgram(code);
      mjvm.runtime.IrMachine.run(prog, rt);
      String got = rt.output();
      if (want != null && got.equals(want)) {
        pass++;
        System.out.println("  ok   [program] " + code.split("\n", 2)[0]);
      } else if (want == null) {
        pass++;
      } else {
        fail++;
        System.out.println(
            "  FAIL [program] got=["
                + got.replace("\n", "\\n")
                + "] want=["
                + want.replace("\n", "\\n")
                + "]");
      }
    } catch (jvmumps.MError e) {
      fail++;
      System.out.println("  FAIL [program] threw: " + e.getMessage());
    }
  }
}
