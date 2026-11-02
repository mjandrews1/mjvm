package jvmumps;

/** M value: numeric or string (ISO); empty string is the default. */
public final class MValue {
  public final double num;
  public final String str;
  public final boolean isnum;

  public static final MValue EMPTY = new MValue(0.0, "", false);

  private MValue(double num, String str, boolean isnum) {
    this.num = num;
    this.str = str;
    this.isnum = isnum;
  }

  public static MValue ofString(String s) {
    return new MValue(0.0, s == null ? "" : s, false);
  }

  /**
   * mjvm#14: boxed small-integer cache. Hot interpreter loops allocate a fresh MValue (plus an mstr
   * string) for every numeric pop/push; MValue is immutable so sharing is safe, and for integral
   * values in range the cached str equals {@link ExprEval#mstr} exactly.
   */
  private static final MValue[] INT_CACHE = new MValue[2305]; // -256 .. 2048

  static {
    for (int i = 0; i < INT_CACHE.length; i++) {
      long v = i - 256;
      INT_CACHE[i] = new MValue((double) v, Long.toString(v), true);
    }
  }

  public static MValue ofNumber(double d) {
    if (d == Math.rint(d) && d >= -256 && d <= 2048) {
      return INT_CACHE[(int) d + 256];
    }
    return new MValue(d, ExprEval.mstr(d), true);
  }

  public String asStr() {
    return str;
  }

  public double asNum() {
    return isnum ? num : parseNum(str);
  }

  static String formatNum(double d) {
    if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
      return Long.toString((long) d);
    }
    return Double.toString(d);
  }

  static double parseNum(String s) {
    if (s == null || s.isEmpty()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  @Override
  public String toString() {
    return isnum ? str : "\"" + str + "\"";
  }
}
