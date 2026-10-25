package mjvm.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jvmumps.MStore;
import mjvm.compiler.ClassEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * Footprint metrics (jvmumps#101): compile latency/size, startup, heap/RSS, GC.
 *
 * <p>Usage: java mjvm.bench.FootprintBench [programLines] [loopIters] java
 * mjvm.bench.FootprintBench --json
 */
public final class FootprintBench {
  @SuppressWarnings({"DLS_DEAD_LOCAL_STORE", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "UCF_USELESS_CONTROL_FLOW", "DB_DUPLICATE_BRANCHES"})
  public static void main(String[] args) throws Exception  {
    boolean json = false;
    int lines = 200;
    int iters = 50_000;
    int narg = 0;
    for (String a : args) {
      if ("--json".equals(a)) json = true;
      else if (a.matches("\\d+")) {
        if (narg == 0) lines = Integer.parseInt(a);
        else if (narg == 1) iters = Integer.parseInt(a);
        narg++;
      }
    }
    System.out.println(json ? "{" : "# Footprint");
    if (!json) System.out.println();

    // --- startup sample: we are already up; report JVM boot via separate note ---
    Runtime rt = Runtime.getRuntime();
    long heapUsed0 = used(rt);

    // --- compile small line (M8 metric) ---
    MFrontend fe = new MFrontend();
    String line = "S X=1 S Y=2 W X+Y,!";
    fe.compileLine(line); // warm
    long t0 = System.nanoTime();
    List<Ir> one = fe.compileLine(line);
    double lineMs = (System.nanoTime() - t0) / 1e6;

    // --- compile program of N lines ---
    StringBuilder progSrc = new StringBuilder();
    for (int i = 0; i < lines; i++) {
      progSrc.append("S L").append(i).append("=").append(i).append(" ");
    }
    progSrc.append("W L0,!");
    t0 = System.nanoTime();
    List<Ir> prog = fe.compileProgram(progSrc.toString());
    double progMs = (System.nanoTime() - t0) / 1e6;

    // --- class emit size + latency (200-op unroll, 64KB-safe) ---
    List<Ir> unroll = MjvmBench.loopProg(0, "inc"); // placeholder replaced below
    unroll = unrollInc(200);
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-footprint");
    Files.createDirectories(dir);
    String cn = "Fp" + System.nanoTime();
    t0 = System.nanoTime();
    ClassEmitter.emit(unroll, cn, dir);
    double emitMs = (System.nanoTime() - t0) / 1e6;
    long classBytes = Files.size(dir.resolve(cn + ".class"));
    long srcBytes = Files.size(dir.resolve(cn + ".java"));

    // --- heap after IR loop ---
    long heapBefore = used(rt);
    MRuntime mr = new MRuntime(new MStore());
    List<Ir> loop = MjvmBench.loopProg(iters, "store");
    IrMachine.run(loop, mr);
    System.gc();
    long heapAfter = used(rt);
    long rssKb = readRssKb();

    if (json) {
      System.out.printf("\"compile_line_ms\":%.3f,\"compile_line_ops\":%d,%n", lineMs, one.size());
      System.out.printf(
          "\"compile_program_lines\":%d,\"compile_program_ms\":%.3f,\"compile_program_ops\":%d,%n",
          lines, progMs, prog.size());
      System.out.printf(
          "\"class_emit_ms\":%.3f,\"class_bytes\":%d,\"class_src_bytes\":%d,%n",
          emitMs, classBytes, srcBytes);
      System.out.printf(
          "\"heap_before_mb\":%d,\"heap_after_irloop_mb\":%d,\"heap_used_start_mb\":%d,%n",
          heapBefore / 1048576, heapAfter / 1048576, heapUsed0 / 1048576);
      System.out.printf(
          "\"rss_kb\":%d,\"gc_count\":%d,%n",
          rssKb,
          java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
              .mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionCount)
              .sum());
      System.out.println("\"host_cpus\": " + Runtime.getRuntime().availableProcessors() + "}");
    } else {
      System.out.printf("| compile line (1 stmt) | %.3f ms | %d ops |%n", lineMs, one.size());
      System.out.printf(
          "| compile program (%d lines) | %.3f ms | %d ops |%n", lines, progMs, prog.size());
      System.out.printf(
          "| class emit (unroll x200) | %.3f ms | src %d B · class %d B |%n",
          emitMs, srcBytes, classBytes);
      System.out.printf(
          "| heap start → after IR loop | %d MB → %d MB | (start sample %d MB) |%n",
          heapBefore / 1048576, heapAfter / 1048576, heapUsed0 / 1048576);
      System.out.printf("| process RSS | %d KB |%n", rssKb);
      long gc =
          java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
              .mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionCount)
              .sum();
      System.out.printf("| GC collections (this JVM) | %d |%n", gc);
      System.out.println();
      System.out.println("Rerun: `java mjvm.bench.FootprintBench " + lines + " " + iters + "`");
      System.out.println("Startup is measured externally: `/usr/bin/time java mjvm.cli.Mjvm -V`");
    }
  }

  static List<Ir> unrollInc(int n) {
    MFrontend fe = new MFrontend();
    List<Ir> prog = new ArrayList<>(fe.compileFragment("S X=0"));
    for (int i = 0; i < n; i++) {
      prog.add(Ir.load("X"));
      prog.add(Ir.pushNum(1));
      prog.add(Ir.bin(Ir.ADD));
      prog.add(Ir.store("X"));
    }
    prog.add(Ir.ret());
    return prog;
  }

  static long used(Runtime rt) {
    return rt.totalMemory() - rt.freeMemory();
  }

  static long readRssKb() {
    try {
      String pid = ProcessHandle.current().pid() + "";
      Process p =
          new ProcessBuilder("sh", "-c", "awk '/VmRSS/{print $2}' /proc/" + pid + "/status")
              .start();
      String s = new String(p.getInputStream().readAllBytes()).trim();
      return s.isEmpty() ? -1 : Long.parseLong(s);
    } catch (@SuppressWarnings("unused") Exception e) {
      return -1;
    }
  }
}
