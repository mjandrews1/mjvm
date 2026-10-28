package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;

/** Class-5 regression: argument indirection (@) across commands. */
public final class IndirectRegression {

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
    System.out.println("== indirect regression ==");

    // DO/GOTO with indirection
    compiles("D @TARGET");
    compiles("D @TARGET^ROUTINE");
    compiles("D @(\"TAG\"_X)");
    compiles("G @TARGET");
    compiles("G @(\"LABEL\"_N)");
    compiles("D @($S(X=1:\"TAG1\",1:\"TAG2\"))");

    // SET with indirection (target and value)
    compiles("S @NAME=value");
    compiles("S @(\"X\"_I)=123");
    compiles("S X=@Y");
    compiles("S X=@(\"VAL\"_I)");
    compiles("S X=@(@Y)");

    // KILL/NEW with indirection
    compiles("K @NAME");
    compiles("K @(\"ALL\"_X)");
    compiles("K @(^TMP($J))");
    compiles("N @NAME");

    // WRITE with indirection
    compiles("W @TEXT");
    compiles("W @(\"MSG\"_N)");
    compiles("W @IOF");

    // XECUTE with indirection
    compiles("X @CODE");
    compiles("X @(\"S X=\"_I)");

    // LOCK with indirection
    compiles("L +@LOCKNAME");
    compiles("L @(\"+\"_DIC_DA)");
    compiles("L -@LOCKNAME");

    // READ with indirection
    compiles("R @VAR");

    // MERGE with indirection
    compiles("M @TO=FROM");

    // OPEN/CLOSE/USE with indirection
    compiles("O @DEV");
    compiles("C @DEV");
    compiles("U @DEV");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0 ? "  RESULT: IndirectRegression OK" : "  RESULT: IndirectRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
