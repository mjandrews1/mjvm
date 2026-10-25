package jvmumps;

/** Find which routine causes OOM. */
public final class MemoryProbe2 {
  public static void main(String[] args) throws Exception {
    String base = args.length > 0 ? args[0] : "/home/mark/vista-foia/VistA-M-master/";
    int limit = args.length > 1 ? Integer.parseInt(args[1]) : 15000;

    java.util.List<java.nio.file.Path> paths = new java.util.ArrayList<>();
    try (var s = java.nio.file.Files.walk(java.nio.file.Path.of(base))) {
      s.filter(p -> p.toString().endsWith(".m")).limit(limit).forEach(paths::add);
    }
    System.out.println("Found " + paths.size() + " files");

    var rt = Runtime.getRuntime();
    for (int i = 0; i < paths.size(); i++) {
      try {
        String code = java.nio.file.Files.readString(paths.get(i));
        var fe = new mjvm.compiler.MFrontend();
        fe.compileProgram(code);
      } catch (Exception | StackOverflowError e) {
        // skip
      }
      if (i % 1000 == 0) {
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        System.out.printf(
            "  [%d] %dMB used, file=%s size=%d%n",
            i, used, paths.get(i).getFileName(), java.nio.file.Files.size(paths.get(i)));
      }
    }
    System.out.println("DONE");
  }
}
