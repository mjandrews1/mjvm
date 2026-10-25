package jvmumps;

/**
 * jvmumps#3 — PatternMatch vs ISO 11756 / RSM differential. ISO wants are independent of
 * PatternMatch; engine is measured against them.
 *
 * <p>High-risk matrix from issue #3: full-string (C29) · empty literals · counts n .m n.m n. . ·
 * class E vs A/N/P
 */
public final class PatternMatchISODiff {
  static int ok = 0, bad = 0;

  static void chk(String s, String pat, boolean want, String tag, String rule) {
    boolean got = PatternMatch.match(s, pat);
    if (got != want) {
      bad++;
      System.out.println(
          "DIVERGE "
              + tag
              + " ["
              + rule
              + "] "
              + j(s)
              + "?"
              + j(pat)
              + " got="
              + got
              + " want="
              + want);
    } else {
      ok++;
    }
  }

  static String j(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public static void main(String[] a) {
    // --- R1 full-string ---
    chk("a!", "1A.AN", false, "C29a", "R1 full-string leftover");
    chk("a", "1A.AN", true, "C29b", "R1");
    chk("ab", "1A.AN", true, "C29c", "R1");
    chk("a1", "1A.AN", true, "C29d", "R1");
    chk("abc", "1\"ab\"", false, "R1-lit-short", "R1 leftover c");
    chk("ab", "1\"ab\"", true, "R1-lit-exact", "R1");
    chk("hello", "1\"hell\"", false, "C28b", "R1 leftover o");
    chk("hello", "1\"hello\"", true, "C28a", "R1");
    chk("hihi", "2\"hi\"", true, "C28c", "R2 exact 2");

    // --- R3 exact-0 / empty literal ---
    chk("a", "0\"a\"", false, "e0a", "R3 0X empty only");
    chk("", "0\"a\"", true, "e0b", "R3");
    chk("aa", "0\"a\"1\"a\"", false, "e0c", "R3 leftover");
    chk("", "0\"a\"0\"b\"", true, "e0d", "R3×2");
    chk("x", "0\"x\"", false, "e0e", "R3");
    chk("", "0\"x\"", true, "e0f", "R3");
    chk("1", "0N", false, "e0g", "R3 class 0");
    chk("", "0N", true, "e0h", "R3");
    chk("1", "0N1N", true, "e0i", "R2/R3 0N then 1N");
    chk("", "0N1N", false, "e0j", "R2 1N needs a digit");
    chk("", "1A.1N", false, "e0k", "R2 1A needs letter");
    chk("", "0A0N", true, "e0l", "R3×2");
    chk("", "0\"\"", true, "e0m", "R3 empty lit exact-0");
    chk("x", "0\"\"1\"x\"", true, "e0n", "R3 empty lit 0 then 1\"x\"");
    chk("", "0\"\"", true, "e0o", "R3");

    // --- R2 count forms ---
    chk("abc", "3A", true, "n1", "R2 exact n");
    chk("ab", "3A", false, "n2", "R2");
    chk("abc", "2.3A", true, "nm1", "R2 n.m");
    chk("ab", "2.3A", true, "nm2", "R2");
    chk("abcd", "2.3A", false, "nm3", "R2");
    chk("a", "2.3A", false, "nm4", "R2");
    chk("a", ".1A", true, "dm1", "R2 .n");
    chk("aa", ".1A", false, "dm2", "R2");
    chk("", ".1A", true, "dm3", "R2");
    chk("", ".A", true, "dot1", "R2 . = 0..∞ (0 matches empty)");
    chk("abc", ".A", true, "dot2", "R2");
    chk("a", "2.A", false, "nd1", "R2 n.");
    chk("aa", "2.A", true, "nd2", "R2");
    chk("aaaa", "2.A", true, "nd3", "R2");
    chk("a", "1.1A", true, "nm5", "R2 n.m with n=m");
    chk("aa", "1.1A", false, "nm6", "R2");
    chk("hello", "1.5E", true, "C27-1.5E", "R2 n.m class E");
    chk("hello", "1E", false, "C27-1E", "R2 exact 1 vs len 5");
    chk("hello", "5E", true, "C27-5E", "R2 exact 5");
    chk("", "1A", false, "C27-1A", "R2");
    chk("", ".A", true, "C27-dotA", "R2");

    // --- R4/R5 classes ---
    chk("a", "1A", true, "clsA", "R4");
    chk("1", "1N", true, "clsN", "R4");
    chk("!", "1P", true, "clsP", "R4");
    chk("!", "1A", false, "clsA2", "R4");
    chk("5", "1P", false, "clsP2", "R4");
    chk("x", "1AN", true, "clsAN1", "R5 alternation");
    chk("1", "1AN", true, "clsAN2", "R5");
    chk("!", "1AN", false, "clsAN3", "R5");
    chk("h", "1E", true, "clsE1", "R4 E any");
    chk("!", "1E", true, "clsE2", "R4");
    chk("1", "1E", true, "clsE3", "R4");
    chk("", "1E", false, "clsE4", "R4");
    chk("A1b", "1A1N1A", true, "mix1", "R4");
    chk("a1b", "1A1N1A", true, "mix2", "R4");
    chk("1a", "1N1A", true, "mix3", "R4");
    chk("ab", "1A1A1A", false, "mix4", "R2");

    // --- multi-atom ---
    chk("hello", "1\"he\"1\"llo\"", true, "ma1", "R1");
    chk("xyz", "1A.1A.1A", true, "ma2", "R2");
    chk("ab", ".A.1A", true, "ma3", "R2");
    chk("x", "1\"x\"0\"y\"", true, "ma4", "R3");

    // --- long unbounded n. (engine max=64 trap) ---
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100; i++) sb.append('a');
    chk(sb.toString(), "1.A", true, "long1.", "R2 n. must cover subject");
    chk(sb.toString(), "0.A", true, "long0.", "R2 . unbounded");
    sb = new StringBuilder();
    for (int i = 0; i < 70; i++) sb.append('1');
    chk(sb.toString(), "1.N", true, "long1.N", "R2 n. >=70");

    // --- empty literal with nonzero count (0-width occurrences) ---
    chk("ab", "2\"\"1A1\"b\"", true, "elit0w", "R4 empty lit is 0-width");
    chk("", "1\"\"", true, "elit1", "R4 empty lit matches empty subject once");
    chk("a", "1\"\"1\"a\"", true, "elit2", "R4");

    System.out.println("PatternMatch ISO diff  OK=" + ok + "  DIVERGE=" + bad);
    if (bad > 0) System.exit(1);
    System.out.println("PatternMatch ISO diff PASS");
  }
}
