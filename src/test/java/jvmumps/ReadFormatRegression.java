package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;

/**
 * READ format arguments: ! / !! newlines, # page, ?expr column, *expr char, and prompt literals
 * before the read variable — R !!,"Prompt: ",X:DTIME (1,991 corpus sites). Prompt writes carry no
 * endWrite (no trailing newline: the user's input stays on the prompt line).
 */
public final class ReadFormatRegression {

  static int passed = 0, failed = 0;

  static void check(String src, boolean routinePath) {
    MFrontend fe = new MFrontend();
    List<Ir> ir;
    List<String> swallowed;
    try {
      if (routinePath) {
        ir = fe.compileProgram("RTN ; test\nLBL " + src + "\n Q");
        swallowed = fe.lastSwallowedErrors();
      } else {
        ir = fe.compileLine(src);
        swallowed = List.of();
      }
    } catch (Exception | StackOverflowError e) {
      failed++;
      System.out.println(
          "FAIL [" + (routinePath ? "routine" : "direct") + "] \"" + src + "\" -> " + e);
      return;
    }
    if (!swallowed.isEmpty()) {
      failed++;
      System.out.println(
          "FAIL ["
              + (routinePath ? "routine" : "direct")
              + "] \""
              + src
              + "\" swallowed: "
              + swallowed.get(0));
      return;
    }
    if (ir.isEmpty()) {
      failed++;
      System.out.println(
          "FAIL [" + (routinePath ? "routine" : "direct") + "] \"" + src + "\" produced no IR");
      return;
    }
    passed++;
  }

  static void both(String src) {
    check(src, false);
    check(src, true);
  }

  public static void main(String[] args) {
    System.out.println("== READ format regression ==");

    both("R !!,\"GO TO: \",X:DTIME I (X=\"\")!(X=\"^\") G EXIT");
    both("R !!,\" GO TO ITEM NUMBER: \",X:DTIME");
    both("R !,\"Press RETURN to continue or \"\"^\"\" to exit: \",X:DTIME");
    both("R !!,\"END OF REPORT...PRESS RETURN TO CONTINUE: \",X:DTIME Q:X=\"^\"");
    both("R !!,\"Enter '^' to exit, press any key to continue: \",X:DTIME S:'$T X=\"^\" Q:X=\"^\"");
    both("R ?10,\"name: \",X:5");
    both("R #,\"page: \",X:DTIME");
    both("R *7,X");
    both("R X:DTIME");
    both("R !,X,!,Y");
    both("R \"Prompt: \",X:DTIME");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0
            ? "  RESULT: ReadFormatRegression OK"
            : "  RESULT: ReadFormatRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
