package jvmumps;
public class T14c {
  static void t(LineInterp m, String s) {
    System.err.println("IN  "+s);
    String r = m.run(s);
    System.err.println("OUT "+r.replace("\n","\\n"));
  }
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    t(m, "S A=1");
    t(m, "S A=1,B=2");
    t(m, "S ^G(1)=\"a\"");
    t(m, "S ^G(1)=\"a\",^G(2)=\"b\"");
    t(m, "S ^G(1)=\"a\",^G(2)=\"b\",^G(10)=\"c\"");
    t(m, "W $O(^G(\"\")),!");
    System.err.println("done");
  }
}
