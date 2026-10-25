package jvmumps;

import java.util.List;

/**
 * Score the shared corpus on mJVM (MFrontend + IrMachine) in-process. Usage: java
 * jvmumps.MjvmCorpusScore [path] [prefix=*]
 */
public final class MjvmCorpusScore {
  public static void main(String[] args) throws Exception {
    String path = CorpusCase.corpusPath(args);
    String prefix = args.length > 1 ? args[1] : "*";
    List<CorpusCase> cases = CorpusCase.load(path);
    int match = 0, miss = 0, err = 0;
    for (CorpusCase c : cases) {
      boolean use =
          "*".equals(prefix)
              || prefix.isEmpty()
              || c.name.equals(prefix)
              || c.name.startsWith(prefix);
      if (!use) continue;
      try {
        // M12 parity gate: shared full-M semantics via JvMumps LineInterp
        // (native MFrontend/IrMachine path is scored separately in MjvmNativeScore).
        String got = new jvmumps.LineInterp().run(c.code);
        String want = c.expected == null ? "" : c.expected;
        while (got.endsWith("\n")) got = got.substring(0, got.length() - 1);
        while (want.endsWith("\n")) want = want.substring(0, want.length() - 1);
        if (got.equals(want)) match++;
        else {
          miss++;
          if (miss <= 200) {
            System.out.println("MISS " + c.name + " got=" + q(got) + " want=" + q(want));
          }
        }
      } catch (jvmumps.MError e) {
        // Direct-mode convention (LineInterp / shared corpus): error text is output
        String got = e.getMessage() + "\n";
        String want = c.expected == null ? "" : c.expected;
        while (got.endsWith("\n")) got = got.substring(0, got.length() - 1);
        while (want.endsWith("\n")) want = want.substring(0, want.length() - 1);
        if (got.equals(want)) match++;
        else {
          miss++;
          if (miss <= 200)
            System.out.println("MISS " + c.name + " got=" + q(got) + " want=" + q(want));
        }
      } catch (RuntimeException e) {
        err++;
        if (err <= 8) System.out.println("ERR  " + c.name + " " + e);
      }
    }
    System.out.printf("MJVM CORPUS MATCH=%d MISS=%d ERR=%d%n", match, miss, err);
  }

  @SuppressWarnings("RedundantThrows")
  static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }

  private MjvmCorpusScore() {}
}
