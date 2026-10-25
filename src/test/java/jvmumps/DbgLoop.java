package jvmumps;

import java.util.List;
import mjvm.bench.MjvmBench;
import mjvm.ir.Ir;
import mjvm.runtime.*;

public class DbgLoop {
  public static void main(String[] a) throws Exception {
    List<Ir> p = MjvmBench.loopProg(5, "inc");
    for (int i = 0; i < p.size(); i++) System.out.println(i + " " + p.get(i));
    MRuntime rt = new MRuntime(new MStore());
    IrMachine.run(p, rt);
    System.out.println("X=" + rt.getVar("X") + " I=" + rt.getVar("I"));
  }
}
