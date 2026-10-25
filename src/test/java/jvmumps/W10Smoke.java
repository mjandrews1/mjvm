package jvmumps;

public class W10Smoke {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    check(m, "W (\"abc\"?.A),!", "1\n");
    check(m, "W (\"\"?0.A),!", "1\n");
    check(m, "W (\"a1\"?1A1N),!", "1\n");
    check(m, "W (\"xy\"?2A),!", "1\n");
    check(m, "W (\"x\"?2A),!", "0\n");
    System.out.println("W10 PATTERNS OK");
  }

  static void check(LineInterp m, String code, String want) {
    String got = m.run(code);
    if (!want.equals(got)) throw new IllegalStateException(code + " got " + got + " want " + want);
  }
}
