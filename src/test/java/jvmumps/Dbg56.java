package jvmumps;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import java.util.List;
public class Dbg56 {
  public static void main(String[] a) {
    String[] codes = {
      "O 2:\"/tmp/x\" U 2 R X U 0 W X,!",
      "L +X W \"a\",! L +X W \"b\",! L -X",
      "S A=0 TSTART S A=1 TROLLBACK W A,!",
      "J \"W 1\" W \"parent\",!",
    };
    for (String c : codes) {
      System.out.println("CODE "+c);
      try {
        List<Ir> p = new MFrontend().compileLine(c);
        for (Ir op : p) System.out.println("  "+op);
      } catch (Exception e) {
        System.out.println("  ERR "+e);
      }
    }
  }
}
