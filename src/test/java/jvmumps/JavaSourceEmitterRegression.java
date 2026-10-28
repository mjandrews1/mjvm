package jvmumps;

import java.util.ArrayList;
import java.util.List;
import mjvm.compiler.JavaSourceEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * JavaSourceEmitter (--class) regression tests — IR parity under compiled execution.
 *
 * <p>Bug 1 (mjvm#11): forward loop exits emitted `break L<n>` to a plain-statement label — javac:
 * "undefined label" — so every program with a FOR loop failed to compile. Back edges emitted
 * `continue L_root`, which restarted the whole method body (re-executing loop init). Fixed with
 * structured emission: labeled loops for backward targets, labeled blocks for forward targets
 * (pulled before loops they escape), plus a switch-dispatch fallback for crossing jump spans.
 *
 * <p>Bug 2: un-exitable loops (argumentless FOR with postconditional QUIT — exit only via return)
 * left unreachable statements after them; javac rejected the class. The IR after such a loop is
 * dead code — now skipped at emission.
 *
 * <p>Bug 3: esc() did not escape control characters, so the frontend's \u0001 global-subscript
 * separator broke generated string literals; and MExprJava.compileGlobalRef emitted adjacent string
 * literals for literal subscripts ("^G\u0001" "5") — invalid Java.
 *
 * <p>Usage: java -cp build:lib/* jvmumps.JavaSourceEmitterRegression
 */
public final class JavaSourceEmitterRegression {

  static int pass = 0;
  static int fail = 0;
  static int id = 0;

  public static void main(String[] args) throws Exception {
    System.out.println("=== JavaSourceEmitter Regression Tests ===");
    System.out.println();

    // --- Bug 1: FOR loops of every shape must compile and match the IR machine ---
    check("f i=1:1:5 w i,\" \"");
    check("s a=0 f i=1:1:10 s a=a+i f j=1:1:3 s a=a+j w a,!"); // nested loops
    check("f i=1:1:5 q:i=3  w i,\" \""); // postconditional QUIT inside FOR
    check("f i=1:1:5 i i=3 q  w i,\" \""); // IF QUIT inside FOR
    check("i 1 w \"yes\" e  w \"no\""); // IF/ELSE forward skip
    check("s x=\"\" f i=1:1:20 s x=x_\"a\" q:$L(x)>5  w x,!"); // FOR + postconditional QUIT value
    check("f i=5:-1:1 w i,\" \""); // negative increment
    check("f i=1:1:3 w i q"); // plain QUIT (RET) inside FOR

    // --- Bug 2: argumentless $ORDER walk — loop exits only via return; tail is dead code ---
    check(
        "k ^T f i=1:1:3 s ^T(i)=i s j=\"\" f  s j=$O(^T(j)) q:j=\"\"  w ^T(j),\" \"  w"
            + " \"after\",!");
    check("s j=\"\" f  s j=$O(j) q:j=\"\"  w j");

    // --- Bug 3: literal global subscripts (\u0001 separator) + globals in expressions ---
    check("k ^G s ^G(5)=\"five\" w $G(^G(5)),^G(5),!");
    check("s ^G(\"a\"_\"b\",3)=7 w $G(^G(\"ab\",3)),!");

    // --- multi-line routine (compileProgram path; RET/fallthrough shapes) ---
    checkProgram("RTN1\n s a=1\n f i=1:1:4 s a=a+i\n w a,! q");

    // --- fallback: crossing jump spans are unexpressible structurally — dispatch mode ---
    List<Ir> prog = new ArrayList<>();
    prog.add(new Ir(Ir.PUSH, null, null, 1, 0)); // pc0: push 0 (falsey)
    prog.add(new Ir(Ir.JMP0, null, null, 0, 6)); // pc1: forward → block [1,6)
    prog.add(new Ir(Ir.RET, null, null, 0, 0)); // pc2: loop head (never reached via fallthrough)
    prog.add(new Ir(Ir.PUSH, null, "x", 0, 0)); // pc3: write "x"
    prog.add(new Ir(Ir.WRITE, null, null, 0, 0)); // pc4
    prog.add(new Ir(Ir.JMP, null, null, 0, 2)); // pc5: back to pc2 — loop [2,9)
    prog.add(new Ir(Ir.PUSH, null, "y", 0, 0)); // pc6: block target: write "y"
    prog.add(new Ir(Ir.WRITE, null, null, 0, 0)); // pc7
    prog.add(new Ir(Ir.JMP, null, null, 0, 2)); // pc8: loop [2,9) crosses block [1,6) → dispatch
    check("crossing-IR dispatch fallback", prog, null);

    System.out.println();
    System.out.println("  summary: pass=" + pass + "  fail=" + fail);
    System.out.println(fail == 0 ? "RESULT: OK" : "RESULT: FAILURES");
    if (fail > 0) System.exit(1);
  }

  static void check(String code) throws Exception {
    MFrontend fe = new MFrontend();
    check(code, fe.compileLine(code), fe.lastTags());
  }

  static void checkProgram(String code) throws Exception {
    MFrontend fe = new MFrontend();
    check(firstLine(code), fe.compileProgram(code), fe.lastTags());
  }

  static void check(String label, List<Ir> prog, java.util.Map<String, Integer> tags)
      throws Exception {
    MRuntime rtIr = new MRuntime(new MStore());
    IrMachine.run(prog, rtIr, tags);
    MRuntime rtClass = new MRuntime(new MStore());
    JavaSourceEmitter.compile(prog, "MjvmGenReg" + (id++), rtClass).run();
    String want = rtIr.output();
    String got = rtClass.output();
    if (want.equals(got)) {
      pass++;
      System.out.println("  ok   " + label + "  [" + want.replace("\n", "\\n") + "]");
    } else {
      fail++;
      System.out.println("  FAIL " + label);
      System.out.println("       ir    = [" + want.replace("\n", "\\n") + "]");
      System.out.println("       class = [" + got.replace("\n", "\\n") + "]");
    }
  }

  static String firstLine(String s) {
    int i = s.indexOf('\n');
    return i < 0 ? s : s.substring(0, i);
  }
}
