package jvmumps;

/** Reproduce MFrontend parser crashes on specific VistA routines. */
public final class ParseCrashTest {
  public static void main(String[] args) throws Exception {
    String[] files = {
      "Packages/Asists/Routines/OOPSPUT1.m",
      "Packages/Automated Medical Information Exchange/Routines/DVBA8861.m",
      "Packages/Barcode Medication Administration/Routines/PSBMD.m",
    };
    String base = "/home/mark/vista-foia/VistA-M-master/";
    if (args.length > 0) base = args[0];

    for (String f : files) {
      String path = base + f;
      try {
        String code = java.nio.file.Files.readString(java.nio.file.Path.of(path));
        var fe = new mjvm.compiler.MFrontend();
        var ir = fe.compileProgram(code);
        System.out.println("OK: " + f + " (" + ir.size() + " ops)");
      } catch (Exception | StackOverflowError e) {
        System.out.println(
            "FAIL: " + f + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
        // Print first 3 frames of the stack
        StackTraceElement[] st = e.getStackTrace();
        for (int i = 0; i < Math.min(5, st.length); i++) {
          System.out.println("    at " + st[i]);
        }
      }
    }
  }
}
