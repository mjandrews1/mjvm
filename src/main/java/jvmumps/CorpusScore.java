package jvmumps;

import java.util.List;

/**
 * Score a corpus prefix (default C01–C39) against LineEngine. Usage: java jvmumps.CorpusScore
 * [path] [prefix]
 */
public final class CorpusScore {
  public static void main(String[] args) throws Exception {
    String path = args.length > 0 ? args[0] : "../pymumps/tests/conformance.json";
    String prefix = args.length > 1 ? args[1] : "C";
    List<CorpusCase> cases = CorpusCase.load(path);
    Engine eng = new LineEngine();
    int match = 0, miss = 0, err = 0;
    for (CorpusCase c : cases) {
      boolean use =
          "*".equals(prefix)
              || prefix.equals("")
              || c.name.equals(prefix)
              || c.name.startsWith(prefix);
      if (!use) {
        continue;
      }
      eng.reset();
      String got;
      try {
        got = eng.evalLine(c.code);
      } catch (RuntimeException e) {
        err++;
        System.out.println("ERR  " + c.name + " " + e);
        continue;
      }
      if (normalize(got).equals(normalize(c.expected))) {
        match++;
      } else {
        miss++;
        // print every miss — the old cap hid 27 of 42 gaps
        System.out.println("MISS " + c.name);
        System.out.println("  code " + c.code.replace('\n', ' '));
        System.out.println("  got  " + q(got));
        System.out.println("  want " + q(c.expected));
      }
    }
    System.out.printf("J2 CORPUS %s MATCH=%d MISS=%d ERR=%d%n", prefix, match, miss, err);
  }

  @SuppressWarnings("RedundantThrows")
  static String normalize(String s) {
    // Corpus expecteds omit trailing newlines from WRITE !
    String t = s.replace("\r\n", "\n");
    while (t.endsWith("\n")) {
      t = t.substring(0, t.length() - 1);
    }
    return t;
  }

  @SuppressWarnings("RedundantThrows")
  static String q(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
  }
}
