package jvmumps;

public class T14b {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    String[] steps = {
      "K ^CT14",
      "S ^CT14(1)=\"a\"",
      "S ^CT14(1)=\"a\",^CT14(2)=\"b\",^CT14(10)=\"c\"",
      "S K=\"\"",
      "S K=$O(^CT14(K))",
      "W \"f=\",K,!",
      "S K=$O(^CT14(K))",
      "W \"s=\",K,!",
    };
    for (String s : steps) {
      System.err.println("STEP " + s);
      String r = m.run(s);
      System.err.println("  -> " + r.replace("\n", "\\n"));
    }
    System.err.println("done");
  }
}
