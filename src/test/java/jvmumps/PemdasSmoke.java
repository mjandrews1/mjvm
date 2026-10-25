package jvmumps;

/** $$PEMDAS opt-in helper (m-semantics#31) — separate from conformance corpus. */
public final class PemdasSmoke {
  static int n = 0;

  static void eq(double got, double want, String tag) {
    if (Math.abs(got - want) > 1e-9) {
      throw new IllegalStateException(tag + " got=" + got + " want=" + want);
    }
    n++;
  }

  static void eqStr(String got, String want, String tag) {
    if (!want.equals(got)) {
      throw new IllegalStateException(tag + " got=" + got + " want=" + want);
    }
    n++;
  }

  public static void main(String[] args) {
    // PEMDAS: mul binds tighter
    eq(PemdasEval.eval("2+3*4").asNum(), 14.0, "2+3*4");
    eq(PemdasEval.eval("(2+3)*4").asNum(), 20.0, "(2+3)*4");
    // power right-assoc: 2**3**2 = 512
    eq(PemdasEval.eval("2**3**2").asNum(), 512.0, "2**3**2");
    // unary minus vs power
    eq(PemdasEval.eval("-2**2").asNum(), -4.0, "-2**2");
    // left-assoc mul/div
    eq(PemdasEval.eval("8/4/2").asNum(), 1.0, "8/4/2");
    // div by zero => 0 (M convention)
    eq(PemdasEval.eval("1/0").asNum(), 0.0, "1/0");
    // whitespace / parens
    eq(PemdasEval.eval(" ( 2 + 3 ) * 4 ").asNum(), 20.0, "spaces");
    // $$PEMDAS via LineInterp extrinsic
    LineInterp m = new LineInterp();
    String out = m.run("W $$PEMDAS(\"2+3*4\"),!");
    eqStr(out, "14\n", "LineInterp $$PEMDAS");
    // default M precedence must NOT change (corpus rule)
    LineInterp m2 = new LineInterp();
    String mout = m2.run("W 2+3*4,!");
    // M: 2+3*4 is (2+3)*4 = 20 in M parse? actually M + then * is same level left: (2+3)*4=20
    // If this ever flips, 131/8/0 is at risk.
    eqStr(mout, "20\n", "default M 2+3*4");
    System.out.println("PEMDAS SMOKE OK cases=" + n);
  }
}
