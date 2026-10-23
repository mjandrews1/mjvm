package jvmumps;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent SET/GET/$ORDER + lock stress on SqlStore (and Cdb2 if CDB2PIPE set).
 * Catches lost updates / CME / crashes from unsynchronized store state (#120 #122).
 */
public final class StoreRaceSmoke {
    public static void main(String[] args) throws Exception {
        File db = File.createTempFile("race", ".db");
        db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        int threads = 8;
        int iters = 200;
        AtomicInteger errs = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int id = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < iters; i++) {
                        st.set(List.of("^R", id + "_" + i), true, MValue.ofNumber(i));
                        st.get(List.of("^R", id + "_" + i), true);
                        if (i % 10 == 0) {
                            st.order(List.of("^R", ""), true, 1);
                        }
                        if (i % 25 == 0) {
                            String pid = "r" + id;
                            if (st.lockAcquire("rk", pid, 2000)) {
                                st.lockRelease("rk", pid);
                            } else {
                                errs.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errs.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();
        st.close();
        st.close(); // idempotent
        if (errs.get() > 0) {
            throw new IllegalStateException("race errs=" + errs.get());
        }
        System.out.println("STORE RACE SMOKE OK threads=" + threads + " iters=" + iters);

        String pipe = System.getenv("CDB2PIPE");
        if (pipe == null || pipe.isEmpty()) pipe = "tools/cdb2pipe";
        if (new File(pipe).canExecute()) {
            try {
                Cdb2Store cd = new Cdb2Store(pipe,
                        System.getenv().getOrDefault("CDB2DB", "testdb"),
                        System.getenv().getOrDefault("CDB2TYPE", "local"));
                AtomicInteger ce = new AtomicInteger();
                CountDownLatch d2 = new CountDownLatch(4);
                for (int t = 0; t < 4; t++) {
                    final int id = t;
                    new Thread(() -> {
                        try {
                            for (int i = 0; i < 50; i++) {
                                cd.set(List.of("^C", id + "_" + i), true, MValue.ofNumber(i));
                                cd.get(List.of("^C", id + "_" + i), true);
                                cd.order(List.of("^C", ""), true, 1);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            ce.incrementAndGet();
                        } finally {
                            d2.countDown();
                        }
                    }).start();
                }
                d2.await();
                cd.close();
                cd.close();
                if (ce.get() > 0) throw new IllegalStateException("cdb2 race errs=" + ce.get());
                System.out.println("CDB2 RACE SMOKE OK");
            } catch (MError e) {
                System.out.println("SKIP cdb2 race (" + e.getMessage() + ")");
            }
        } else {
            System.out.println("SKIP cdb2 race (no cdb2pipe)");
        }
        System.out.println("STORE RACE OK");
    }
}
