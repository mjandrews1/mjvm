package jvmumps;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-key lock isolation (JavaM #59).
 * Distinct keys must not block each other; same key serializes.
 */
public final class SqlLockMultiKeySmoke {
    public static void main(String[] args) throws Exception {
        File db = File.createTempFile("sqlk-multi", ".db");
        db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());

        // 1) two keys held at once by different threads
        final boolean[] aok = new boolean[1];
        final boolean[] bok = new boolean[1];
        final CountDownLatch hold = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            aok[0] = st.lockAcquire("keyA", "pA", 500);
            if (aok[0]) {
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                st.lockRelease("keyA", "pA");
            }
        });
        Thread t2 = new Thread(() -> {
            // should not wait for keyA
            bok[0] = st.lockAcquire("keyB", "pB", 200);
            if (bok[0]) {
                st.lockRelease("keyB", "pB");
            }
        });
        t1.start();
        Thread.sleep(20);
        t2.start();
        t2.join(1000);
        hold.countDown();
        t1.join(1000);
        if (!aok[0] || !bok[0]) {
            throw new IllegalStateException("key isolation fail A=" + aok[0] + " B=" + bok[0]);
        }

        // 2) same key still exclusive across keys
        st.lockAcquire("keyC", "h", 200);
        if (st.lockAcquire("keyC", "o", 80)) {
            throw new IllegalStateException("same key not exclusive");
        }
        // other key free while C held
        if (!st.lockAcquire("keyD", "d", 200)) {
            throw new IllegalStateException("keyD blocked by keyC");
        }
        st.lockRelease("keyD", "d");
        st.lockRelease("keyC", "h");

        // 3) parallel keys progress: 4 keys × 40 rounds
        final int keys = 4;
        final int rounds = 40;
        final CyclicBarrier go = new CyclicBarrier(keys);
        final CountDownLatch done = new CountDownLatch(keys);
        final AtomicInteger okN = new AtomicInteger();
        for (int k = 0; k < keys; k++) {
            final String key = "par" + k;
            new Thread(() -> {
                try {
                    go.await();
                    for (int i = 0; i < rounds; i++) {
                        if (st.lockAcquire(key, "p" + key, 2000)) {
                            okN.incrementAndGet();
                            st.lockRelease(key, "p" + key);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();
        if (okN.get() != keys * rounds) {
            throw new IllegalStateException("parallel keys incomplete " + okN.get());
        }
        st.close();
        System.out.println("MULTIKEY SMOKE OK parallel=" + okN.get());
    }
}
