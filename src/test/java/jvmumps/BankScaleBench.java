package jvmumps;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Bank scale benchmark — two modes:
 *
 * <p>Mode 1: 10,000 accounts, 1-10 concurrent instances Mode 2: 0 to 250,000 accounts, 1 instance
 *
 * <p>Usage: java jvmumps.BankScaleBench [store] [mode] store = mem|h2|tiered (default: mem) mode =
 * concurrency|accounts (default: both)
 */
public final class BankScaleBench {

  static String loadBank() throws Exception {
    return Files.readString(Path.of("src/main/m/lib/BANK.m"));
  }

  static int CACHE_SIZE = 10000; // configurable via --cache

  static Object makeStore(String type) throws Exception {
    String tmp = System.getProperty("java.io.tmpdir");
    switch (type) {
      case "tiered":
        return new jvmumps.store.TieredStore(
            tmp + "/scale-" + System.nanoTime() + ".mv.db",
            tmp + "/scale-" + System.nanoTime() + ".wal",
            CACHE_SIZE,
            100);
      case "h2":
        return new H2Store(tmp + "/scale-" + System.nanoTime() + ".mv.db");
      default:
        return new MStore();
    }
  }

  static String runOne(String bank, Object store, long n, long nAccounts, int instanceId)
      throws Exception {
    var rt = new mjvm.runtime.MRuntime((jvmumps.StoreApi) store);
    var fe = new mjvm.compiler.MFrontend();
    var libProg = fe.compileProgram(bank);
    var libTags = fe.lastTags();
    mjvm.runtime.IrMachine.run(libProg, rt, libTags);
    rt.reset();
    String code = "D INIT^BANK W $$BENCH2^BANK(" + n + "," + nAccounts + "),!\n";
    var benchProg = fe.compileProgram(code);
    mjvm.runtime.IrMachine.run(benchProg, rt, fe.lastTags());
    return rt.output().strip();
  }

  public static void main(String[] args) throws Exception {
    String storeType = "mem";
    String mode = "both";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--cache") && i + 1 < args.length) {
        CACHE_SIZE = Integer.parseInt(args[i + 1]);
      } else if (args[i].equals("mem") || args[i].equals("h2") || args[i].equals("tiered")) {
        storeType = args[i];
      } else if (args[i].equals("concurrency")
          || args[i].equals("accounts")
          || args[i].equals("both")) {
        mode = args[i];
      }
    }
    String bank = loadBank();

    if (mode.equals("concurrency") || mode.equals("both")) {
      runConcurrencyMode(bank, storeType);
    }
    if (mode.equals("accounts") || mode.equals("both")) {
      runAccountsMode(bank, storeType);
    }
    System.out.println("DONE");
    System.exit(0);
  }

  /** Mode 1: 10,000 accounts, 1-10 concurrent instances. */
  static void runConcurrencyMode(String bank, String storeType) throws Exception {
    System.out.println("=== Mode 1: 10,000 accounts, 1-10 instances ===");
    System.out.println("store=" + storeType + " cache=" + CACHE_SIZE);
    System.out.println();
    System.out.println("instances    time_ms    txn/ms");
    System.out.println("---------    -------    ------");

    long n = 100000; // 100K transactions
    long nAccounts = 10000;

    for (int instances = 1; instances <= 10; instances++) {
      long t0 = System.nanoTime();
      if (instances == 1) {
        var store = makeStore(storeType);
        runOne(bank, store, n, nAccounts, 0);
      } else {
        var store = makeStore(storeType);
        ExecutorService ex = Executors.newFixedThreadPool(instances);
        List<Future<?>> futures = new ArrayList<>();
        long perInst = n / instances;
        for (int i = 0; i < instances; i++) {
          final long count = (i == instances - 1) ? n - perInst * (instances - 1) : perInst;
          final int idx = i;
          futures.add(
              ex.submit(
                  () -> {
                    runOne(bank, store, count, nAccounts, idx);
                    return null;
                  }));
        }
        for (Future<?> f : futures) f.get();
        ex.shutdown();
      }
      long t1 = System.nanoTime();
      double ms = (t1 - t0) / 1e6;
      double tps = n / (ms / 1000);
      System.out.printf("%9d    %7.1f    %6.1f%n", instances, ms, tps);
    }
    System.out.println();
  }

  /** Mode 2: 0 to 250,000 accounts, 1 instance. */
  static void runAccountsMode(String bank, String storeType) throws Exception {
    System.out.println("=== Mode 2: 0 to 250,000 accounts, 1 instance ===");
    System.out.println("store=" + storeType + " cache=" + CACHE_SIZE);
    System.out.println();
    System.out.println("accounts     time_ms    txn/ms");
    System.out.println("---------    -------    ------");

    long n = 100000; // 100K transactions
    long[] accountCounts = {0, 1, 10, 100, 1000, 10000, 50000, 100000, 250000};

    for (long nAccounts : accountCounts) {
      long t0 = System.nanoTime();
      var store = makeStore(storeType);
      runOne(bank, store, n, nAccounts, 0);
      long t1 = System.nanoTime();
      double ms = (t1 - t0) / 1e6;
      double tps = n / (ms / 1000);
      System.out.printf("%9d    %7.1f    %6.1f%n", nAccounts, ms, tps);
    }
    System.out.println();
  }
}
