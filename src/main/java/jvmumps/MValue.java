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

  public static MValue ofNumber(double d) {
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
