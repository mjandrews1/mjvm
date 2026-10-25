package jvmumps;

public final class EngGapTest {
  public static void main(String[] args) throws Exception {
    String[] files = {
      "Packages/Engineering/Routines/ENY2VAC2.m", "Packages/Engineering/Routines/ENWOD.m",
    };
    String base = "/home/mark/vista-foia/VistA-M-master/";
    for (String f : files) {
      try {
        String code = java.nio.file.Files.readString(java.nio.file.Path.of(base + f));
        var fe = new mjvm.compiler.MFrontend();
        var ir = fe.compileProgram(code);
        System.out.println("OK: " + f + " (" + ir.size() + " ops)");
      } catch (Throwable e) {
        System.out.println(
            "FAIL: " + f + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
        StackTraceElement[] st = e.getStackTrace();
        for (int i = 0; i < Math.min(8, st.length); i++) System.out.println("    at " + st[i]);
      }
    }
  }
}
