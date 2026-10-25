package jvmumps;

import java.util.List;

/**
 * J0 corpus runner: load shared conformance.json and execute each case.
 *
 * <p>Usage: java jvmumps.CorpusRunner [path/to/conformance.json] Exit 0 = runner completed (J0
 * gate). Match scoring arrives with J1+.
 */
public final class CorpusRunner {
  public static void main(String[] args) throws Exception {
    String path = args.length > 0 ? args[0] : "../pymumps/tests/conformance.json";
    List<CorpusCase> cases = CorpusCase.load(path);
    Engine engine = new StubEngine();
    int n = 0;
    for (CorpusCase c : cases) {
      engine.reset();
      String out = engine.evalLine(c.code);
      n++;
      if (n <= 5) {
        System.out.printf("case %s -> %s%n", c.name, json(out));
      }
    }
    System.out.printf("J0 RUNNER OK cases=%d engine=%s%n", n, engine.getClass().getSimpleName());
  }

  private static String json(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
  }

  private CorpusRunner() {}
}
