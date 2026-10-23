package jvmumps;
public final class Gap2Probe {
  public static void main(String[] a) {
    mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
    mjvm.compiler.MFrontend fe = new mjvm.compiler.MFrontend();
    var irR = fe.compileProgram("P2(Y)\n S Y=Y_\"ab\" Q Y\n");
    System.out.println("tags=" + fe.lastTags() + " ir=" + irR);
    mjvm.runtime.IrMachine.run(irR, rt, fe.lastTags());
    System.out.println("after link, linkedTags=" + rt.linkedTags());

    // direct extrinsic call
    MValue v = rt.callExtrinsic("P2", new MValue[]{ MValue.ofString("z") });
    System.out.println("direct callExtrinsic -> [" + v.asStr() + "]");

    rt.reset();
    var irC = fe.compileProgram("S X=$$P2(\"z\") W X,!");
    System.out.println("call ir=" + irC + "  lastTags now=" + fe.lastTags());
    mjvm.runtime.IrMachine.run(irC, rt, fe.lastTags());
    System.out.println("output=[" + rt.output().strip() + "]");
    System.out.println("X=[" + rt.getVar("X").asStr() + "]");
  }
}
