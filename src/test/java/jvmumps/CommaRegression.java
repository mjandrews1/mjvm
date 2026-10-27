package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;

/** Class-4 regression: comma-separated arguments across all commands. */
public final class CommaRegression {

  static int passed = 0, failed = 0;

  static void check(String name, boolean cond) {
    if (cond) passed++;
    else {
      failed++;
      System.out.println("FAIL: " + name);
    }
  }

  static void compiles(String src) {
    try {
      MFrontend fe = new MFrontend();
      List<Ir> prog = fe.compileLine(src);
      check("\"" + src + "\" compiles", prog != null && !prog.isEmpty());
    } catch (Exception e) {
      check("\"" + src + "\" compiles", false);
    }
  }

  public static void main(String[] args) {
    System.out.println("== comma regression ==");

    // DO with comma-separated targets
    compiles("D ^A,^B");
    compiles("D ^A,^B,^C");
    compiles("D TAG1,TAG2");
    compiles("D ^%ZISC,^FOO");

    // KILL/NEW with comma-separated names
    compiles("K A,B,C");
    compiles("N A,B,C");
    compiles("K X,Y,Z,W");

    // KILL/NEW exclusive form
    compiles("K (A,B)");
    compiles("N (A,B)");
    compiles("K (I,J,K)");

    // WRITE with comma-separated args (strings, formats)
    compiles("W \"x\",\"y\"");
    compiles("W !,\"x\",!,\"y\"");
    compiles("W \"a\",\"b\",\"c\"");
    compiles("W \"x\",!,\"y\",#,\"z\"");

    // SET with comma-separated assignments
    compiles("S X=1,Y=2");
    compiles("S A=\"a\",B=\"b\",C=3");

    // OPEN/CLOSE/USE with comma-separated devices
    compiles("O IO1,IO2");
    compiles("C IO1,IO2");
    compiles("U IO1,IO2");

    // READ with comma-separated variables + timeouts
    compiles("R X:10,Y:20");
    compiles("R X,Y");
    compiles("R X:5,Y:5,Z:5");

    // LOCK with comma-separated locks
    compiles("L +A,-B");
    compiles("L +A,+B,+C");
    compiles("L -A,-B");

    // Mixed commands with commas on the same line
    compiles("D ^A,^B W \"done\"");
    compiles("K A,B W \"killed\"");
    compiles("W \"x\",\"y\" D ^Z");

    // Single arg still works (no comma)
    compiles("D ^A");
    compiles("K A");
    compiles("W \"x\"");
    compiles("S X=1");
    compiles("O IO");
    compiles("R X");
    compiles("L +A");

    // Postconditional with comma-separated targets
    compiles("D:X=1 ^A,^B");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0 ? "  RESULT: CommaRegression OK" : "  RESULT: CommaRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
