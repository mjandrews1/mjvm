package jvmumps;

/** M value: numeric or string (ISO); empty string is the default. */
public final class MValue {
  public final double num;
  public final boolean isnum;

  /**
   * mjvm#14: lazy for numeric values — {@link #ofNumber} no longer allocates an mstr string per
   * numeric result (the top hot-loop cost after slot caching); it is materialized on first {@link
   * #asStr()} call. Eager for string values. Benign race: idempotent recompute.
   */
  private volatile String str;

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
   * mjvm#14: boxed small-integer cache. Hot interpreter loops allocate a fresh MValue for every
   * numeric pop/push; MValue is immutable so sharing is safe.
   */
  private static final MValue[] INT_CACHE = new MValue[2305]; // -256 .. 2048

  static {
    for (int i = 0; i < INT_CACHE.length; i++) {
      long v = i - 256;
      INT_CACHE[i] = new MValue((double) v, null, true);
    }
  }

  public static MValue ofNumber(double d) {
    if (d == Math.rint(d) && d >= -256 && d <= 2048) {
      return INT_CACHE[(int) d + 256];
    }
    return new MValue(d, null, true); // str lazy
  }

  public String asStr() {
    String s = str;
    if (s == null) {
      s = ExprEval.mstr(num);
      str = s;
    }
    return s;
  }

  public double asNum() {
    String s = str;
    return isnum ? num : parseNum(s == null ? ExprEval.mstr(num) : s);
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
