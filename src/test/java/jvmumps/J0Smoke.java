package jvmumps;

/** J0 smoke: MValue + StubEngine + corpus load (no JUnit dep). */
public final class J0Smoke {
  public static void main(String[] args) throws Exception {
    MValue n = MValue.ofNumber(2);
    MValue s = MValue.ofString("2");
    check(n.isnum && n.asStr().equals("2"), "num format");
    check(!s.isnum && s.asNum() == 2.0, "str->num");
    check(MValue.EMPTY.asStr().isEmpty(), "empty");

    Engine e = new StubEngine();
    check(e.evalLine("S X=1").isEmpty(), "set");
    check(e.evalLine("W \"hi\",!").equals("hi\n"), "write");

    int count = CorpusCase.load(CorpusCase.corpusPath(args)).size();
    check(count > 50, "corpus cases=" + count);

    System.out.println("J0 SMOKE OK cases=" + count);
  }

  private static void check(boolean ok, String what) {
    if (!ok) {
      throw new IllegalStateException("J0 smoke failed: " + what);
    }
  }
}
