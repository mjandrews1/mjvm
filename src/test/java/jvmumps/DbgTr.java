package jvmumps;

public class DbgTr {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    String line =
        "I \"abc\" W \"abc=T\",! I \"0.5\" W \"0.5=T\",! I \"0abc\" W \"0abc=T\",! I \"1abc\" W"
            + " \"1abc=T\",! I \" 1\" W \"sp1=T\",! I \"1 \" W \"1sp=T\",! I \"-0\" W \"m0=T\",! I"
            + " \"\" W \"empty=T\",! I \"0.0\" W \"0.0=T\",! I \"1E2\" W \"1E2=T\",! I -0.5 W"
            + " \"m0.5=T\",!";
    System.out.println("ALL[" + m.run(line) + "]");
    // binary search
    String[] parts = {
      "I \"abc\" W \"abc=T\",! I \"0.5\" W \"0.5=T\",!",
      "I \"0abc\" W \"0abc=T\",! I \"1abc\" W \"1abc=T\",!",
      "I \" 1\" W \"sp1=T\",! I \"1 \" W \"1sp=T\",!",
      "I \"-0\" W \"m0=T\",! I \"\" W \"empty=T\",!",
      "I \"0.0\" W \"0.0=T\",! I \"1E2\" W \"1E2=T\",! I -0.5 W \"m0.5=T\",!"
    };
    for (String p : parts) {
      System.out.println("P[" + m.run(p) + "] from " + p.substring(0, Math.min(30, p.length())));
    }
  }
}
