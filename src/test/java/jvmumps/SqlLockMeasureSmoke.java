package jvmumps;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measure SqlStore.lockAcquire wait timeouts with queued LockQueue (JavaM #59).
 */
public final class SqlLockMeasureSmoke {
    public static void main(String[] args) throws Exception {
        File db = File.createTempFile("sqlk", ".db");
        db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());

        // 1) uncontended acquire ~0ms
        long t0 = System.nanoTime();
        boolean ok = st.lockAcquire("k", "p0", 200);
        long uncontendedUs = (System.nanoTime() - t0) / 1000;
        if (!ok) {
            throw new IllegalStateException("uncontended acquire failed");
        }
        st.lockRelease("k", "p0");

        // 2) contended: holder keeps lock, waiter times out
        st.lockAcquire("k", "holder", 200);
        t0 = System.nanoTime();
        boolean got = st.lockAcquire("k", "waiter", 150);
        long waitMs = (System.nanoTime() - t0) / 1_000_000;
        if (got) {
            throw new IllegalStateException("waiter should timeout");
        }
        st.lockRelease("k", "holder");

        // 3) queued handoff: N waiters serialized
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger granted = new AtomicInteger();
        AtomicLong maxWait = new AtomicLong();
        for (int i = 0; i < n; i++) {
            final String pid = "p" + i;
            new Thread(() -> {
                try {
                    start.await();
                    long a = System.nanoTime();
                    if (st.lockAcquire("serial", pid, 5000)) {
                        long w = (System.nanoTime() - a) / 1_000_000;
                        maxWait.accumulateAndGet(w, Math::max);
                        granted.incrementAndGet();
                        Thread.sleep(5);
                        st.lockRelease("serial", pid);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        if (granted.get() != n) {
            throw new IllegalStateException("not all granted " + granted.get());
        }
        long last = st.lastLockWaitMs("serial");
        System.out.println("LOCK MEASURE OK uncontended_us=" + uncontendedUs
                + " contended_timeout_ms=" + waitMs
                + " serialized=" + granted.get()
                + " max_wait_ms=" + maxWait.get()
                + " last_wait_ms=" + last);
        st.close();
    }
}
