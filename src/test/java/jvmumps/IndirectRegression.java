package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;

/**
 * Class-5 regression: argument indirection (@) across commands.
 *
 * <p>Asserts two things the first version of this suite silently skipped: the compile must (a)
 * produce IR and (b) swallow no error lines — on BOTH the direct path (compileLine) and the routine
 * path (compileProgram with a label), which is what the VistA corpus exercises. (The original suite
 * only caught thrown exceptions; MFrontend swallows parse errors and returns normally, so every
 * case "passed" regardless of the fix — vacuous.)
 */
public final class IndirectRegression {

  static int passed = 0, failed = 0;

  static void check(String label, String src, boolean routinePath) {
    MFrontend fe = new MFrontend();
    List<Ir> ir;
    List<String> swallowed;
    try {
      if (routinePath) {
        String code = "RTN ; test\nLBL " + src + "\n Q";
        ir = fe.compileProgram(code);
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
    check("direct", src, false);
    check("routine", src, true);
  }

  public static void main(String[] args) {
    System.out.println("== indirect regression ==");

    // DO/GOTO with indirection
    both("D @TARGET");
    both("D @TARGET^ROUTINE");
    both("D @(\"TAG\"_X)");
    both("G @TARGET");
    both("G @(\"LABEL\"_N)");
    both("D @($S(X=1:\"TAG1\",1:\"TAG2\"))");
    both("G @DQ");
    both("G @(\"X\"_DQ) K YS");

    // SET with indirection (target and value)
    both("S @NAME=value");
    both("S @(\"X\"_I)=123");
    both("S X=@Y");
    both("S X=@(\"VAL\"_I)");
    both("S X=@(@Y)");

    // KILL/NEW with indirection
    both("K @NAME");
    both("K @(\"ALL\"_X)");
    both("K @(^TMP($J))");
    both("N @NAME");

    // WRITE with indirection
    both("W @TEXT");
    both("W @(\"MSG\"_N)");
    both("W @IOF");

    // XECUTE with indirection
    both("X @CODE");
    both("X @(\"S X=\"_I)");

    // LOCK with indirection (incl. timeout suffix)
    both("L +@LOCKNAME");
    both("L @(\"+\"_DIC_DA)");
    both("L -@LOCKNAME");
    both("L +@X:1");
    both("L +@LOCKNAME:10 K:'$T DIKLK");

    // READ with indirection
    both("R @VAR");
    both("R @VAR:DTIME");

    // MERGE with indirection
    both("M @TO=FROM");

    // OPEN/CLOSE/USE with indirection
    both("O @DEV");
    both("C @DEV");
    both("U @DEV");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0 ? "  RESULT: IndirectRegression OK" : "  RESULT: IndirectRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
