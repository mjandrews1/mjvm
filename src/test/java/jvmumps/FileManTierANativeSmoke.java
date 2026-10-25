package jvmumps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FileMan Tier A smoke on the **native mJVM** path (jvmumps#32, 1.0 gate).
 *
 * <p>Same A5/A6/A7 gates as {@link FileManTierASmoke}, but executed through {@code MFrontend} +
 * {@code IrMachine} + {@code MRuntime} — not {@code LineInterp}. The 1.0 gate is *FileMan Tier A
 * without ENGINE GAP*; this is the half that was still open.
 *
 * <p>A8 (c3fst) is NOT part of this suite: c3fst is deprecated.
 *
 * <p>GOLDEN: parse a small stable FileMan/toolkit subset from FILEMAN_CORPUS. RUN: FileMan-shaped
 * direct-mode snippets (XLFUTL/XLFSTR idioms).
 *
 * <p>Exit 0 = golden parse clean + all RUN cases MATCH. Failures classified: ENGINE GAP / DIALECT /
 * DATA.
 */
public final class FileManTierANativeSmoke {

  static final String[] GOLDEN = {
    "dictest.m", "explore.m", "seedpat.m",
    "fileman/DIC0.m", "fileman/DILF.m", "fileman/DILIBF.m",
    "utils/XLFUTL.m", "utils/XLFSTR.m",
  };

  static final String[][] RUN_CASES = {
    {"fm.piece", "S X=\"A^B^C\" W $P(X,\"^\",2),!", "B"},
    {"fm.extract", "S X=\"HELLO\" W $E(X,2,4),!", "ELL"},
    {"fm.length", "W $L(\"ABC\"),$L(\"A,B,C\",\",\"),!", "33"},
    {
      "fm.data-order",
      "K ^FMS S ^FMS(1)=\"a\",^FMS(2)=\"b\" W $D(^FMS),! S K=\"\" S K=$O(^FMS(K)) W K,!",
      "10\n1"
    },
    {"fm.get-default", "W $G(^FMS(9),\"dflt\"),!", "dflt"},
    {
      "fm.order-dec", "K ^DD S ^DD(0.81)=\"a\",^DD(0.01)=\"b\" S K=\"\" S K=$O(^DD(K)) W K,!", ".01"
    },
    {
      "fm.order-multi",
      "K ^DIC S ^DIC(1,0)=\"FILE\" S ^DIC(1,1)=\"F1\" S ^DIC(1,2)=\"F2\" W $D(^DIC(1)),! S K=\"\" S"
          + " K=$O(^DIC(1,K)) W K,!",
      "10\n0"
    },
    {
      "fm.order-next",
      "K ^DIC S ^DIC(1,1)=\"F1\" S ^DIC(1,2)=\"F2\" S K=1 S K=$O(^DIC(1,K)) W K,!",
      "2"
    },
    {"fm.order-empty", "K ^DIC S ^DIC(1,1)=\"F1\" S K=1 S K=$O(^DIC(1,K)) W \"[\",K,\"]\",!", "[]"},
    {"fm.query", "K ^Q S ^Q(1,2,3)=\"x\" W $QUERY(^Q),!", "^Q(1,2,3)"},
  };

  /** Minimal extrinsic used by the A6 gate. */
  static final String EXTRINSIC_ROUTINE = " Q\n" + "P2(Y) ; extrinsic\n" + " S Y=Y_\"ab\" Q Y\n";

  /**
   * Registered OPEN ENGINE GAPs (docs/FILEMAN-TIER-A-GAPS.md). These are reported as KNOWN GAP and
   * do not fail the merge gate — but the 1.0 gate still requires them CLOSED. Any failure NOT in
   * this set fails the build; a case that starts passing is reported so it can be removed from the
   * register.
   */
  static final java.util.Set<String> KNOWN_GAPS = java.util.Set.of();

  @SuppressWarnings("RedundantThrows")
  static String filemanRoot() {
    String env = System.getenv("FILEMAN_CORPUS");
    return env != null && !env.isEmpty() ? env : System.getProperty("user.home") + "/_fileman";
  }

  public static void main(String[] args) throws Exception {
    String root = args.length > 0 && Files.isDirectory(Path.of(args[0])) ? args[0] : filemanRoot();
    System.out.println("## FileMan Tier A smoke — NATIVE mJVM (FILEMAN_CORPUS=" + root + ")");
    System.out.println("### path: MFrontend + IrMachine + MRuntime (not LineInterp)");
    System.out.println("### NOTE A8 c3fst search smoke SKIPPED (c3fst deprecated; jvmumps#32)");
    System.out.println();

    int fail = 0, known = 0, healed = 0;
    int[] r = goldenParse(root);
    fail += r[0];
    System.out.println();
    r = runCases();
    fail += r[0];
    known += r[1];
    healed += r[2];
    System.out.println();
    r = extrinsic();
    fail += r[0];
    known += r[1];
    healed += r[2];

    System.out.println();
    System.out.printf("  summary: fail=%d  known-gap=%d  healed=%d%n", fail, known, healed);
    if (healed > 0) {
      System.out.println(
          "  NOTE: a registered gap now PASSES — remove it from KNOWN_GAPS /"
              + " docs/FILEMAN-TIER-A-GAPS.md");
    }
    if (fail == 0) {
      System.out.println(
          known == 0
              ? "  RESULT: FileMan Tier A smoke OK (native mJVM)"
              : "  RESULT: FileMan Tier A smoke OK (native mJVM) — "
                  + known
                  + " registered ENGINE GAP(s) remain; 1.0 requires them CLOSED");
      return;
    }
    System.out.println(
        "  RESULT: FileMan Tier A smoke FAILED (" + fail + " unexpected) (native mJVM)");
    System.exit(1);
  }

  // ---- A5: golden parse -------------------------------------------

  static int[] goldenParse(String root) {
    System.out.println("### A5 READ: routine parse + labels (golden subset)");
    Path base = Path.of(root);
    if (!Files.isDirectory(base)) {
      System.out.println("  SKIP golden parse (no FILEMAN_CORPUS dir: " + root + ")");
      return new int[] {0, 0, 0};
    }
    int fail = 0;
    for (String rel : GOLDEN) {
      Path p = Path.of(root, rel);
      if (!Files.isRegularFile(p)) {
        System.out.printf("  %-28s MISSING (DATA)%n", rel);
        fail++;
        continue;
      }
      String code;
      try {
        code = Files.readString(p);
      } catch (Exception e) {
        System.out.printf("  %-28s READ ERROR %s%n", rel, e);
        fail++;
        continue;
      }
      try {
        List<LineState.RtLine> lines = LineRoutine.parseRoutine(code);
        int nlab = 0;
        for (LineState.RtLine tl : lines) {
          if (tl.tag != null && !tl.tag.isEmpty()) {
            nlab++;
          }
        }
        if (nlab == 0) {
          System.out.printf("  %-28s NO LABELS (DIALECT)%n", rel);
          fail++;
        } else {
          System.out.printf("  %-28s ok  labels=%d%n", rel, nlab);
        }
      } catch (Exception e) {
        System.out.printf(
            "  %-28s PARSE ERROR (%s) %s%n", rel, FileManTierASmoke.classify(e.toString()), e);
        fail++;
      }
    }
    return new int[] {fail, 0, 0};
  }

  // ---- A7: RUN cases on the native path ----------------------------

  static int[] runCases() {
    System.out.println("### A7 FileMan-shaped RUN cases (native mJVM)");
    int fail = 0, known = 0, healed = 0;
    for (String[] c : RUN_CASES) {
      int[] r = runNative(c[0], c[1], c[2]);
      fail += r[0];
      known += r[1];
      healed += r[2];
    }
    return new int[] {fail, known, healed};
  }

  // ---- A6: extrinsic dispatch -------------------------------------

  static int[] extrinsic() {
    System.out.println("### A6 extrinsic dispatch (native mJVM)");
    int fail = 0, known = 0, healed = 0;
    int[] r = runNative("fm.extrinsic", "S X=$$P2(\"z\") W X,!", "zab", EXTRINSIC_ROUTINE);
    fail += r[0];
    known += r[1];
    healed += r[2];
    r = runNative("fm.extrinsic-byref", "S X=$$P2(.Y) W X,!", "ab", EXTRINSIC_ROUTINE);
    fail += r[0];
    known += r[1];
    healed += r[2];
    return new int[] {fail, known, healed};
  }

  /** Run one code string on MFrontend + IrMachine; score against want. */
  static int[] runNative(String name, String code, String want) {
    return runNative(name, code, want, null);
  }

  /**
   * {@code prelude} is a tagged routine to LINK; {@code code} is then run as a separate statement
   * against the linked routine. This is the ZLINK shape {@code LineInterp} uses — concatenating
   * them into one program is not M, because a routine's {@code Q} ends the top-down run.
   *
   * @return {fail, knownGap, healed}
   */
  static int[] runNative(String name, String code, String want, String prelude) {
    try {
      mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
      mjvm.compiler.MFrontend fe = new mjvm.compiler.MFrontend();
      if (prelude != null) {
        // Phase 1 — link the routine (tags stay callable).
        var irRoutine = fe.compileProgram(prelude);
        mjvm.runtime.IrMachine.run(irRoutine, rt, fe.lastTags());
        rt.reset();
      }
      // Phase 2 — run the statement against the linked routine.
      var ir = fe.compileProgram(code);
      mjvm.runtime.IrMachine.run(ir, rt, fe.lastTags());
      String got = rt.output().strip();
      if (want.strip().equals(got)) {
        if (KNOWN_GAPS.contains(name)) {
          System.out.printf(
              "  %-22s MATCH  %s   [HEALED — drop from KNOWN_GAPS]%n",
              name, FileManTierASmoke.quote(got));
          return new int[] {0, 0, 1};
        }
        System.out.printf("  %-22s MATCH  %s%n", name, FileManTierASmoke.quote(got));
        return new int[] {0, 0, 0};
      }
      String cls = FileManTierASmoke.classifyMismatch(code, want, got);
      if (KNOWN_GAPS.contains(name)) {
        System.out.printf(
            "  %-22s KNOWN GAP (%s) exp=%s got=%s%n",
            name, cls, FileManTierASmoke.quote(want.strip()), FileManTierASmoke.quote(got));
        return new int[] {0, 1, 0};
      }
      System.out.printf(
          "  %-22s MISMATCH (%s) exp=%s got=%s%n",
          name, cls, FileManTierASmoke.quote(want.strip()), FileManTierASmoke.quote(got));
      return new int[] {1, 0, 0};
    } catch (Exception e) {
      String cls = FileManTierASmoke.classify(e.toString());
      if (KNOWN_GAPS.contains(name)) {
        System.out.printf("  %-22s KNOWN GAP (%s) %s%n", name, cls, e);
        return new int[] {0, 1, 0};
      }
      System.out.printf("  %-22s ERROR (%s) %s%n", name, cls, e);
      return new int[] {1, 0, 0};
    }
  }

  private FileManTierANativeSmoke() {}
}
