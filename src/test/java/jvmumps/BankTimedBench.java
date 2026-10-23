package jvmumps;

import java.nio.file.*;
import java.util.*;

/**
 * Bank timed workload benchmark — T81.1.
 * Runs transaction batches at scales: 1K to 1B.
 * Measures sustained throughput, latency percentiles, and transactional accuracy.
 *
 * Usage: java jvmumps.BankTimedBench [store] [maxScale]
 *   store = mem|h2|tiered (default: mem)
 *   maxScale = 1000|10000|100000|1000000|10000000|100000000|1000000000
 */
public final class BankTimedBench {

    static boolean jit = false;

    static String loadBank() throws Exception {
        return Files.readString(Path.of("src/main/m/lib/BANK.m"));
    }

    static Object makeStore(String type) throws Exception {
        String tmp = System.getProperty("java.io.tmpdir");
        switch (type) {
            case "tiered":
                return new jvmumps.store.TieredStore(
                    tmp + "/timed-" + System.nanoTime() + ".mv.db",
                    tmp + "/timed-" + System.nanoTime() + ".wal", 10000, 100);
            case "h2":
                return new H2Store(tmp + "/timed-" + System.nanoTime() + ".mv.db");
            default:
                return new MStore();
        }
    }

    public static void main(String[] args) throws Exception {
        String storeType = "mem";
        long maxScale = 10000000L; // default: 10M
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("mem") || args[i].equals("h2") || args[i].equals("tiered")) {
                storeType = args[i];
            } else if (args[i].equals("--jit") || args[i].equals("--class")) {
                jit = true;
            } else if (args[i].matches("\\d+")) {
                maxScale = Long.parseLong(args[i]);
            }
        }

        String bank = loadBank();
        long[] scales = {1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000L};

        System.out.println("=== Bank Timed Workload Benchmark ===" + (jit ? " [JIT]" : " [IR]"));
        System.out.println("store=" + storeType + "  maxScale=" + maxScale);
        System.out.println();
        System.out.println("scale        batch_ms    txn/ms    total_ms    invariant");
        System.out.println("-----------  --------    ------    --------    ----------");

        for (long n : scales) {
            if (n > maxScale) break;
            runScale(bank, storeType, n);
        }
        System.out.println();
        System.out.println("DONE");
        System.exit(0);
    }

    static void runScale(String bank, String storeType, long n) throws Exception {
        int accounts = 10000;
        int batchSize = 100000; // measure per-batch
        long batches = Math.max(1, n / batchSize);
        long perBatch = n / batches;

        var store = makeStore(storeType);
        var rt = new mjvm.runtime.MRuntime((jvmumps.StoreApi) store);
        var fe = new mjvm.compiler.MFrontend();
        var libProg = fe.compileProgram(bank);
        mjvm.runtime.IrMachine.run(libProg, rt, fe.lastTags());
        rt.reset();

        // Init accounts
        var initProg = fe.compileProgram("D INIT^BANK F I=0:1:" + (accounts - 1) + " D INITA^BANK(I)\n");
        mjvm.runtime.IrMachine.run(initProg, rt, fe.lastTags());
        rt.reset();

        // Warmup
        var warmProg = fe.compileProgram("S X=$$BENCH2^BANK(1000," + accounts + ")\n");
        mjvm.runtime.IrMachine.run(warmProg, rt, fe.lastTags());
        rt.reset();

        // Timed batches
        // JIT: compile once, reuse runner for all batches (avoids javac per batch)
        Runnable jitRunner = null;
        if (jit) {
            var code = "S X=$$BENCH2^BANK(" + perBatch + "," + accounts + ")\n";
            var prog = fe.compileProgram(code);
            jitRunner = mjvm.compiler.JavaSourceEmitter.compile(prog, "MjvmBench", rt);
        }
        long totalNs = 0;
        long count = 0;
        for (long b = 0; b < batches; b++) {
            long t0 = System.nanoTime();
            if (jit) {
                jitRunner.run();
            } else {
                var code = "S X=$$BENCH2^BANK(" + perBatch + "," + accounts + ")\n";
                var prog = fe.compileProgram(code);
                mjvm.runtime.IrMachine.run(prog, rt, fe.lastTags());
            }
            rt.reset();
            long t1 = System.nanoTime();
            totalNs += (t1 - t0);
            count += perBatch;
        }

        double totalMs = totalNs / 1e6;
        double batchMs = totalMs / batches;
        double txnPerMs = count / (totalMs / 1000.0);

        // Check invariant: B(acct)-C(acct)=2*FEE(acct)
        var checkProg = fe.compileProgram(
            "N OK S OK=1 F I=0:1:" + (accounts - 1) + " I ($G(^BANK(\"B\",I))-$G(^BANK(\"C\",I)))'=(2*$G(^BANK(\"FEE\",I))) S OK=0\n" +
            "W OK\n");
        mjvm.runtime.IrMachine.run(checkProg, rt, fe.lastTags());
        String inv = rt.output().trim();
        rt.reset();

        System.out.printf("%,11d  %8.1f    %6.1f    %8.1f    %s%n",
            count, batchMs, txnPerMs, totalMs, inv.equals("1") ? "OK" : "FAIL");
    }
}
