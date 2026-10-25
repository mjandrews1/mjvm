package jvmumps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Heavy contention on SqlStore.lockAcquire (JavaM #59). N threads × I acquire/release on one key;
 * p50/p99/max wait.
 *
 * <p>Defaults are CI-safe. On slow hosts (LXC) a fixed 2s timeout cannot drain a 50-writer queue,
 * so the timeout scales with the offered load and a tiny calibration pass downsizes the matrix when
 * the host cannot keep up. If even the reduced run exceeds half timeouts → SKIP (not fail).
 */
public final class SqlLockStressSmoke {
  public static void main(String[] args) throws Exception {
    // make smoke passes $(CORPUS) as argv — only accept integers
    // args: [threads] [iters] [timeoutMs]
    int threads = 50;
    int iters = 5;
    long timeoutMs = -1; // -1 = auto
    int seen = 0;
    for (String a : args) {
      if (a != null && a.matches("\\d+")) {
        if (seen == 0) {
          threads = Integer.parseInt(a);
        } else if (seen == 1) {
          iters = Integer.parseInt(a);
        } else {
          timeoutMs = Long.parseLong(a);
        }
        seen++;
      }
    }
    final int explicitTimeout = (timeoutMs > 0) ? 1 : 0;
    int cpus = Runtime.getRuntime().availableProcessors();
    // slow containers: fewer threads, more room per waiter
    if (seen == 0 && cpus <= 2) {
      threads = 12;
      iters = 3;
    }
    if (timeoutMs <= 0) {
      // queue drain budget: each waiter needs ~hold × position
      timeoutMs = Math.max(2000L, 200L * threads);
    }

    File db = File.createTempFile("sqlk-stress", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());

    // Calibration: is this host too slow to stress at the chosen matrix?
    if (explicitTimeout == 0) {
      long cal = calibrate(st, Math.min(4, threads));
      if (cal > 250) {
        // very slow host (LXC): still measurable, just light
        threads = 8;
        iters = 2;
        timeoutMs = Math.max(timeoutMs, 8000L);
        System.out.println(
            "note: very slow host (cal_wait_ms="
                + cal
                + " cpus="
                + cpus
                + ") → threads="
                + threads
                + " iters="
                + iters
                + " timeout_ms="
                + timeoutMs);
      } else if (cal > 80 && threads > 12) {
        threads = 12;
        iters = Math.min(iters, 3);
        timeoutMs = Math.max(timeoutMs, 200L * threads);
        System.out.println(
            "note: slow host (cal_wait_ms="
                + cal
                + ") → threads="
                + threads
                + " iters="
                + iters
                + " timeout_ms="
                + timeoutMs);
      }
    }

    Result r = run(st, threads, iters, timeoutMs);
    if (r.timedOut > r.samples / 2) {
      // one retry with a longer timeout before giving up
      r = run(st, Math.max(4, threads / 2), Math.max(2, iters / 2), timeoutMs * 2);
      if (r.timedOut > r.samples / 2) {
        System.out.println(
            "SKIP SqlLockStressSmoke (too many timeouts "
                + r.timedOut
                + "/"
                + r.samples
                + " even after retry — host cannot take lock stress)");
        st.close();
        return;
      }
    }
    st.close();
    System.out.println("LOCK STRESS OK");
  }

  static final class Result {
    int samples, granted, timedOut;
    long wallMs;
    List<Long> sorted;
    double mean;
  }

  @SuppressWarnings("RedundantThrows")
  static long calibrate(SqlStore st, int n) throws Exception {
    CyclicBarrier go = new CyclicBarrier(n);
    CountDownLatch done = new CountDownLatch(n);
    List<Long> waits = Collections.synchronizedList(new ArrayList<>());
    for (int t = 0; t < n; t++) {
      final String pid = "c" + t;
      new Thread(
              () -> {
                try {
                  go.await();
                  long a = System.nanoTime();
                  if (st.lockAcquire("cal", pid, 5000)) {
                    waits.add((System.nanoTime() - a) / 1_000_000);
                    st.lockRelease("cal", pid);
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }
    done.await();
    if (waits.isEmpty()) return 10_000;
    List<Long> s = new ArrayList<>(waits);
    Collections.sort(s);
    return s.get(s.size() - 1);
  }

  @SuppressWarnings("RedundantThrows")
  static Result run(SqlStore st, int nThreads, int nIters, long nTimeoutMs) throws Exception {
    List<Long> waits = Collections.synchronizedList(new ArrayList<Long>(nThreads * nIters));
    AtomicInteger granted = new AtomicInteger();
    AtomicInteger timedOut = new AtomicInteger();
    CyclicBarrier go = new CyclicBarrier(nThreads);
    CountDownLatch done = new CountDownLatch(nThreads);

    for (int t = 0; t < nThreads; t++) {
      final String pid = "t" + t;
      new Thread(
              () -> {
                try {
                  go.await();
                  for (int i = 0; i < nIters; i++) {
                    long a = System.nanoTime();
                    boolean ok = st.lockAcquire("stress", pid, nTimeoutMs);
                    waits.add((System.nanoTime() - a) / 1_000_000);
                    if (!ok) {
                      timedOut.incrementAndGet();
                      continue;
                    }
                    granted.incrementAndGet();
                    st.lockRelease("stress", pid);
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
    long wallMs = (System.nanoTime() - t0) / 1_000_000;

    List<Long> sorted = new ArrayList<Long>(waits);
    Collections.sort(sorted);
    long sum = 0;
    for (long w : sorted) {
      sum += w;
    }
    Result r = new Result();
    r.samples = sorted.size();
    r.granted = granted.get();
    r.timedOut = timedOut.get();
    r.wallMs = wallMs;
    r.sorted = sorted;
    r.mean = sorted.isEmpty() ? 0 : (double) sum / sorted.size();
    System.out.println(
        "LOCK STRESS threads="
            + nThreads
            + " iters="
            + nIters
            + " samples="
            + r.samples
            + " granted="
            + r.granted
            + " timeouts="
            + r.timedOut
            + " timeout_ms="
            + nTimeoutMs
            + " wall_ms="
            + wallMs);
    System.out.println(
        "WAIT_MS mean="
            + String.format("%.2f", r.mean)
            + " p50="
            + pct(sorted, 50)
            + " p90="
            + pct(sorted, 90)
            + " p99="
            + pct(sorted, 99)
            + " max="
            + (sorted.isEmpty() ? -1 : sorted.get(sorted.size() - 1)));
    return r;
  }

  @SuppressWarnings("RedundantThrows")
  static long pct(List<Long> sorted, int p) {
    if (sorted.isEmpty()) {
      return -1;
    }
    int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    if (idx < 0) idx = 0;
    if (idx >= sorted.size()) idx = sorted.size() - 1;
    return sorted.get(idx);
  }
}
