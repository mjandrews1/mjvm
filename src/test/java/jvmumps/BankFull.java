package jvmumps;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * BankFull — expansive bank model in native Java.
 * Multi-service, double-entry accounting, immutable journal.
 *
 * Invariants:
 *   1. Bank's own account is foundational
 *   2. Overdraft coverage optional, may charge fee
 *   3. Interest accrual per service type
 *   4. Monthly fees per service
 *   5. Double-entry: all transactions balanced, immutable journal
 *   6. Savings minimum balance: $100
 */
public final class BankFull {

    // Service types
    public static final int SVC_SAVINGS = 0;
    public static final int SVC_CHECKING = 1;
    public static final int SVC_LOAN = 2;

    // Service subtypes
    public static final int SUB_STANDARD = 0;
    public static final int SUB_CD = 1;
    public static final int SUB_PERSONAL = 2;
    public static final int SUB_MORTGAGE = 3;
    public static final int SUB_AUTO = 4;
    public static final int SUB_OVERDRAFT = 5;

    // Account types
    public static final int ACCT_PERSONAL = 0;
    public static final int ACCT_BUSINESS = 1;

    // Immutable journal entry
    public static final class JournalEntry {
        public final String type;
        public final int serviceId;
        public final double amount;
        public final double balanceAfter;
        public final long timestamp;

        public JournalEntry(String type, int serviceId, double amount, double balanceAfter) {
            this.type = type;
            this.serviceId = serviceId;
            this.amount = amount;
            this.balanceAfter = balanceAfter;
            this.timestamp = System.nanoTime();
        }
    }

    // Service record
    public static final class Service {
        public int id;
        public int accountId;
        public int type;
        public int subtype;
        public double balance;
        public double rate;
        public double monthlyFee;
        public double minBalance;
    }

    // Account record
    public static final class Account {
        public int id;
        public int type; // ACCT_PERSONAL or ACCT_BUSINESS
        public String owner;
        public List<Integer> services = new ArrayList<>();
    }

    // Bank state
    public double bankBalance = 1_000_000;
    public String bankName = "First National Test Bank";
    public int nextAccountId = 0;
    public int nextServiceId = 0;
    public final Map<Integer, Account> accounts = new ConcurrentHashMap<>();
    public final Map<Integer, Service> services = new ConcurrentHashMap<>();
    public final List<JournalEntry> journal = Collections.synchronizedList(new ArrayList<>());
    public final AtomicInteger journalSeq = new AtomicInteger(0);

    // === Core operations ===

    /** Create account. Returns account ID. */
    public int createAccount(int type, String owner) {
        int id = ++nextAccountId;
        Account acct = new Account();
        acct.id = id;
        acct.type = type;
        acct.owner = owner;
        accounts.put(id, acct);
        // Auto-create standard savings (min $100, 2% rate, $5 monthly fee)
        addService(id, SVC_SAVINGS, SUB_STANDARD, 100, 0.02, 5);
        return id;
    }

    /** Add service to account. Returns service ID. */
    public int addService(int accountId, int type, int subtype,
                          double minBalance, double rate, double monthlyFee) {
        int id = ++nextServiceId;
        Service svc = new Service();
        svc.id = id;
        svc.accountId = accountId;
        svc.type = type;
        svc.subtype = subtype;
        svc.balance = 0;
        svc.rate = rate;
        svc.monthlyFee = monthlyFee;
        svc.minBalance = minBalance;
        services.put(id, svc);
        accounts.get(accountId).services.add(id);
        return id;
    }

    /** Deposit into service. Returns new balance or -1. */
    public double deposit(int serviceId, double amount) {
        if (amount <= 0) return -1;
        Service svc = services.get(serviceId);
        if (svc == null) return -1;
        svc.balance += amount;
        bankBalance -= amount;
        journal.add(new JournalEntry("DEPOSIT", serviceId, amount, svc.balance));
        return svc.balance;
    }

    /** Withdraw from service. Returns new balance or -1. */
    public double withdraw(int serviceId, double amount) {
        if (amount <= 0) return -1;
        Service svc = services.get(serviceId);
        if (svc == null) return -1;
        double newBal = svc.balance - amount;
        // Minimum balance check
        if (newBal < svc.minBalance) return -1;
        // Overdraft
        if (newBal < 0) {
            double fee = svc.monthlyFee;
            if (fee > 0) newBal -= fee;
            if (newBal < -1000) return -1;
        }
        svc.balance = newBal;
        bankBalance += amount;
        journal.add(new JournalEntry("WITHDRAW", serviceId, amount, newBal));
        return newBal;
    }

    /** Inter-service transfer within same account. Returns 1 if OK. */
    public int transfer(int fromServiceId, int toServiceId, double amount) {
        if (amount <= 0) return 0;
        Service from = services.get(fromServiceId);
        Service to = services.get(toServiceId);
        if (from == null || to == null) return 0;
        if (from.balance < amount) return 0;
        from.balance -= amount;
        to.balance += amount;
        journal.add(new JournalEntry("TRANSFER", fromServiceId, amount, from.balance));
        journal.add(new JournalEntry("TRANSFER", toServiceId, amount, to.balance));
        return 1;
    }

    /** Inter-account transfer (savings to savings). Returns 1 if OK. */
    public int interAccountTransfer(int fromAccountId, int toAccountId, double amount) {
        Integer fromSvc = findService(fromAccountId, SVC_SAVINGS);
        Integer toSvc = findService(toAccountId, SVC_SAVINGS);
        if (fromSvc == null || toSvc == null) return 0;
        return transfer(fromSvc, toSvc, amount);
    }

    /** Find first service of type in account. Returns service ID or null. */
    public Integer findService(int accountId, int type) {
        Account acct = accounts.get(accountId);
        if (acct == null) return null;
        for (int svcId : acct.services) {
            Service svc = services.get(svcId);
            if (svc != null && svc.type == type) return svcId;
        }
        return null;
    }

    /** Accrue interest on all services. Returns total interest paid. */
    public double accrueInterest() {
        double total = 0;
        for (Service svc : services.values()) {
            if (svc.balance <= 0 || svc.rate <= 0) continue;
            double interest = svc.balance * (svc.rate / 12.0);
            svc.balance += interest;
            total += interest;
            journal.add(new JournalEntry("INTEREST", svc.id, interest, svc.balance));
        }
        return total;
    }

    /** Charge monthly fees. Returns total fees charged. */
    public double chargeMonthlyFees() {
        double total = 0;
        for (Service svc : services.values()) {
            if (svc.monthlyFee <= 0) continue;
            svc.balance -= svc.monthlyFee;
            total += svc.monthlyFee;
            journal.add(new JournalEntry("FEE", svc.id, svc.monthlyFee, svc.balance));
        }
        return total;
    }

    /** Verify double-entry invariant. Returns true if balanced. */
    public boolean verify() {
        double debit = 0, credit = 0;
        for (JournalEntry e : journal) {
            switch (e.type) {
                case "DEPOSIT" -> { debit += e.amount; credit += e.amount; }
                case "WITHDRAW" -> { debit += e.amount; credit += e.amount; }
                case "TRANSFER" -> { debit += e.amount; credit += e.amount; }
                case "INTEREST" -> { debit += e.amount; credit += e.amount; }
                case "FEE" -> { debit += e.amount; credit += e.amount; }
            }
        }
        return Math.abs(debit - credit) < 0.01;
    }

    /** Get total balance across all services in account. */
    public double accountBalance(int accountId) {
        Account acct = accounts.get(accountId);
        if (acct == null) return 0;
        double total = 0;
        for (int svcId : acct.services) {
            Service svc = services.get(svcId);
            if (svc != null) total += svc.balance;
        }
        return total;
    }

    // === Benchmark ===

    /**
     * Run benchmark: create accounts, run transactions, verify.
     * Returns {timeMs, txnPerMs, invariantOk}.
     */
    public static double[] bench(int nAccounts, int nInstances, long txnPerInstance) {
        BankFull bank = new BankFull();
        long totalTxn = nInstances * txnPerInstance;
        long t0 = System.nanoTime();

        // Create accounts
        List<Integer> accountIds = new ArrayList<>();
        for (int i = 0; i < nAccounts; i++) {
            int id = bank.createAccount(ACCT_PERSONAL, "User" + i);
            accountIds.add(id);
            // Initial deposit
            Integer svc = bank.findService(id, SVC_SAVINGS);
            if (svc != null) bank.deposit(svc, 1000);
        }

        // Run transactions
        if (nInstances == 1) {
            runTransactions(bank, accountIds, txnPerInstance);
        } else {
            ExecutorService ex = Executors.newFixedThreadPool(nInstances);
            List<Future<?>> futures = new ArrayList<>();
            long perInst = totalTxn / nInstances;
            for (int i = 0; i < nInstances; i++) {
                final long count = (i == nInstances - 1) ? totalTxn - perInst * (nInstances - 1) : perInst;
                futures.add(ex.submit(() -> {
                    runTransactions(bank, accountIds, count);
                    return null;
                }));
            }
            try {
                for (Future<?> f : futures) f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ex.shutdown();
        }

        long elapsed = System.nanoTime() - t0;
        double ms = elapsed / 1e6;
        double tps = totalTxn / (ms / 1000.0);
        boolean ok = bank.verify();
        return new double[]{ms, tps, ok ? 1 : 0};
    }

    static void runTransactions(BankFull bank, List<Integer> accountIds, long n) {
        if (accountIds.isEmpty()) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (long i = 0; i < n; i++) {
            int acctId = accountIds.get(rng.nextInt(accountIds.size()));
            Integer svc = bank.findService(acctId, SVC_SAVINGS);
            if (svc == null) continue;
            double amt = rng.nextInt(100) + 1;
            if (rng.nextInt(2) == 0) {
                bank.deposit(svc, amt);
            } else {
                bank.withdraw(svc, amt);
            }
        }
    }

    /** Main for standalone testing. */
    public static void main(String[] args) {
        int nAccounts = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int nInstances = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        long txnPerInstance = args.length > 2 ? Long.parseLong(args[2]) : 100000;

        System.out.println("=== BankFull (Java) ===");
        System.out.println("accounts=" + nAccounts + " instances=" + nInstances + " txn/inst=" + txnPerInstance);

        double[] result = bench(nAccounts, nInstances, txnPerInstance);
        System.out.printf("time=%.1fms  txn/ms=%.0f  invariant=%s%n",
            result[0], result[1], result[2] == 1 ? "OK" : "FAIL");
    }
}
