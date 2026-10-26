package jvmumps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * VistA full-run harness (jvmumps#34) — parse every routine under the FOIA VistA corpus, classify
 * failures, report per-package metrics.
 *
 * <p>Usage: jvmumps.VistAFullRun [--legacy] [VISTA_CORPUS] --native run through MFrontend (native
 * mJVM) instead of LineRoutine Env: VISTA_CORPUS root dir (default ~/vista-foia/VistA-M-master)
 * WALK_LIMIT cap files (0 = all) VISTA_EXECUTE 1 = also smoke-execute tags after parse Exit 0
 * always (report is the product).
 */
public final class VistAFullRun {

  @SuppressWarnings("RedundantThrows")
  static String root() {
    String env = System.getenv("VISTA_CORPUS");
    return env != null && !env.isEmpty()
        ? env
        : System.getProperty("user.home") + "/vista-foia/VistA-M-master";
  }

  /** Per-package tallies. */
  static final class Pkg {
    int ok, empty, fail, total, swallow;
    final List<String> fails = new ArrayList<>();
    long nanos;
  }

  public static void main(String[] args) throws Exception {
    boolean nativeMode = false;
    boolean execute = "1".equals(System.getenv("VISTA_EXECUTE"));
    String baseArg = root();
    for (String a : args) {
      if (a.equals("--native")) nativeMode = true;
      else if (a.equals("--execute")) execute = true;
      else if (Files.isDirectory(Path.of(a))) baseArg = a;
    }
    final String base = baseArg;
    if (!Files.isDirectory(Path.of(base))) {
      System.out.println("## VistA full run: SKIP (no VISTA_CORPUS dir: " + base + ")");
      return;
    }
    int limit = Integer.parseInt(System.getenv().getOrDefault("WALK_LIMIT", "0"));
    String engine = nativeMode ? "MFrontend (native mJVM)" : "LineRoutine.parseRoutine (JavaM)";
    System.out.println("## VistA full run (VISTA_CORPUS=" + base + ")");
    System.out.println("### parse via " + engine + " — read-only" + (execute ? " + execute" : ""));
    System.out.println();

    // Collect .m file paths grouped by package — use ArrayList not sorted stream
    Map<String, List<Path>> byPkg = new TreeMap<>();
    try (Stream<Path> s = Files.walk(Path.of(base))) {
      s.filter(p -> p.getFileName().toString().endsWith(".m"))
          .forEach(
              p -> {
                String pkg = packageOf(base, p);
                byPkg.computeIfAbsent(pkg, k -> new ArrayList<>()).add(p);
              });
    }
    int totalFound = byPkg.values().stream().mapToInt(List::size).sum();
    System.out.println("  packages: " + byPkg.size());
    System.out.println("  .m files found: " + totalFound);
    System.out.println();

    Map<String, Pkg> stats = new LinkedHashMap<>();
    Map<String, Integer> swallowClasses = new TreeMap<>();
    int scanned = 0;
    for (Map.Entry<String, List<Path>> ent : byPkg.entrySet()) {
      String pkg = ent.getKey();
      Pkg ps = new Pkg();
      stats.put(pkg, ps);
      for (Path p : ent.getValue()) {
        if (limit > 0 && scanned >= limit) break;
        scanned++;
        ps.total++;
        if (scanned % 2000 == 0) System.gc();
        String code;
        try {
          code = Files.readString(p);
        } catch (IOException e) {
          ps.fail++;
          ps.fails.add(rel(base, p) + "  READ (DATA) " + e.getMessage());
          continue;
        }
        long t0 = System.nanoTime();
        try {
          if (nativeMode) {
            CompileResult res = compileWithGuard(code);
            if (res == null) {
              ps.fail++;
              ps.fails.add(rel(base, p) + "  PARSE (ENGINE GAP) StackOverflow in compiler");
            } else if (res.ir().isEmpty()) {
              ps.empty++;
              ps.fails.add(rel(base, p) + "  NO OPS (DIALECT)");
            } else if (!res.swallowed().isEmpty()) {
              // Compiled, but the routine builder replaced error lines with
              // RET — previously invisible; surfaced via the ledger (2026-10-26).
              ps.ok++;
              ps.swallow++;
              for (String sw : res.swallowed()) {
                swallowClasses.merge(swallowClassOf(sw), 1, Integer::sum);
              }
              ps.fails.add(
                  rel(base, p)
                      + "  SWALLOWED (LEDGER) "
                      + res.swallowed().size()
                      + " — "
                      + truncateName(String.valueOf(res.swallowed().get(0))));
            } else {
              ps.ok++;
            }
          } else {
            List<LineState.RtLine> lines = LineRoutine.parseRoutine(code);
            int nlab = 0;
            for (LineState.RtLine tl : lines) {
              if (tl.tag != null && !tl.tag.isEmpty()) nlab++;
            }
            if (nlab == 0) {
              ps.empty++;
              ps.fails.add(rel(base, p) + "  NO LABELS (DIALECT)");
            } else {
              ps.ok++;
            }
          }
        } catch (Exception | StackOverflowError | OutOfMemoryError e) {
          ps.fail++;
          String cls =
              e instanceof StackOverflowError
                  ? "ENGINE GAP"
                  : FileManTierASmoke.classify(e.toString());
          ps.fails.add(rel(base, p) + "  PARSE (" + cls + ") " + truncate(String.valueOf(e)));
        }
        ps.nanos += System.nanoTime() - t0;
      }
      if (limit > 0 && scanned >= limit) break;
    }

    // Summary
    int totOk = 0, totEmpty = 0, totFail = 0, totFiles = 0, totSwallow = 0;
    long totNanos = 0;
    Map<String, Integer> failClasses = new TreeMap<>();
    for (Pkg ps : stats.values()) {
      totOk += ps.ok;
      totEmpty += ps.empty;
      totFail += ps.fail;
      totFiles += ps.total;
      totSwallow += ps.swallow;
      totNanos += ps.nanos;
      for (String f : ps.fails) {
        String cls = "OTHER";
        if (f.contains("ENGINE GAP")) cls = "ENGINE GAP";
        else if (f.contains("SWALLOWED (LEDGER)")) cls = "SWALLOWED";
        else if (f.contains("DIALECT")) cls = "DIALECT";
        else if (f.contains("DATA")) cls = "DATA";
        failClasses.merge(cls, 1, Integer::sum);
      }
    }

    System.out.println("### per-package summary");
    System.out.printf(
        "  %-40s %5s %5s %5s %5s  %s%n", "package", "total", "ok", "empty", "fail", "time");
    System.out.println("  " + "-".repeat(85));
    for (Map.Entry<String, Pkg> ent : stats.entrySet()) {
      Pkg ps = ent.getValue();
      if (ps.total == 0) continue;
      System.out.printf(
          "  %-40s %5d %5d %5d %5d  %6.1fs%n",
          truncateName(ent.getKey()), ps.total, ps.ok, ps.empty, ps.fail, ps.nanos / 1e9);
    }

    System.out.println();
    System.out.println("### swallowed-class histogram (ledger entries)");
    if (swallowClasses.isEmpty()) {
      System.out.println("  (none)");
    } else {
      int totSw = swallowClasses.values().stream().mapToInt(Integer::intValue).sum();
      for (Map.Entry<String, Integer> e : swallowClasses.entrySet()) {
        System.out.printf(
            "  %-42s %7d  (%.1f%%)%n",
            e.getKey(), e.getValue(), 100.0 * e.getValue() / Math.max(1, totSw));
      }
    }

    System.out.println();
    System.out.println("### failure classification");
    if (failClasses.isEmpty()) {
      System.out.println("  (none)");
    } else {
      for (Map.Entry<String, Integer> e : failClasses.entrySet()) {
        System.out.printf("  %-12s %d%n", e.getKey(), e.getValue());
      }
    }

    System.out.println();
    System.out.println("### sample failures (first 40)");
    int shown = 0;
    for (Pkg ps : stats.values()) {
      for (String f : ps.fails) {
        if (shown++ >= 40) {
          System.out.println("  … (use per-package output for full list)");
          break;
        }
        System.out.println("  " + f);
      }
      if (shown >= 40) break;
    }

    System.out.println();
    System.out.printf(
        "  scanned=%d ok=%d no-labels=%d fail=%d swallowed=%d%n",
        totFiles, totOk, totEmpty, totFail, totSwallow);
    if (totSwallow > 0) {
      System.out.println(
          "  NOTE: 'swallowed' = routines that compiled but had error lines replaced with RET —"
              + " silent under the old engine, visible via the ledger since v2.2.1.");
    }
    System.out.printf(
        "  parse_time=%.1fs avg=%.3fms/routine%n",
        totNanos / 1e9, totFiles > 0 ? totNanos / 1e6 / totFiles : 0);
    double pct = totFiles > 0 ? 100.0 * totOk / totFiles : 0;
    System.out.printf("  success_rate=%.2f%%%n", pct);
    System.out.println("  RESULT: VistA full run complete");
  }

  @SuppressWarnings("RedundantThrows")
  static String packageOf(String base, Path p) {
    // .../Packages/<PackageName>/Routines/<file>.m → <PackageName>
    Path rel = Path.of(base).relativize(p);
    String[] parts = rel.toString().split("/");
    for (int i = 0; i < parts.length - 1; i++) {
      if (parts[i].equals("Routines") && i > 0) return parts[i - 1];
    }
    return "(root)";
  }

  @SuppressWarnings("RedundantThrows")
  static String rel(String base, Path p) {
    return Path.of(base).relativize(p).toString();
  }

  @SuppressWarnings("RedundantThrows")
  static String truncate(String s) {
    if (s == null) return "";
    return s.length() > 120 ? s.substring(0, 120) + "…" : s;
  }

  /** Compile with OOM/stack guard — returns null on overflow. */
  record CompileResult(java.util.List<mjvm.ir.Ir> ir, java.util.List<String> swallowed) {}

  /** Bucket a ledger entry into a syntax class for the backlog histogram. */
  static String swallowClassOf(String ledgerEntry) {
    int at = ledgerEntry.indexOf("expected command at ");
    if (at >= 0) {
      String rest = ledgerEntry.substring(at + "expected command at ".length()).trim();
      if (rest.startsWith(".")) return "dotted-line continuation (.)";
      if (rest.startsWith("@")) return "argument indirection (@)";
      if (rest.startsWith(",")) return "continuation comma (,)";
      if (rest.startsWith(":$") || rest.startsWith(":")) return "post-conditional (:)";
      if (rest.startsWith("$$") || rest.startsWith("$")) return "function/intrinsic ($)";
      return "other token: '" + (rest.isEmpty() ? "" : rest.charAt(0)) + "'";
    }
    if (ledgerEntry.contains("SET target must be a name")) return "SET target must be a name";
    if (ledgerEntry.contains("depth limit")) return "compile depth limit";
    if (ledgerEntry.contains("Undefined local variable")) return "undefined local";
    if (ledgerEntry.length() > 60) return ledgerEntry.substring(0, 57) + "…";
    return ledgerEntry;
  }

  static CompileResult compileWithGuard(String code) {
    try {
      var fe = new mjvm.compiler.MFrontend();
      return new CompileResult(fe.compileProgram(code), fe.lastSwallowedErrors());
    } catch (StackOverflowError | OutOfMemoryError e) {
      return null;
    }
  }

  @SuppressWarnings("RedundantThrows")
  static String truncateName(String s) {
    return s.length() > 40 ? s.substring(0, 37) + "…" : s;
  }

  private VistAFullRun() {}
}
