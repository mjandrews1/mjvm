package jvmumps;

/**
 * Repro for the two native-mJVM ENGINE GAPs (jvmumps#32, docs/FILEMAN-TIER-A-GAPS.md). GAP-1
 * numeric subscript canonicalisation on SET · GAP-2 $$ after tagged-routine prelude.
 */
public final class GapProbe {
  @SuppressWarnings("RedundantThrows")
  static void run(String label, String code) {
    try {
      mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
      rt.setExtrinsicHandler(
          (fn, argv) -> {
            if (fn.equalsIgnoreCase("P2")) {
              String y = argv.length > 0 ? argv[0].asStr() : "";
              return MValue.ofString(y + "ab");
            }
            throw new jvmumps.MError("Extrinsic $$" + fn + " not available");
          });
      var ir = new mjvm.compiler.MFrontend().compileProgram(code);
      mjvm.runtime.IrMachine.run(ir, rt);
      System.out.println("  " + label + " -> [" + rt.output().strip() + "]");
    } catch (Exception e) {
      System.out.println("  " + label + " -> EXC " + e);
    }
  }

  public static void main(String[] args) {
    System.out.println("GAP 1: numeric subscript canonicalisation on SET");
    run("S ^DD(0.01)=$O()  ", "K ^DD S ^DD(0.01)=\"b\" S K=\"\" S K=$O(^DD(K)) W K,!");
    run("S ^DD(1.10)=$O()  ", "K ^DD S ^DD(1.10)=\"c\" S K=\"\" S K=$O(^DD(K)) W K,!");
    run("W 0.01 (control)  ", "W 0.01,!");
    run("S ^DD(\".01\")     ", "K ^DD S ^DD(\".01\")=\"b\" S K=\"\" S K=$O(^DD(K)) W K,!");
    System.out.println();
    System.out.println("GAP 2: $$ dispatch after a tagged-routine prelude (ZLINK shape)");
    run("W $$P2 (bare)     ", "W $$P2(\"z\"),!");
    runZlink(
        "ZLINK + W $$P2   ", " Q\nP2(Y) ; extrinsic\n S Y=Y_\"ab\" Q Y\n", "S X=$$P2(\"z\") W X,!");
  }

  /** ZLINK shape: load a tagged routine, then run a call against it. */
  @SuppressWarnings("RedundantThrows")
  static void runZlink(String label, String prelude, String code) {
    try {
      mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
      mjvm.compiler.MFrontend fe = new mjvm.compiler.MFrontend();
      // Phase 1 — link the routine (tags stay callable).
      var irRoutine = fe.compileProgram(prelude);
      mjvm.runtime.IrMachine.run(irRoutine, rt, fe.lastTags());
      rt.reset();
      // Phase 2 — run the statement against the linked routine.
      var ir = fe.compileProgram(code);
      mjvm.runtime.IrMachine.run(ir, rt, fe.lastTags());
      System.out.println("  " + label + " -> [" + rt.output().strip() + "]");
    } catch (Exception e) {
      System.out.println("  " + label + " -> EXC " + e);
    }
  }

  private GapProbe() {}
}
