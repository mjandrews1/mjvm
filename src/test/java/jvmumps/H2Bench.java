package jvmumps;

import java.util.List;

/** Benchmark H2Store vs SqlStore for bank DEP pattern. */
public final class H2Bench {
    public static void main(String[] args) throws Exception {
        int n = 10000;
        String tmpDir = System.getProperty("java.io.tmpdir");

        // H2Store
        {
            String path = tmpDir + "/h2bench-" + System.nanoTime() + ".mv.db";
            var s = new H2Store(path);
            // Warmup
            for (int i = 0; i < 100; i++) depPattern(s, i);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) depPattern(s, i);
            long t1 = System.nanoTime();
            double us = (t1 - t0) / 1e3 / n;
            System.out.printf("H2Store DEP:    %.2f us/txn (n=%d)%n", us, n);
            s.close();
            new java.io.File(path).delete();
        }

        // SqlStore
        {
            String path = tmpDir + "/sqlbench-" + System.nanoTime() + ".db";
            var s = new SqlStore(path);
            for (int i = 0; i < 100; i++) depPattern(s, i);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) depPattern(s, i);
            long t1 = System.nanoTime();
            double us = (t1 - t0) / 1e3 / n;
            System.out.printf("SqlStore DEP:   %.2f us/txn (n=%d)%n", us, n);
        }

        // MapDBStore
        {
            String path = tmpDir + "/mapdbbench-" + System.nanoTime() + ".db";
            var s = new MapDBStore(path);
            for (int i = 0; i < 100; i++) depPattern(s, i);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) depPattern(s, i);
            long t1 = System.nanoTime();
            double us = (t1 - t0) / 1e3 / n;
            System.out.printf("MapDBStore DEP: %.2f us/txn (n=%d)%n", us, n);
            s.close();
            new java.io.File(path).delete();
        }

        // MStore
        {
            var s = new MStore();
            for (int i = 0; i < 100; i++) depPattern(s, i);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) depPattern(s, i);
            long t1 = System.nanoTime();
            double us = (t1 - t0) / 1e3 / n;
            System.out.printf("MStore DEP:     %.2f us/txn (n=%d)%n", us, n);
        }
    }

    static void depPattern(StoreApi store, int i) {
        List<String> acct = List.of("BANK", "C", "0");
        List<String> bank = List.of("BANK", "B", "0");
        List<String> n = List.of("BANK", "N");
        List<String> j = List.of("BANK", "J", String.valueOf(i));
        double cb = store.get(acct, true).asNum() + 10;
        store.set(acct, true, MValue.ofNumber(cb));
        double bb = store.get(bank, true).asNum() + 10;
        store.set(bank, true, MValue.ofNumber(bb));
        store.increment(n, true, 1);
        store.set(j, true, MValue.ofString("D,0,10," + (int) cb));
    }
}
