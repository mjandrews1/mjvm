package jvmumps;

/**
 * M collation order for subscripts (mjvm#40): a subscript that is a canonical numeric string
 * (parses as a number and equals its canonical form — no leading zeros, no trailing zeros, no
 * exponent) sorts numerically and before every non-numeric string; non-numeric strings sort by
 * UTF-16 code unit order. This matches GT.M: $O over {"9","0011012","1264327","7502500"} yields 9,
 * 1264327, 7502500, 0011012.
 */
public final class MCollation {
  private MCollation() {}

  /** True iff s is a canonical M numeric string. */
  public static boolean isCanonicalNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    try {
      java.math.BigDecimal bd = new java.math.BigDecimal(s);
      return s.equals(bd.stripTrailingZeros().toPlainString());
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** M collation compare: canonical numerics first (numeric order), then strings by code unit. */
  public static int compare(String a, String b) {
    boolean an = isCanonicalNumeric(a);
    boolean bn = isCanonicalNumeric(b);
    if (an && bn) {
      return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b));
    }
    if (an) return -1;
    if (bn) return 1;
    return a.compareTo(b);
  }
}
