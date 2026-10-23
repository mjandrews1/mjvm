package jvmumps;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Performance metrics harness — measures the three dimensions:
 * 1. Sustained throughput (TPS over time)
 * 2. Response time (latency percentiles: p50/p95/p99/p999/max)
 * 3. Transactional accuracy (invariant checker under load)
 *
 * Usage: java jvmumps.PerfMetrics [store] [seconds] [threads]
 *   store = mem|h2|tiered (default: mem)
 *   seconds = duration (default: 10)
 *   threads = concurrent threads (default: 4)
 */
public final class PerfMetrics {

    static final int WARMUP_TXNS = 1000;
    static final int SAMPLE_INTERVAL_MS = 100;

    /** Latency recorder — stores individual transaction latencies. */
    static final class LatencyRecorder {
        final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        void record(long nanos) { latencies.add(nanos); }
        double percentile(double p) {
            if (latencies.isEmpty()) return 0;
            long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, idx)] / 1e3; // microseconds
        }
        double mean() {
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1e3;
        }
        int count() { return latencies.size(); }
    }

    /** Time-series TPS recorder. */
    static final class TpsRecorder {
        final List<double[]> samples = Collections.synchronizedList(new ArrayList<>());
        final AtomicLong txnCount = new AtomicLong();
        final long startTime = System.nanoTime();
        void record() { txnCount.incrementAndGet(); }
        void sample() {
            double elapsedSec = (System.nanoTime() - startTime) / 1e9;
            double tps = txnCount.get() / Math.max(0.001, elapsedSec);
            samples.add(new double[]{elapsedSec, tps});
        }
        double current() {
            double elapsedSec = (System.nanoTime() - startTime) / 1e9;
            return txnCount.get() / Math.max(0.001, elapsedSec);
        }
        double min() { return samples.stream().mapToDouble(s -> s[1]).min().orElse(0); }
        double max() { return samples.stream().mapToDouble(s -> s[1]).max().orElse(0); }
        double mean() { return samples.stream().mapToDouble(s -> s[1]).average().orElse(0); }
    }

    /** Invariant checker — verifies bank double-entry. */
    static final class InvariantChecker {
        final AtomicInteger violations = new AtomicInteger();
        final List<String> failures = Collections.synchronizedList(new ArrayList<>());

        void checkBankInvariant(StoreApi store) {
            // B(acct) - C(acct) = 2 * FEE(acct)
            for (int acct = 0; acct < 10; acct++) {
                String a = String.valueOf(acct);
                double b = store.getNum("BANK" + (char)1 + "B" + (char)1 + a, true);
                double c = store.getNum("BANK" + (char)1 + "C" + (char)1 + a, true);
                double fee = store.getNum("BANK" + (char)1 + "FEE" + (char)1 + a, true);
                double expected = 2 * fee;
                double actual = b - c;
                if (Math.abs(actual - expected) > 0.01) {
                    violations.incrementAndGet();
                    failures.add("acct=" + a + " B-C=" + actual + " expected=" + expected);
                }
            }
        }

        void checkJournalCount(StoreApi store, long expected) {
            double actual = store.getNum("BANK" + (char)1 + "N", true);
            if (Math.abs(actual - expected) > 0.01) {
                violations.incrementAndGet();
                failures.add("journal count=" + actual + " expected=" + expected);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String storeType = args.length > 0 ? args[0] : "mem";
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        System.out.println("=== PerfMetrics: " + storeType + " store, " + seconds + "s, " + threads + " threads ===");
        System.out.println();

        // Create store
        String tmp = System.getProperty("java.io.tmpdir");
        Object storeObj;
        switch (storeType) {
            case "h2":
                storeObj = new H2Store(tmp + "/perf-" + System.nanoTime() + ".mv.db");
                break;
            case "tiered":
                storeObj = new jvmumps.store.TieredStore(
                    tmp + "/perf-" + System.nanoTime() + ".mv.db",
                    tmp + "/perf-" + System.nanoTime() + ".wal",
                    10000, 100);
                break;
            default:
                storeObj = new MStore();
        }
        StoreApi store = (StoreApi) storeObj;

        // Initialize bank
        for (int i = 0; i < 10; i++) {
            String a = String.valueOf(i);
            store.setNum("BANK" + (char)1 + "C" + (char)1 + a, true, 0);
            store.setNum("BANK" + (char)1 + "B" + (char)1 + a, true, 0);
            store.setNum("BANK" + (char)1 + "FEE" + (char)1 + a, true, 0);
        }
        store.setNum("BANK" + (char)1 + "N", true, 0);

        // Warmup
        for (int i = 0; i < WARMUP_TXNS; i++) {
            bankTxn(store, i);
        }

        // Run benchmark
        LatencyRecorder lat = new LatencyRecorder();
        TpsRecorder tps = new TpsRecorder();
        InvariantChecker checker = new InvariantChecker();
        AtomicLong totalTxns = new AtomicLong();

        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);

        // Sampler thread
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(tps::sample, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Worker threads
        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(ex.submit(() -> {
                Random rng = new Random(threadIdx * 1000);
                while (running.get()) {
                    long t0 = System.nanoTime();
                    bankTxn(store, rng.nextInt(1000000));
                    long t1 = System.nanoTime();
                    lat.record(t1 - t0);
                    tps.record();
                    totalTxns.incrementAndGet();
                }
            }));
        }

        // Run for specified duration
        Thread.sleep(seconds * 1000L);
        running.set(false);

        // Wait for workers
        for (Future<?> f : futures) {
            try { f.get(5, TimeUnit.SECONDS); } catch (Exception e) {}
        }
        ex.shutdown();
        sampler.shutdown();

        // Check invariants
        long expectedJournal = totalTxns.get(); // 1 journal entry per txn (approx)
        checker.checkBankInvariant(store);
        checker.checkJournalCount(store, expectedJournal);

        // Report
        System.out.println("=== RESULTS ===");
        System.out.println();
        System.out.println("--- Sustained Throughput ---");
        System.out.printf("  Duration:      %d seconds%n", seconds);
        System.out.printf("  Total txns:    %d%n", totalTxns.get());
        System.out.printf("  Mean TPS:      %.0f%n", tps.mean());
        System.out.printf("  Min TPS:       %.0f%n", tps.min());
        System.out.printf("  Max TPS:       %.0f%n", tps.max());
        System.out.printf("  Stddev TPS:    %.0f%n", stddev(tps));
        System.out.println();
        System.out.println("--- Response Time (latency percentiles) ---");
        System.out.printf("  Mean:          %.1f us%n", lat.mean());
        System.out.printf("  p50:           %.1f us%n", lat.percentile(50));
        System.out.printf("  p95:           %.1f us%n", lat.percentile(95));
        System.out.printf("  p99:           %.1f us%n", lat.percentile(99));
        System.out.printf("  p999:          %.1f us%n", lat.percentile(99.9));
        System.out.printf("  Max:           %.1f us%n", lat.percentile(100));
        System.out.println();
        System.out.println("--- Transactional Accuracy ---");
        System.out.printf("  Violations:    %d%n", checker.violations.get());
        if (!checker.failures.isEmpty()) {
            System.out.println("  Failures:");
            for (String f : checker.failures.subList(0, Math.min(5, checker.failures.size()))) {
                System.out.println("    " + f);
            }
        } else {
            System.out.println("  Status:        ALL INVARIANTS HELD");
        }
        System.out.println();
        System.out.printf("=== DONE (%s) ===%n", storeType);

        // Cleanup
        if (storeObj instanceof AutoCloseable ac) ac.close();
        System.exit(0);
    }

    /** Run one bank transaction (DEP or WDR). */
    static void bankTxn(StoreApi store, int seed) {
        Random rng = new Random(seed);
        String acct = String.valueOf(rng.nextInt(10));
        double amount = rng.nextInt(100) + 1;
        boolean isDeposit = rng.nextBoolean();

        if (isDeposit) {
            double cb = store.getNum("BANK" + (char)1 + "C" + (char)1 + acct, true) + amount;
            store.setNum("BANK" + (char)1 + "C" + (char)1 + acct, true, cb);
            store.setNum("BANK" + (char)1 + "B" + (char)1 + acct, true,
                    store.getNum("BANK" + (char)1 + "B" + (char)1 + acct, true) + amount);
        } else {
            double wc = store.getNum("BANK" + (char)1 + "C" + (char)1 + acct, true);
            double wn = wc - amount;
            double wf = 0;
            if (wn < 0) { wf = 3; wn -= 3; }
            if (wn < -100) return;
            store.setNum("BANK" + (char)1 + "C" + (char)1 + acct, true, wn);
            store.setNum("BANK" + (char)1 + "B" + (char)1 + acct, true,
                    store.getNum("BANK" + (char)1 + "B" + (char)1 + acct, true) - amount + wf);
            if (wf > 0) {
                store.setNum("BANK" + (char)1 + "FEE" + (char)1 + acct, true,
                        store.getNum("BANK" + (char)1 + "FEE" + (char)1 + acct, true) + wf);
            }
        }
        store.increment("BANK" + (char)1 + "N", true, 1);
    }

    static double stddev(TpsRecorder tps) {
        double mean = tps.mean();
        double variance = tps.samples.stream()
                .mapToDouble(s -> (s[1] - mean) * (s[1] - mean))
                .average().orElse(0);
        return Math.sqrt(variance);
    }
}
