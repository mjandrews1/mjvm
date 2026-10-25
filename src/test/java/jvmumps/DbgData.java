package jvmumps;

public class DbgData {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    System.out.println("set[" + m.run("S ^CT4(1)=1") + "]");
    System.out.println("set2[" + m.run("S ^CT4(1,2)=2") + "]");
    System.out.println("get[" + m.run("W $D(^CT4(1)),!") + "]");
    System.out.println("rawget[" + m.run("W ^CT4(1),!") + "]");
  }
}
