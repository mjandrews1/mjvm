package jvmumps;
public class PatG {
  public static void main(String[] a) {
    // real VistA patterns
    String p1 = ".(.N1\",\")";
    String p2 = ".1(1\"F\",1\"L\",1\"U\",1\"i\",1\"h\",1\"A\")";
    t("12,34,", p1, true);
    t(",", p1, true);
    t("", p1, true);
    t("12,34", p1, false);
    t("A", p2, true);
    t("F", p2, true);
    t("i", p2, true);
    t("X", p2, false);
    t("", p2, true);
    t("FF", p2, false);
    // regression
    t("A", "1A", true);
    t("AB", "2A", true);
    t("hello", "1.A", true);
    t("abc", ".AN", true);
    t("a1", "1A1N", true);
    t("hihi", "2\"hi\"", true);
    t("hello", "1\"hello\"", true);
  }
  static void t(String s, String p, boolean want) {
    boolean got = PatternMatch.match(s, p);
    System.out.println((got==want?"OK  ":"FAIL") + " [" + s + "] ? [" + p + "] -> " + got + " want " + want);
  }
}
