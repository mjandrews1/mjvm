package mjvm.bench;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jvmumps.MStore;
import jvmumps.MValue;
import jvmumps.SqlStore;
import jvmumps.StoreApi;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * Holistic performance matrix for JavaM + mJVM (jvmumps#97).
 *
 * <p>One re-runnable capture of the numbers that answer: how fast is this stack, and is it getting
 * better? Covers interpreter/store (JavaM), IR + compile (mJVM), and a minimal footprint probe. Not
 * a CI gate — fixed hosts only.
 *
 * <p>Usage: java mjvm.bench.PerfMatrix # default iters java mjvm.bench.PerfMatrix 100000 java
 * mjvm.bench.PerfMatrix --json 100000
 */
public final class PerfMatrix {
  static final int DEFAULT_ITERS = 100_000;

  public static void main(String[] args) throws Exception {
    boolean json = false;
    int iters = DEFAULT_ITERS;
    for (String a : args) {
      if ("--json".equals(a)) json = true;
      else if (a.matches("\\d+")) iters = Integer.parseInt(a);
    }

    Map<String, Object> m = new LinkedHashMap<>();
    captureHost(m);
    captureCompile(m);
    captureIrLoops(m, iters);
    captureStore(m, iters / 20);
    captureLock(m);
    captureFootprint(m, iters);

    if (json) {
      printJson(m);
    } else {
      printMarkdown(m, iters);
    }
  }

  static void captureHost(Map<String, Object> m) {
    m.put("host.os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    m.put("host.arch", System.getProperty("os.arch"));
    m.put("host.cpus", Runtime.getRuntime().availableProcessors());
    m.put(
        "host.java",
        System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
    m.put("host.maxheap_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
  }

  static void captureCompile(Map<String, Object> m) {
    String src = "S X=1 S Y=2 W X+Y,!";
    // warm
    new MFrontend().compileLine(src);
    long t0 = System.nanoTime();
    List<Ir> prog = new MFrontend().compileLine(src);
    double ms = (System.nanoTime() - t0) / 1e6;
    m.put("m.irexec.compile_line_ms", round(ms, 3));
    m.put("m.irexec.compile_line_ops", prog.size());

    t0 = System.nanoTime();
    MFrontend fe = new MFrontend();
    List<Ir> frag = fe.compileFragment("S A=0 S I=" + 1000 + " S A=A+1 S I=I-1");
    m.put("m.irexec.compile_fragment_ms", round((System.nanoTime() - t0) / 1e6, 3));
    m.put("m.irexec.compile_fragment_ops", frag.size());
  }

  static void captureIrLoops(Map<String, Object> m, int iters) {
    for (String kind : new String[] {"inc", "cat", "store"}) {
      List<Ir> loop = loopProg(iters, kind);
      MRuntime rt = new MRuntime(new MStore());
      // warmup
      IrMachine.run(loop, rt);
      loop = loopProg(iters, kind);
      rt = new MRuntime(new MStore());
      long t0 = System.nanoTime();
      IrMachine.run(loop, rt);
      double sec = (System.nanoTime() - t0) / 1e9;
      double ops = iters / sec;
      m.put("mjvm.ir.loop_" + kind + "_ops", (long) ops);
      m.put("mjvm.ir.loop_" + kind + "_ms", round(sec * 1000, 2));
    }
  }

  static void captureStore(Map<String, Object> m, int n) {
    // in-memory MStore: set/get/order
    MStore mem = new MStore();
    long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) {
      mem.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    double setMs = (System.nanoTime() - t0) / 1e6;
    t0 = System.nanoTime();
    for (int i = 0; i < n; i++) {
      mem.get(List.of("^T", String.valueOf(i)), true);
    }
    double getMs = (System.nanoTime() - t0) / 1e6;
    t0 = System.nanoTime();
    int walked = walk(mem, n);
    double walkMs = (System.nanoTime() - t0) / 1e6;
    m.put("store.mstore.n", n);
    m.put("store.mstore.set_ms", round(setMs, 2));
    m.put("store.mstore.get_ms", round(getMs, 2));
    m.put("store.mstore.order_walk_ms", round(walkMs, 2));
    m.put("store.mstore.order_walk_keys", walked);
    m.put("store.mstore.set_ops", (long) (n / (setMs / 1000.0)));
    m.put("store.mstore.get_ops", (long) (n / (getMs / 1000.0)));

    // SqlStore
    try {
      File db = File.createTempFile("perf-sql", ".db");
      db.deleteOnExit();
      SqlStore sq = new SqlStore(db.getAbsolutePath());
      t0 = System.nanoTime();
      for (int i = 0; i < n; i++) {
        sq.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
      }
      double sqlSetMs = (System.nanoTime() - t0) / 1e6;
      t0 = System.nanoTime();
      for (int i = 0; i < n; i++) {
        sq.get(List.of("^T", String.valueOf(i)), true);
      }
      double sqlGetMs = (System.nanoTime() - t0) / 1e6;
      t0 = System.nanoTime();
      int sqlWalked = walk(sq, n);
      double sqlWalkMs = (System.nanoTime() - t0) / 1e6;
      m.put("store.sqlite.n", n);
      m.put("store.sqlite.set_ms", round(sqlSetMs, 2));
      m.put("store.sqlite.get_ms", round(sqlGetMs, 2));
      m.put("store.sqlite.order_walk_ms", round(sqlWalkMs, 2));
      m.put("store.sqlite.order_walk_keys", sqlWalked);
      m.put("store.sqlite.set_ops", (long) (n / (sqlSetMs / 1000.0)));
      m.put("store.sqlite.get_ops", (long) (n / (sqlGetMs / 1000.0)));
      sq.close();
    } catch (Exception e) {
      m.put("store.sqlite.error", e.toString());
    }
  }

  static void captureLock(Map<String, Object> m) {
    try {
      File db = File.createTempFile("perf-lock", ".db");
      db.deleteOnExit();
      SqlStore st = new SqlStore(db.getAbsolutePath());
      // warm
      st.lockAcquire("w", "p", 1000);
      st.lockRelease("w", "p");
      long t0 = System.nanoTime();
      boolean ok = st.lockAcquire("k", "p0", 2000);
      long us = (System.nanoTime() - t0) / 1000;
      if (ok) st.lockRelease("k", "p0");
      m.put("lock.uncontended_us", us);
      st.close();
    } catch (Exception e) {
      m.put("lock.error", e.toString());
    }
  }

  static void captureFootprint(Map<String, Object> m, int iters) {
    Runtime rt = Runtime.getRuntime();
    System.gc();
    long before = rt.totalMemory() - rt.freeMemory();
    // allocate a realistic M store tree
    MStore mem = new MStore();
    for (int i = 0; i < Math.min(iters, 50_000); i++) {
      mem.set(List.of("^F", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    System.gc();
    long after = rt.totalMemory() - rt.freeMemory();
    m.put("footprint.heap_before_mb", before / (1024 * 1024));
    m.put("footprint.heap_after_store_mb", after / (1024 * 1024));
    m.put("footprint.heap_delta_mb", Math.max(0, after - before) / (1024 * 1024));
    // process RSS best-effort (Linux)
    try {
      String pid = ProcessHandle.current().pid() + "";
      List<String> cmd = List.of("sh", "-c", "awk '/VmRSS/{print $2}' /proc/" + pid + "/status");
      Process p = new ProcessBuilder(cmd).start();
      String rss = new String(p.getInputStream().readAllBytes()).trim();
      if (!rss.isEmpty()) {
        m.put("footprint.rss_kb", Long.parseLong(rss.trim()));
      }
    } catch (Exception ignored) {
      // macOS / no /proc — omit
    }
  }

  static int walk(StoreApi st, int n) {
    String k = "";
    int c = 0;
    while (c <= n + 10) {
      k = st.order(List.of("^T", k), true, 1);
      if (k.isEmpty()) break;
      c++;
    }
    return c;
  }

  /** Counted IR loop — same shape as MjvmBench. */
  static List<Ir> loopProg(int n, String kind) {
    return MjvmBench.loopProg(n, kind);
  }

  static double round(double v, int p) {
    double f = Math.pow(10, p);
    return Math.round(v * f) / f;
  }

  static void printMarkdown(Map<String, Object> m, int iters) {
    System.out.println("# Perf matrix");
    System.out.println();
    System.out.println("iters=" + iters + " · generated " + java.time.Instant.now());
    System.out.println();
    System.out.println("| Metric | Value |");
    System.out.println("|--------|------:|");
    for (Map.Entry<String, Object> e : m.entrySet()) {
      System.out.println("| `" + e.getKey() + "` | " + e.getValue() + " |");
    }
    System.out.println();
    System.out.println(
        "Rerun: `java mjvm.bench.PerfMatrix " + iters + "` (add `--json` for baselines).");
  }

  static void printJson(Map<String, Object> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> e : m.entrySet()) {
      if (!first) sb.append(',');
      first = false;
      sb.append('"').append(e.getKey()).append("\":");
      Object v = e.getValue();
      if (v instanceof Number) sb.append(v);
      else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
    }
    sb.append('}');
    System.out.println(sb);
  }
}
