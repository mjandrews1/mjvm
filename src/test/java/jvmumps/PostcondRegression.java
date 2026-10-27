package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;

/** Class-3 regression: universal postconditional at dispatch. */
public final class PostcondRegression {

  static int passed = 0, failed = 0;

  static void compiles(String src) {
    try {
      MFrontend fe = new MFrontend();
      List<mjvm.ir.Ir> prog = fe.compileLine(src);
      passed++;
    } catch (Exception e) {
      failed++;
      System.out.println("FAIL: \"" + src + "\" -> " + e.getMessage().split("\n")[0]);
    }
  }

  public static void main(String[] args) {
    System.out.println("== postcond regression ==");

    // KILL/NEW with postconditional + comma
    compiles("K:X=1 A,B");
    compiles("N:X=1 A,B");
    compiles("K:$D(X) ^G");
    compiles("N:$G(FLAG) TMP,IDX");
    compiles("K:ERROR A,B,C");

    // READ with postconditional
    compiles("R:X=1 Y:5");

    // OPEN/CLOSE/USE with postconditional
    compiles("O:X=1 IO:(DEV)");
    compiles("C:X=1 IO");
    compiles("U:X=1 IO");

    // XECUTE with postconditional
    compiles("X:X=1 \"S Y=1\"");

    // HALT/HANG with postconditional
    compiles("H:X=1");
    compiles("HANG:X=1 5");

    // MERGE with postconditional
    compiles("M:X=1 TO=FROM");

    // Postconditional false → command skipped, next command runs
    compiles("K:0 A,B W \"not killed\"");
    compiles("N:0 A W \"not newed\"");

    // Mixed commands, some with postconds
    compiles("S X=1 K:X=1 A W \"done\"");
    compiles("W \"start\" K:$D(X) ^G W \"end\"");

    // Original postconditional commands still work
    compiles("S:X=1 Y=2");
    compiles("W:X=1 \"hello\"");
    compiles("Q:X=1");
    compiles("D:X=1 ^A");
    compiles("L:X=1 +A");

    // Postconditional with expressions
    compiles("K:$G(FLAG)=1 A,B");
    compiles("N:$P(X,\"^\",2)=\"Y\" TMP");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0 ? "  RESULT: PostcondRegression OK" : "  RESULT: PostcondRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
