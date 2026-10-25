package jvmumps;

import java.util.List;
import mjvm.compiler.BytecodeEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.MRuntime;

public class T3 {
  public static void main(String[] a) throws Exception {
    List<Ir> p = new MFrontend().compileLine("S X=1 W X,!");
    MRuntime rt = new MRuntime(new MStore());
    try {
      Runnable r = BytecodeEmitter.load(p, "T3Gen", rt);
      r.run();
      System.out.println("OUT=" + rt.output().replace("\n", "\\n"));
    } catch (Throwable t) {
      t.printStackTrace();
    }
    // CALL via indy
    p = new MFrontend().compileLine("D FOO");
    rt = new MRuntime(new MStore());
    rt.setResolver((n, argc, r2) -> r2.write("called:" + n));
    try {
      Runnable r = BytecodeEmitter.load(p, "T3Gen2", rt);
      r.run();
      System.out.println("OUT2=" + rt.output().replace("\n", "\\n"));
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
