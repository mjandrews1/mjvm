package jvmumps;

public class DbgOne {
  public static void main(String[] a) throws Exception {
    String code = "MAIN D\n . W \"in-block\",!\n W \"after\",!";
    var prog = new mjvm.compiler.MFrontend().compileProgram(code);
    for (int i = 0; i < prog.size(); i++) System.out.println("  " + i + " " + prog.get(i));
    mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
    mjvm.runtime.IrMachine.run(prog, rt);
    System.out.println("OUT=" + rt.output().replace("\n", "\\n"));
  }
}
