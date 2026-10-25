package jvmumps;

public class T14 {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    System.out.println("set: " + m.run("S ^CT14(1)=\"a\" S ^CT14(2)=\"b\" S ^CT14(10)=\"c\""));
    System.out.println("o0: " + m.run("S K=\"\" W $O(^CT14(K)),!"));
    System.out.println("o1: " + m.run("W $O(^CT14(K)),!"));
    System.out.println("done");
  }
}
