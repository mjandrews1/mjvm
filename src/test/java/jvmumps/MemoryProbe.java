package jvmumps;

/** Profile memory usage during VistA native compile. */
public final class MemoryProbe {
  public static void main(String[] args) throws Exception {
    String base = args.length > 0 ? args[0] : "/home/mark/vista-foia/VistA-M-master/";
    int limit = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

    java.util.List<String> sources = new java.util.ArrayList<>();
    java.util.List<String> paths = new java.util.ArrayList<>();
    try (var s = java.nio.file.Files.walk(java.nio.file.Path.of(base))) {
      s.filter(p -> p.toString().endsWith(".m"))
          .sorted()
          .limit(limit)
          .forEach(
              p -> {
                try {
                  sources.add(java.nio.file.Files.readString(p));
                  paths.add(p.toString());
                } catch (Exception e) {
                }
              });
    }
    System.out.println("Loaded " + sources.size() + " sources");
    System.gc();
    printMem("after load");

    int ok = 0, fail = 0;
    for (int i = 0; i < sources.size(); i++) {
      try {
        var fe = new mjvm.compiler.MFrontend();
        var ir = fe.compileProgram(sources.get(i));
        if (!ir.isEmpty()) ok++;
        else fail++;
      } catch (Exception | StackOverflowError e) {
        fail++;
      }
      if (i % 1000 == 0) {
        System.gc();
        printMem("after " + (i + 1) + " compiled");
      }
    }
    System.gc();
    printMem("final");
    System.out.println("ok=" + ok + " fail=" + fail);
  }

  static void printMem(String label) {
    var rt = Runtime.getRuntime();
    long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
    long total = rt.totalMemory() / 1048576;
    System.out.printf("  %s: %dMB used / %dMB total%n", label, used, total);
  }
}
