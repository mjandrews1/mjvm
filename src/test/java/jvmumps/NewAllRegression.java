package jvmumps;

import mjvm.runtime.MRuntime;

/**
 * mjvm#31/#19-family regression: argumentless NEW (and exclusive NEW) must hide ALL locals — simple
 * (slot-side) AND subscripted (store-side) — and restore them exactly on scope exit (RET),
 * including restoring UNDEFINED (the var must be removed again, not left defined). GT.M V7.2-001 on
 * U01 is the arbiter for every expected value below.
 */
public final class NewAllRegression {

  static int pass = 0;
  static int fail = 0;

  public static void main(String[] args) throws Exception {
    // argumentless NEW in a DOed tag; caller var UNDEFINED -> after QUIT it must
    // stay undefined (GT.M: %GTM-E-UNDEF on W L; $D(L)=0 without error).
    evalProgram("MIN\n D TAG\n W $D(L),!\n Q\nTAG\n N\n S L=7\n Q\n", "0\n");
    errProgram("MINERR\n D TAG\n W L,!\n Q\nTAG\n N\n S L=7\n Q\n", "Undefined local variable");
    // caller var DEFINED -> restored to its old value after QUIT.
    evalProgram("MIN2\n S L=1\n D TAG2\n W L,!\n Q\nTAG2\n N\n S L=7\n Q\n", "1\n");
    // subscripted locals are hidden by argumentless NEW and restored after.
    evalProgram(
        "T3\n S A(1)=\"x\"\n D TAG3\n W A(1),!\n Q\nTAG3\n N\n S A(1)=\"y\" S A(2)=\"z\"\n Q\n",
        "x\n");
    evalProgram(
        "T4\n S A(1)=\"x\"\n D TAG4\n W $D(A(1)),!\n Q\nTAG4\n N\n W $D(A(1)),!\n Q\n", "0\n1\n");
    // exclusive NEW N (A): A keeps its value (inside and after); B is hidden
    // inside and restored after.
    evalProgram(
        "T5\n S A=1,B=2\n D TAG5\n W A,\"-\",B,!\n Q\nTAG5\n N (A)\n W A,\"-\",$D(B),!\n Q\n",
        "1-0\n1-2\n");
    // $TEST is the documented exception: argumentless NEW must not touch it.
    evalProgram("T6\n I 1\n D TAG6\n W $T,!\n Q\nTAG6\n N\n Q\n", "1\n");
    // nested argumentless NEW scopes restore LIFO.
    // nested argumentless NEW scopes restore LIFO: IN sees X=3, OUT sees 2 after
    // IN quits, caller sees 1 after OUT quits (GT.M V7.2-001 arbiter).
    evalProgram(
        "T7\n"
            + " S X=1\n"
            + " D OUT\n"
            + " W X,!\n"
            + " Q\n"
            + "OUT\n"
            + " N\n"
            + " S X=2\n"
            + " D IN\n"
            + " W X,!\n"
            + " Q\n"
            + "IN\n"
            + " N\n"
            + " S X=3\n"
            + " W X,!\n"
            + " Q\n",
        "3\n2\n1\n");

    System.out.println("NewAllRegression: pass=" + pass + " fail=" + fail);
    if (fail > 0) throw new IllegalStateException("NewAllRegression FAILED");
  }

  static void evalProgram(String code, String want) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    try {
      var prog = new mjvm.compiler.MFrontend().compileProgram(code);
      mjvm.runtime.IrMachine.run(prog, rt);
      String got = rt.output();
      if (got.equals(want)) {
        pass++;
        System.out.println("  ok   [program] " + code.split("\n", 2)[0]);
      } else {
        fail++;
        System.out.println(
            "  FAIL [program] "
                + code.split("\n", 2)[0]
                + " got=["
                + got.replace("\n", "\\n")
                + "] want=["
                + want.replace("\n", "\\n")
                + "]");
      }
    } catch (jvmumps.MError e) {
      fail++;
      System.out.println(
          "  FAIL [program] " + code.split("\n", 2)[0] + " threw: " + e.getMessage());
    }
  }

  static void errProgram(String code, String wantMsg) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    try {
      var prog = new mjvm.compiler.MFrontend().compileProgram(code);
      mjvm.runtime.IrMachine.run(prog, rt);
      fail++;
      System.out.println(
          "  FAIL [err-program] " + code.split("\n", 2)[0] + " (no error; want [" + wantMsg + "])");
    } catch (jvmumps.MError e) {
      if (e.getMessage() != null && e.getMessage().startsWith(wantMsg)) {
        pass++;
        System.out.println("  ok   [err-program] " + code.split("\n", 2)[0]);
      } else {
        fail++;
        System.out.println("  FAIL [err-program] wrong error: " + e.getMessage());
      }
    }
  }
}
