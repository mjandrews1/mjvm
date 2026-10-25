package jvmumps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Store + lock latency matrix (jvmumps#100).
 *
 * <p>Bulk SET/GET/KILL/$ORDER, TSTART/TCOMMIT/TROLLBACK undo cost, contention sweep, and multi-key
 * vs single-key lock throughput.
 *
 * <p>Usage: java jvmumps.StoreLockBench [nOps] [maxThreads]
 */
public final class StoreLockBench {
  public static void main(String[] args) throws Exception {
    int n = args.length > 0 && args[0].matches("\\d+") ? Integer.parseInt(args[0]) : 5000;
    int maxT = args.length > 1 && args[1].matches("\\d+") ? Integer.parseInt(args[1]) : 64;

    System.out.println("StoreLockBench n=" + n + " maxThreads=" + maxT);
    System.out.println();
    bulkOps(new MStore(), "mstore", n);
    File db = File.createTempFile("slb", ".db");
    db.deleteOnExit();
    SqlStore sq = new SqlStore(db.getAbsolutePath());
    bulkOps(sq, "sqlite", n);
    txLatency(sq);
    contentionSweep(sq, maxT);
    multiKey(sq);
    sq.close();
    System.out.println();
    System.out.println("STORE LOCK BENCH OK");
  }

  static void bulkOps(StoreApi st, String tag, int n) {
    long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) {
      st.set(List.of("^T", String.valueOf(i)), true, MValue.ofNumber(i));
    }
    double setMs = (System.nanoTime() - t0) / 1e6;
    t0 = System.nanoTime();
    for (int i = 0; i < n; i++) {
      st.get(List.of("^T", String.valueOf(i)), true);
    }
    double getMs = (System.nanoTime() - t0) / 1e6;
    t0 = System.nanoTime();
    int w = 0;
    String k = "";
    while (w <= n + 10) {
      k = st.order(List.of("^T", k), true, 1);
      if (k.isEmpty()) break;
      w++;
    }
    double walkMs = (System.nanoTime() - t0) / 1e6;
    t0 = System.nanoTime();
    st.kill(List.of("^T"), true);
    double killMs = (System.nanoTime() - t0) / 1e6;
    System.out.printf(
        "BULK %s n=%d set=%.1fms (%.0f/s) get=%.1fms (%.0f/s) order=%d keys in %.1fms"
            + " kill=%.1fms%n",
        tag, n, setMs, n / (setMs / 1000), getMs, n / (getMs / 1000), w, walkMs, killMs);
  }

  /** TSTART / TCOMMIT / TROLLBACK via LineEngine (undo size = rows). */
  static void txLatency(SqlStore st) {
    for (int rows : new int[] {0, 10, 100, 500}) {
      LineEngine eng = new LineEngine();
      // empty TSTART/TCOMMIT
      long t0 = System.nanoTime();
      for (int i = 0; i < 1000; i++) {
        eng.evalLine("TSTART");
        eng.evalLine("TCOMMIT");
      }
      double emptyMs = (System.nanoTime() - t0) / 1e6 / 1000;
      // TSTART + rows SETs + TCOMMIT
      StringBuilder set = new StringBuilder("TSTART");
      for (int i = 0; i < rows; i++) {
        set.append(" S ^TX(").append(i).append(")=1");
      }
      set.append(" TCOMMIT");
      int reps = rows >= 500 ? 20 : rows >= 100 ? 50 : 200;
      t0 = System.nanoTime();
      for (int i = 0; i < reps; i++) {
        eng.evalLine(set.toString());
      }
      double commitMs = (System.nanoTime() - t0) / 1e6 / reps;
      // TSTART + rows + TROLLBACK
      StringBuilder rb = new StringBuilder("TSTART");
      for (int i = 0; i < rows; i++) {
        rb.append(" S ^TXR(").append(i).append(")=1");
      }
      rb.append(" TROLLBACK");
      t0 = System.nanoTime();
      for (int i = 0; i < reps; i++) {
        eng.evalLine(rb.toString());
      }
      double rollMs = (System.nanoTime() - t0) / 1e6 / reps;
      System.out.printf(
          "TX rows=%d empty_tstart_commit=%.4fms tcommit=%.4fms trollack=%.4fms%n",
          rows, emptyMs, commitMs, rollMs);
    }
  }

  static void contentionSweep(SqlStore st, int maxT) {
    System.out.println("CONT sweep (one key \"sweep\", 5 acquires/thread, timeout 8s)");
    System.out.println("threads wall_ms granted timeouts p50 p99 max");
    for (int th : new int[] {1, 2, 4, 8, 16, 32, 64}) {
      if (th > maxT) continue;
      int iters = 5;
      List<Long> waits = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger granted = new AtomicInteger();
      AtomicInteger timedOut = new AtomicInteger();
      CyclicBarrier go = new CyclicBarrier(th);
      CountDownLatch done = new CountDownLatch(th);
      for (int t = 0; t < th; t++) {
        final String pid = "sw" + t;
        new Thread(
                () -> {
                  try {
                    go.await();
                    for (int i = 0; i < iters; i++) {
                      long a = System.nanoTime();
                      boolean ok = st.lockAcquire("sweep", pid, 8000);
                      waits.add((System.nanoTime() - a) / 1_000_000);
                      if (!ok) {
                        timedOut.incrementAndGet();
                        continue;
                      }
                      granted.incrementAndGet();
                      st.lockRelease("sweep", pid);
                    }
                  } catch (Exception e) {
                    e.printStackTrace();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }
      long t0 = System.nanoTime();
      try {
        done.await();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      long wall = (System.nanoTime() - t0) / 1_000_000;
      List<Long> s = new ArrayList<>(waits);
      Collections.sort(s);
      System.out.printf(
          "%d %d %d %d %d %d %d%n",
          th,
          wall,
          granted.get(),
          timedOut.get(),
          pct(s, 50),
          pct(s, 99),
          s.isEmpty() ? -1 : s.get(s.size() - 1));
    }
  }

  static void multiKey(SqlStore st) throws Exception {
    int threads = 32;
    int iters = 20;
    for (int keys : new int[] {1, 4}) {
      CyclicBarrier go = new CyclicBarrier(threads);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger granted = new AtomicInteger();
      AtomicInteger timedOut = new AtomicInteger();
      for (int t = 0; t < threads; t++) {
        final String pid = "mk" + t;
        final int k = t % keys;
        new Thread(
                () -> {
                  try {
                    go.await();
                    for (int i = 0; i < iters; i++) {
                      boolean ok = st.lockAcquire("mk" + k, pid, 3000);
                      if (!ok) {
                        timedOut.incrementAndGet();
                        continue;
                      }
                      granted.incrementAndGet();
                      st.lockRelease("mk" + k, pid);
                    }
                  } catch (Exception e) {
                    e.printStackTrace();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }
      long t0 = System.nanoTime();
      done.await();
      long wall = (System.nanoTime() - t0) / 1_000_000;
      System.out.printf(
          "MULTIKEY keys=%d threads=%d wall=%dms granted=%d timeouts=%d%n",
          keys, threads, wall, granted.get(), timedOut.get());
    }
  }

  static long pct(List<Long> s, int p) {
    if (s.isEmpty()) return -1;
    int idx = (int) Math.ceil(p / 100.0 * s.size()) - 1;
    if (idx < 0) idx = 0;
    if (idx >= s.size()) idx = s.size() - 1;
    return s.get(idx);
  }
}
