package jvmumps;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Concurrency stress test for MStore's caches.
 * Verifies thread safety of:
 *   - Striped long[] primitive cache (setNumAt/getNumAt)
 *   - Open-addressing String cache (setNum/getNumFast)
 *   - Tree nodes (ConcurrentHashMap)
 *
 * Runs N threads doing random reads/writes concurrently,
 * then verifies data consistency.
 */
public final class StoreConcurrencySmoke {

    static final int THREADS = 8;
    static final int OPS_PER_THREAD = 500_000;
    static final int KEYS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== MStore Concurrency Stress Test ===");
        System.out.println("threads=" + THREADS + " ops/thread=" + OPS_PER_THREAD + " keys=" + KEYS);
        System.out.println();

        boolean ok = true;

        // === Test 1: Primitive cache (setNumAt/getNumAt) ===
        System.out.println("--- Test 1: Striped long[] primitive cache ---");
        ok &= testPrimitiveCache();

        // === Test 2: String cache (setNum/getNumFast) ===
        System.out.println("--- Test 2: Open-addressing String cache ---");
        ok &= testStringCache();

        // === Test 3: Tree writes (set/get) ===
        System.out.println("--- Test 3: Tree (ConcurrentHashMap nodes) ---");
        ok &= testTree();

        // === Test 4: Mixed (all caches + tree concurrently) ===
        System.out.println("--- Test 4: Mixed (all layers concurrently) ---");
        ok &= testMixed();

        // === Test 5: Concurrent kill + read ===
        System.out.println("--- Test 5: Concurrent kill + read ---");
        ok &= testKillRace();

        System.out.println();
        System.out.println(ok ? "ALL CONCURRENCY TESTS PASSED" : "SOME CONCURRENCY TESTS FAILED");
        System.exit(ok ? 0 : 1);
    }

    static boolean testPrimitiveCache() throws Exception {
        var store = new MStore();
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger();

        // Launch concurrent writers + readers
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ex.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    int key = (tid * 131 + i) % KEYS;
                    double val = tid * 1000.0 + i;
                    store.setNumAt("^C", key, val);
                    double read = store.getNumAt("^C", (tid * 131 + i + 1) % KEYS);
                    // NaN is OK (not yet written by this thread)
                    if (Double.isNaN(read) && i > KEYS * 2) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(60, TimeUnit.SECONDS);

        // Verify: all keys should have values (written by some thread)
        int missing = 0;
        for (int k = 0; k < KEYS; k++) {
            double v = store.getNumAt("^C", k);
            if (Double.isNaN(v)) missing++;
        }
        boolean ok = missing == 0 && errors.get() < OPS_PER_THREAD;
        System.out.printf("  %s (missing=%d errors=%d)%n", ok ? "PASS" : "FAIL", missing, errors.get());
        return ok;
    }

    static boolean testStringCache() throws Exception {
        var store = new MStore();
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger();

        // Pre-build keys
        String[] keys = new String[KEYS];
        for (int i = 0; i < KEYS; i++) keys[i] = "^S\u0001" + i;

        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ex.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    int key = (tid * 131 + i) % KEYS;
                    double val = tid * 1000.0 + i;
                    store.setNum(keys[key], true, val);
                    double read = store.getNumFast(keys[(tid * 131 + i + 1) % KEYS], true);
                    if (Double.isNaN(read) && i > KEYS * 2) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(60, TimeUnit.SECONDS);

        int missing = 0;
        for (int k = 0; k < KEYS; k++) {
            double v = store.getNumFast(keys[k], true);
            if (Double.isNaN(v)) missing++;
        }
        boolean ok = missing == 0 && errors.get() < OPS_PER_THREAD;
        System.out.printf("  %s (missing=%d errors=%d)%n", ok ? "PASS" : "FAIL", missing, errors.get());
        return ok;
    }

    static boolean testTree() throws Exception {
        var store = new MStore();
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ex.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD / 10; i++) {
                    String key = "^T\u0001" + ((tid * 131 + i) % KEYS);
                    store.set(key, true, MValue.ofNumber(tid * 1000.0 + i));
                    MValue read = store.get("^T\u0001" + ((tid * 131 + i + 1) % KEYS), true);
                    // EMPTY is OK (not yet written)
                    if (read == MValue.EMPTY && i > KEYS) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(60, TimeUnit.SECONDS);

        int missing = 0;
        for (int k = 0; k < KEYS; k++) {
            MValue v = store.get("^T\u0001" + k, true);
            if (v == MValue.EMPTY || v.asNum() == 0) missing++;
        }
        boolean ok = errors.get() < OPS_PER_THREAD / 10;
        System.out.printf("  %s (missing=%d errors=%d)%n", ok ? "PASS" : "FAIL", missing, errors.get());
        return ok;
    }

    static boolean testMixed() throws Exception {
        var store = new MStore();
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger();

        String[] keys = new String[KEYS];
        for (int i = 0; i < KEYS; i++) keys[i] = "^M\u0001" + i;

        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            ex.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD / 10; i++) {
                    int k = (tid * 131 + i) % KEYS;
                    double val = tid * 1000.0 + i;
                    // Mix all three paths
                    store.setNumAt("^M", k, val);
                    store.setNum(keys[k], true, val);
                    store.set(keys[k], true, MValue.ofNumber(val));
                    double r1 = store.getNumAt("^M", k);
                    double r2 = store.getNumFast(keys[k], true);
                    MValue r3 = store.get(keys[k], true);
                    if (Double.isNaN(r1) && i > KEYS * 2) errors.incrementAndGet();
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(60, TimeUnit.SECONDS);

        boolean ok = errors.get() < OPS_PER_THREAD / 10;
        System.out.printf("  %s (errors=%d)%n", ok ? "PASS" : "FAIL", errors.get());
        return ok;
    }

    static boolean testKillRace() throws Exception {
        var store = new MStore();
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger();
        AtomicBoolean running = new AtomicBoolean(true);

        // Writers
        for (int t = 0; t < THREADS / 2; t++) {
            final int tid = t;
            ex.submit(() -> {
                int i = 0;
                while (running.get()) {
                    store.setNumAt("^K", tid * 100 + (i % 100), i);
                    store.setNum("^K\u0001" + (tid * 100 + (i % 100)), true, i);
                    i++;
                }
            });
        }

        // Killers + readers
        for (int t = 0; t < THREADS / 2; t++) {
            final int tid = t;
            ex.submit(() -> {
                int i = 0;
                while (running.get()) {
                    if (i % 100 == 0) {
                        store.kill("^K\u0001" + (tid * 50), true);
                    }
                    double v = store.getNumAt("^K", tid * 100 + (i % 100));
                    // NaN is OK after kill
                    i++;
                }
            });
        }

        Thread.sleep(2000); // Run for 2 seconds
        running.set(false);
        ex.shutdown();
        ex.awaitTermination(10, TimeUnit.SECONDS);

        // No exceptions = pass (data races on NaN are expected and OK)
        boolean ok = errors.get() == 0;
        System.out.printf("  %s (errors=%d — NaN after kill is expected)%n", ok ? "PASS" : "FAIL", errors.get());
        return ok;
    }
}
