package jvmumps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * FileMan corpus walk (jvmumps#32) — parse every *.m under FILEMAN_CORPUS, classify failures as
 * ENGINE GAP / DIALECT / DATA. Read-only; no store writes.
 *
 * <p>Usage: jvmumps.FileManCorpusWalk [--legacy] [FILEMAN_CORPUS] --legacy use LineRoutine parser
 * (deprecated, frozen) Default: MFrontend (native mJVM) — the unified compiler (#197) Exit 0 always
 * (report is the product). Set WALK_LIMIT to cap files (0 = all).
 */
public final class FileManCorpusWalk {

  @SuppressWarnings("RedundantThrows")


  static String root()  {
    String env = System.getenv("FILEMAN_CORPUS");
    return env != null && !env.isEmpty() ? env : System.getProperty("user.home") + "/_fileman";
  }

  public static void main(String[] args) throws Exception {
    boolean nativeMode = true;
    String base = root();
    for (String a : args) {
      if (a.equals("--legacy")) nativeMode = false;
      else if (Files.isDirectory(Path.of(a))) base = a;
    }
    if (!Files.isDirectory(Path.of(base))) {
      System.out.println("## FileMan corpus walk: SKIP (no FILEMAN_CORPUS dir: " + base + ")");
      return;
    }
    int limit = Integer.parseInt(System.getenv().getOrDefault("WALK_LIMIT", "0"));
    String engine = nativeMode ? "MFrontend (unified #197)" : "LineRoutine (deprecated)";
    System.out.println("## FileMan corpus walk (FILEMAN_CORPUS=" + base + ")");
    System.out.println("### parse via " + engine + " — read-only");
    System.out.println();

    List<Path> files = new ArrayList<>();
    try (Stream<Path> s = Files.walk(Path.of(base))) {
      s.filter(p -> p.getFileName().toString().endsWith(".m")).sorted().forEach(files::add);
    }
    System.out.println("  .m files found: " + files.size());

    int ok = 0, empty = 0, fail = 0, scanned = 0;
    List<String> fails = new ArrayList<>();
    for (Path p : files) {
      if (limit > 0 && scanned >= limit) {
        break;
      }
      scanned++;
      String code;
      try {
        code = Files.readString(p);
      } catch (IOException e) {
        fail++;
        fails.add(rel(base, p) + "  READ (DATA) " + e);
        continue;
      }
      try {
        if (nativeMode) {
          var ir = new mjvm.compiler.MFrontend().compileProgram(code);
          if (ir.isEmpty()) {
            empty++;
            fails.add(rel(base, p) + "  NO OPS (DIALECT)");
          } else {
            ok++;
          }
        } else {
          List<LineState.RtLine> lines = LineRoutine.parseRoutine(code);
          int nlab = 0;
          for (LineState.RtLine tl : lines) {
            if (tl.tag != null && !tl.tag.isEmpty()) {
              nlab++;
            }
          }
          if (nlab == 0) {
            empty++;
            fails.add(rel(base, p) + "  NO LABELS (DIALECT)");
          } else {
            ok++;
          }
        }
      } catch (Exception e) {
        fail++;
        fails.add(rel(base, p) + "  PARSE (" + FileManTierASmoke.classify(e.toString()) + ") " + e);
      }
    }

    System.out.println();
    System.out.println("### failures / oddities (" + fails.size() + ")");
    int shown = 0;
    for (String f : fails) {
      if (shown++ >= 40) {
        System.out.println("  … " + (fails.size() - 40) + " more (raise via full log)");
        break;
      }
      System.out.println("  " + f);
    }

    System.out.println();
    System.out.printf("  scanned=%d ok=%d no-labels=%d fail=%d%n", scanned, ok, empty, fail);
    System.out.println("  RESULT: corpus walk complete");
  }

  @SuppressWarnings("RedundantThrows")


  static String rel(String base, Path p)  {
    return Path.of(base).relativize(p).toString();
  }

  private FileManCorpusWalk() {}
}
