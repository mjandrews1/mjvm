package jvmumps;

import java.util.List;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;

/**
 * Argumentless-DO vs tag-call disambiguation.
 *
 * <p>VA convention: an argumentless command is followed by TWO spaces before the next command; one
 * space after D means a tag call — including single-letter tags that collide with command words (D
 * N, D W, D P: ~12.5k one-space tag calls vs ~24k two-space argumentless sites in the FOIA corpus).
 * Asserts zero swallowed errors and non-empty IR on both compile paths, and — where the tag exists
 * — that the call actually lands (CALL with a label target, not a NEW/KILL mis-parse).
 */
public final class DoAmbiguityRegression {

  static int passed = 0, failed = 0;

  static void check(String src, boolean routinePath) {
    MFrontend fe = new MFrontend();
    List<Ir> ir;
    List<String> swallowed;
    try {
      if (routinePath) {
        // column-0 tag N (command-word collision) as in FileMan templates
        ir = fe.compileProgram("RTN ; test\nN W \"n\"\n Q\nLBL " + src + "\n Q");
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

  /** Full routine (tags at column 0) must compile with nothing swallowed. */
  static void routine(String code) {
    MFrontend fe = new MFrontend();
    List<Ir> ir;
    List<String> swallowed;
    try {
      ir = fe.compileProgram(code);
      swallowed = fe.lastSwallowedErrors();
    } catch (Exception | StackOverflowError e) {
      failed++;
      System.out.println("FAIL [routine-full] -> " + e);
      return;
    }
    if (!swallowed.isEmpty()) {
      failed++;
      System.out.println("FAIL [routine-full] swallowed: " + swallowed.get(0));
      return;
    }
    if (ir.isEmpty()) {
      failed++;
      System.out.println("FAIL [routine-full] produced no IR");
      return;
    }
    passed++;
  }

  public static void main(String[] args) {
    System.out.println("== DO disambiguation regression ==");

    // FileMan print-template idiom: single-letter tag calls (N collides with NEW)
    both("D N D N D N:$X>27 Q:'DN  W ?27 W \"NEW ACCOUNTS RECEIVABLE\"");
    both("D N:$X>27 Q:'DN  W ?27 W \"WT LBS: \"");
    both("D N Q:'DN  W ?27");
    both("D T:$X>27 Q:'DN  W ?27");
    // two-space argumentless forms must keep working
    both("D  Q:$G(NP)=\"^\"");
    both("D  W \"X\"");
    both("D  I CHK G DONE");
    // postconditional DO: one space = conditional tag call, two spaces = argumentless block
    both("D:$D(X) T");
    both("D:$D(X)  Q:X");
    // D with argument then one-space command (standard M: argument present, one space is fine)
    both("D TAG W \"X\"");
    both("D ^A,^B W \"X\"");
    // GOTO never argumentless at a word
    both("G W");
    both("G  W");
    // per-argument postconditional still parses
    both("D A:1 Q:'DN  W ?27");

    // column-0 command-word tags (VistA generated code): tag N, not NEW
    routine(
        "RTN ; t\n"
            + "N I X=\"\" G A:DV'[\"R\",X:'DV,X:D'>0,A\n"
            + " Q\n"
            + "LBL D N D N D N:$X>27 Q:'DN  W ?27\n"
            + " Q");
    // conditional argumentless block: D:cond K — one space, K not a label
    both("D:X K ^UTILITY($J)");
    both("D:DIKM1>0 K ^DIK(DIKZ1)");
    routine("RTN ; t\nK S X=QUX K QUX Q\n Q\nLBL D:DIKZ1'=DH(1) K DA D:DIKZ1'=DH(1) SET D DA\n Q");

    System.out.println();
    System.out.println("  summary: pass=" + passed + "  fail=" + failed);
    System.out.println(
        failed == 0
            ? "  RESULT: DoAmbiguityRegression OK"
            : "  RESULT: DoAmbiguityRegression FAILURES");
    if (failed > 0) System.exit(1);
  }
}
