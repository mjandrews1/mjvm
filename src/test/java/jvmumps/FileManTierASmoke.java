package jvmumps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FileMan Tier A smoke (jvmumps#32) — A5/A6/A7 engine gates.
 *
 * <p>A8 (c3fst search) is NOT part of this suite: c3fst is deprecated and JavaM has no c3fst
 * dependency (user, 2026-10-16).
 *
 * <p>GOLDEN: parse a small stable FileMan/toolkit subset from FILEMAN_CORPUS. RUN: FileMan-shaped
 * direct-mode snippets (XLFUTL/XLFSTR idioms), expected values from the PyMumps/C3Mumps
 * fileman_smoke + live RSM.
 *
 * <p>Exit 0 = golden parse clean + all RUN cases MATCH. Failures are classified: ENGINE GAP /
 * DIALECT / DATA.
 */
public final class FileManTierASmoke {

  /** Golden parse subset — small, stable FileMan / toolkit routines. */
  static final String[] GOLDEN = {
    "dictest.m",
    "explore.m",
    "seedpat.m",
    "fileman/DIC0.m",
    "fileman/DILF.m",
    "fileman/DILIBF.m",
    "utils/XLFUTL.m",
    "utils/XLFSTR.m",
  };

  /** (name, code, expected) — FileMan-shaped idioms. */
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
    // A7 — $ORDER / $QUERY on FileMan-shaped keys (numeric, decimal, multi-level)
    // M canonical numeric: 0.01 → ".01" (leading zero stripped). Do not expect "0.01".
    {
      "fm.order-dec", "K ^DD S ^DD(0.81)=\"a\",^DD(0.01)=\"b\" S K=\"\" S K=$O(^DD(K)) W K,!", ".01"
    },
    // FileMan header node 0: ^DIC(1,0) exists, so $O from "" → 0 (not 1).
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

  /**
   * Minimal extrinsic used by fm.extrinsic / fm.extrinsic-byref. Leading Q so top-down run is a
   * no-op.
   */
  static final String EXTRINSIC_ROUTINE = " Q\n" + "P2(Y) ; extrinsic\n" + " S Y=Y_\"ab\" Q Y\n";

  @SuppressWarnings("RedundantThrows")


  static String filemanRoot()  {
    String env = System.getenv("FILEMAN_CORPUS");
    return env != null && !env.isEmpty() ? env : System.getProperty("user.home") + "/_fileman";
  }

  public static void main(String[] args) throws Exception {
    // Makefile smoke passes $(CORPUS) to every *Smoke — only honor a real dir.
    String root = filemanRoot();
    if (args.length > 0 && java.nio.file.Files.isDirectory(Path.of(args[0]))) {
      root = args[0];
    }
    System.out.println("## FileMan Tier A smoke (FILEMAN_CORPUS=" + root + ")");
    System.out.println("### NOTE A8 c3fst search smoke SKIPPED (c3fst deprecated; jvmumps#32)");
    System.out.println();

    int fail = 0;
    fail += runGoldenParse(root);
    System.out.println();
    fail += runCases();
    System.out.println();
    fail += runExtrinsic();

    System.out.println();
    if (fail == 0) {
      System.out.println("  RESULT: FileMan Tier A smoke OK");
      return;
    }
    System.out.println("  RESULT: FileMan Tier A smoke FAILED (" + fail + ")");
    System.exit(1);
  }

  // ---- A5 READ: golden parse ----------------------------------------

  @SuppressWarnings("RedundantThrows")


  static int runGoldenParse(String root)  {
    System.out.println("### READ: routine parse + labels (golden subset)");
    Path base = Path.of(root);
    if (!Files.isDirectory(base)) {
      // CI hosts have no FILEMAN_CORPUS — RUN cases still gate (they are self-contained).
      System.out.println("  SKIP golden parse (no FILEMAN_CORPUS dir: " + root + ")");
      return 0;
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
        String cls = classify(e.toString());
        System.out.printf("  %-28s PARSE ERROR (%s) %s%n", rel, cls, e);
        fail++;
      }
    }
    return fail;
  }

  // ---- RUN cases ----------------------------------------------------

  @SuppressWarnings("RedundantThrows")


  static int runCases()  {
    System.out.println("### FileMan-shaped RUN cases");
    int fail = 0;
    for (String[] c : RUN_CASES) {
      // fresh interpreter per case — no cross-case locals/globals
      fail += runOne(new LineInterp(), c[0], c[1], c[2]);
    }
    return fail;
  }

  @SuppressWarnings("RedundantThrows")


  static int runExtrinsic()  {
    System.out.println("### A6 extrinsic dispatch");
    int fail = 0;
    // one shared interpreter so the loaded routine is visible to $$ calls
    LineInterp m = new LineInterp();
    String load = m.run(EXTRINSIC_ROUTINE);
    if (looksLikeError(load)) {
      System.out.println("  fm.extrinsic-load  ERROR (" + classify(load) + ") " + load.trim());
      return 1;
    }
    fail += runOne(m, "fm.extrinsic", "S X=$$P2(\"z\") W X,!", "zab");
    // ENGINE GAP (jvmumps#32): pass-by-reference args .var in $$ / DO arg lists
    fail += runOne(m, "fm.extrinsic-byref", "S X=$$P2(.Y) W X,!", "ab");
    return fail;
  }

  @SuppressWarnings("RedundantThrows")


  static int runOne(LineInterp m, String name, String code, String want)  {
    String got;
    try {
      got = m.run(code).strip();
    } catch (Exception e) {
      String cls = classify(e.toString());
      System.out.printf("  %-22s ERROR (%s) %s%n", name, cls, e);
      return 1;
    }
    if (looksLikeError(got)) {
      String cls = classify(got);
      System.out.printf("  %-22s ERROR (%s) %s%n", name, cls, got.trim());
      return 1;
    }
    if (want.strip().equals(got)) {
      System.out.printf("  %-22s MATCH  %s%n", name, quote(got));
      return 0;
    }
    String cls = classifyMismatch(code, want, got);
    System.out.printf(
        "  %-22s MISMATCH (%s) exp=%s got=%s%n", name, cls, quote(want.strip()), quote(got));
    return 1;
  }

  // ---- classification ----------------------------------------------

  /**
   * LineInterp.run returns MError text as output (C02). Detect that shape so we do not score an
   * error string as a WRITE mismatch.
   */
  @SuppressWarnings("RedundantThrows")

  static boolean looksLikeError(String got)  {
    if (got == null || got.isEmpty()) {
      return false;
    }
    String g = got.toLowerCase(java.util.Locale.ROOT);
    return g.startsWith("syntax:")
        || g.startsWith("no such label")
        || g.startsWith("undefined")
        || g.startsWith("not available")
        || g.contains("error");
  }

  /**
   * ENGINE GAP — command/intrinsic not implemented or parse hole. DIALECT — FileMan/vendor M that
   * JavaM intentionally does not model yet. DATA — missing file/global/variable (lookup or load
   * problem).
   */
  @SuppressWarnings("RedundantThrows")

  static String classify(String msg)  {
    String m = String.valueOf(msg).toLowerCase(java.util.Locale.ROOT);
    // pass-by-reference args (.var) in $$ / DO arg lists — jvmumps#32 ENGINE GAP
    if (m.contains("op:.") || m.contains("unexpected op:.")) {
      return "ENGINE GAP";
    }
    if (m.contains("$z")
        || m.contains("xcmd")
        || m.contains("vendor")
        || m.contains("osv")
        || m.contains("zish")) {
      return "DIALECT";
    }
    if (m.contains("undefined") || m.contains("missing") || m.contains("no data")) {
      return "DATA";
    }
    if (m.contains("no such label")
        || m.contains("not available")
        || m.contains("syntax:")
        || m.contains("unexpected")) {
      return "ENGINE GAP";
    }
    return "ENGINE GAP";
  }

  @SuppressWarnings("RedundantThrows")


  static String classifyMismatch(String code, String want, String got)  {
    String c = code.toLowerCase(java.util.Locale.ROOT);
    if (c.contains("^") && (c.contains("$d") || c.contains("$o") || c.contains("$g"))) {
      return "DATA";
    }
    if (want.isEmpty() && !got.isEmpty()) {
      return "ENGINE GAP";
    }
    return "DIALECT";
  }

  @SuppressWarnings("RedundantThrows")


  static String quote(String s)  {
    return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
  }

  private FileManTierASmoke() {}
}
