/**
 * BankBenchmark.java — Native Java bank benchmark (T83). Double-entry accounting:
 * B(acct)-C(acct)=2*FEE(acct). Same workload as BANK.m BENCH2: random deposits/withdrawals across N
 * accounts.
 */
public class BankBenchmark {
  static final int ACCOUNTS = 10000;
  static double[] checking = new double[ACCOUNTS];
  static double[] bank = new double[ACCOUNTS];
  static double[] fees = new double[ACCOUNTS];
  static long txnCount = 0;
  static java.util.concurrent.ThreadLocalRandom rng =
      java.util.concurrent.ThreadLocalRandom.current();

  static void deposit(int acct, double amt) {
    checking[acct] += amt;
    bank[acct] += amt;
    txnCount++;
  }

  static void withdraw(int acct, double amt) {
    double c = checking[acct];
    double n = c - amt;
    double fee = 0;
    if (n < 0) {
      fee = 3;
      n -= 3;
    }
    if (n < -100) return; // reject
    checking[acct] = n;
    bank[acct] = bank[acct] - amt + fee;
    if (fee > 0) fees[acct] += fee;
    txnCount++;
  }

  static void runBatch(int n) {
    for (int i = 0; i < n; i++) {
      int acct = rng.nextInt(ACCOUNTS);
      double amt = rng.nextInt(100) + 1;
      if (rng.nextInt(2) == 0) deposit(acct, amt);
      else withdraw(acct, amt);
    }
  }

  static boolean checkInvariant() {
    for (int i = 0; i < ACCOUNTS; i++) {
      if (Math.abs(bank[i] - checking[i] - 2 * fees[i]) > 1e-9) return false;
    }
    return true;
  }

  public static void main(String[] args) {
    long maxScale = args.length > 0 ? Long.parseLong(args[0]) : 10_000_000L;
    long[] scales = {1000, 10000, 100000, 1000000, 10000000};

    System.out.println("=== Bank Native Benchmark: Java ===");
    System.out.println("accounts=" + ACCOUNTS + "  maxScale=" + maxScale);
    System.out.println();
    System.out.println("scale        batch_ms    txn/ms    total_ms    invariant");
    System.out.println("-----------  --------    ------    --------    ----------");

    for (long n : scales) {
      if (n > maxScale) break;
      // Reset
      java.util.Arrays.fill(checking, 0);
      java.util.Arrays.fill(bank, 0);
      java.util.Arrays.fill(fees, 0);
      txnCount = 0;
      rng = java.util.concurrent.ThreadLocalRandom.current();

      // Warmup
      runBatch(1000);

      // Reset again
      java.util.Arrays.fill(checking, 0);
      java.util.Arrays.fill(bank, 0);
      java.util.Arrays.fill(fees, 0);
      txnCount = 0;
      rng = java.util.concurrent.ThreadLocalRandom.current();

      int batchSize = 100000;
      long batches = Math.max(1, n / batchSize);
      long perBatch = n / batches;

      long totalNs = 0;
      for (long b = 0; b < batches; b++) {
        long t0 = System.nanoTime();
        runBatch((int) perBatch);
        totalNs += (System.nanoTime() - t0);
      }

      double totalMs = totalNs / 1e6;
      double batchMs = totalMs / batches;
      double txnPerMs = txnCount / (totalMs / 1000.0);
      boolean inv = checkInvariant();

      System.out.printf(
          "%,11d  %8.1f    %6.1f    %8.1f    %s%n",
          txnCount, batchMs, txnPerMs, totalMs, inv ? "OK" : "FAIL");
    }
    System.out.println();
    System.out.println("DONE");
  }
}
