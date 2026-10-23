package jvmumps;

/**
 * StoreMetrics smoke test — verifies throughput, latency, cache hit rate,
 * and disk space monitoring.
 */
public final class StoreMetricsSmoke {

    public static void main(String[] args) throws Exception {
        String tmp = System.getProperty("java.io.tmpdir");
        String dbPath = tmp + "/metrics-" + System.nanoTime() + ".mv.db";
        String walPath = tmp + "/metrics-" + System.nanoTime() + ".wal";

        var store = new jvmumps.store.TieredStore(dbPath, walPath, 100, 1000);
        var metrics = store.metrics();

        // Write 200 keys (100 will fit in cache, 100 will evict)
        for (int i = 0; i < 200; i++) {
            store.set("K" + i, true, jvmumps.MValue.ofString("V" + i));
        }

        // Read 50 keys (should hit cache for recent ones)
        for (int i = 150; i < 200; i++) {
            store.get("K" + i, true);
        }

        // Check metrics
        boolean ok = true;

        if (metrics.writes() != 200) {
            System.out.println("FAIL: writes=" + metrics.writes() + " want=200");
            ok = false;
        }
        if (metrics.reads() != 50) {
            System.out.println("FAIL: reads=" + metrics.reads() + " want=50");
            ok = false;
        }
        if (metrics.cacheHits() + metrics.cacheMisses() != 50) {
            System.out.println("FAIL: hits+misses=" + (metrics.cacheHits() + metrics.cacheMisses()) + " want=50");
            ok = false;
        }
        if (metrics.walAppends() != 200) {
            System.out.println("FAIL: walAppends=" + metrics.walAppends() + " want=200");
            ok = false;
        }
        if (metrics.avgWriteUs() <= 0) {
            System.out.println("FAIL: avgWriteUs=" + metrics.avgWriteUs());
            ok = false;
        }

        // Disk check
        String disk = store.checkDisk();
        if (!disk.equals("OK") && !disk.equals("WARN")) {
            System.out.println("FAIL: checkDisk=" + disk);
            ok = false;
        }

        // Snapshot
        System.out.println("metrics: " + metrics.snapshot());
        System.out.println("disk: " + disk + " free=" + metrics.diskFreeBytes(dbPath) / 1024 / 1024 + "MB");

        store.close();
        System.out.println(ok ? "ALL METRICS TESTS PASSED" : "SOME METRICS TESTS FAILED");
        System.exit(ok ? 0 : 1);
    }
}
