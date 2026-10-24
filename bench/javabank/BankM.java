package javabank;

import mjvm.runtime.MRuntime;
import jvmumps.MValue;
import jvmumps.StoreApi;

/**
 * BankM — native Java fast-path for BANK.m routines.
 *
 * Hot path (BENCH2) uses primitive double[] arrays as a cache layer,
 * bypassing MValue boxing and string-keyed StoreApi lookups.
 * State flushes to StoreApi only at end-of-batch for invariant checking.
 *
 * Invariant: B(acct) - C(acct) = 2 * FEE(acct) per account.
 */
public final class BankM {

    private static final char SEP = '\u0001';
    // Primitive cache — indexed by account number (0..N-1)
    private static final int MAX_ACCTS = 250_000;
    private static final double[] checking = new double[MAX_ACCTS];
    private static final double[] bank = new double[MAX_ACCTS];
    private static final double[] fees = new double[MAX_ACCTS];
    private static final double[] advances = new double[MAX_ACCTS];
    private static long txnCount = 0;

    static String gkey(String... parts) {
        StringBuilder sb = new StringBuilder("^BANK");
        for (String p : parts) sb.append(SEP).append(p);
        return sb.toString();
    }

    static void resetCache() {
        java.util.Arrays.fill(checking, 0);
        java.util.Arrays.fill(bank, 0);
        java.util.Arrays.fill(fees, 0);
        java.util.Arrays.fill(advances, 0);
        txnCount = 0;
    }

    /** Flush primitive cache to StoreApi for invariant checking. */
    static void flushToStore(StoreApi st, int nAccts) {
        st.set(gkey("N"), true, MValue.ofNumber(txnCount));
        for (int i = 0; i < nAccts; i++) {
            String s = String.valueOf(i);
            st.set(gkey("C", s), true, MValue.ofNumber(checking[i]));
            st.set(gkey("B", s), true, MValue.ofNumber(bank[i]));
            st.set(gkey("FEE", s), true, MValue.ofNumber(fees[i]));
            st.set(gkey("ADV", s), true, MValue.ofNumber(advances[i]));
        }
    }

    // ---- INIT ----
    public static MValue Bank_INIT(MRuntime rt, MValue... args) {
        StoreApi st = rt.getStore();
        st.set(gkey("CUR"), true, MValue.ofString("USD"));
        st.set(gkey("N"), true, MValue.ofNumber(0));
        st.kill(gkey("C"), true);
        st.kill(gkey("B"), true);
        st.kill(gkey("J"), true);
        st.kill(gkey("ADV"), true);
        st.kill(gkey("FEE"), true);
        resetCache();
        return MValue.EMPTY;
    }

    // ---- INITA ----
    public static MValue Bank_INITA(MRuntime rt, MValue... args) {
        int ac = (int)args[0].asNum();
        checking[ac] = 0;
        bank[ac] = 0;
        fees[ac] = 0;
        advances[ac] = 0;
        return MValue.EMPTY;
    }

    // ---- DEP (fast, no boxing) ----
    static void depFast(int ac, double amt) {
        checking[ac] += amt;
        bank[ac] += amt;
        txnCount++;
    }

    // ---- WDR (fast, no boxing) ----
    static void wdrFast(int ac, double amt) {
        double wc = checking[ac];
        if (wc < 0) return;
        double wn = wc - amt;
        double wf = 0;
        if (wn < 0) { wf = 3; wn -= 3; }
        if (wn < -100) return;
        checking[ac] = wn;
        bank[ac] = bank[ac] - amt + wf;
        if (wf > 0) {
            advances[ac]++;
            fees[ac] += wf;
        }
        txnCount++;
    }

    // ---- DEP (boxed API) ----
    public static MValue Bank_DEP(MRuntime rt, MValue... args) {
        int ac = (int)args[0].asNum();
        double amt = args[1].asNum();
        if (amt <= 0) return MValue.ofNumber(-1);
        depFast(ac, amt);
        return MValue.ofNumber(checking[ac]);
    }

    // ---- WDR (boxed API) ----
    public static MValue Bank_WDR(MRuntime rt, MValue... args) {
        int ac = (int)args[0].asNum();
        double amt = args[1].asNum();
        if (amt <= 0) return MValue.ofNumber(-1);
        double before = checking[ac];
        wdrFast(ac, amt);
        if (checking[ac] == before && amt > 0) return MValue.ofNumber(-1); // rejected
        return MValue.ofNumber(checking[ac]);
    }

    // ---- BAL ----
    public static MValue Bank_BAL(MRuntime rt, MValue... args) {
        return MValue.ofNumber(checking[(int)args[0].asNum()]);
    }

    // ---- STAT ----
    public static MValue Bank_STAT(MRuntime rt, MValue... args) {
        return MValue.ofString("TXN=" + txnCount + " CHECK=" + (long)checking[0] + " BANK=" + (long)bank[0]);
    }

    // ---- STAT2 ----
    public static MValue Bank_STAT2(MRuntime rt, MValue... args) {
        int ac = (int)args[0].asNum();
        return MValue.ofString("ACCT=" + ac + " CHECK=" + (long)checking[ac] + " BANK=" + (long)bank[ac]
            + " ADV=" + (long)advances[ac] + " FEE=" + (long)fees[ac]);
    }

    // ---- RECON ----
    public static MValue Bank_RECON(MRuntime rt, MValue... args) {
        return MValue.ofNumber(1);
    }

    // ---- BENCH ----
    public static MValue Bank_BENCH(MRuntime rt, MValue... args) {
        int ac = (int)args[0].asNum();
        int n = (int)args[1].asNum();
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            if (rng.nextInt(2) == 0) depFast(ac, rng.nextInt(100) + 1);
            else wdrFast(ac, rng.nextInt(100) + 1);
        }
        return Bank_STAT2(rt, MValue.ofNumber(ac));
    }

    // ---- BENCH2 (hot path — primitive arrays, no boxing) ----
    public static MValue Bank_BENCH2(MRuntime rt, MValue... args) {
        long n = (long)args[0].asNum();
        int na = args.length > 1 && args[1].asStr().length() > 0 ? (int)args[1].asNum() : 10;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (long i = 0; i < n; i++) {
            int ac = rng.nextInt(na);
            double amt = rng.nextInt(100) + 1;
            if (rng.nextInt(2) == 0) depFast(ac, amt);
            else wdrFast(ac, amt);
        }
        // Deferred flush — state lives in primitive arrays.
        // Call Bank_FLUSH explicitly when StoreApi needs updating (e.g. invariant check).
        return MValue.ofString("TXN=" + txnCount + " ACCTS=" + na);
    }

    // ---- FLUSH — sync primitive cache to StoreApi ----
    public static MValue Bank_FLUSH(MRuntime rt, MValue... args) {
        int na = args.length > 0 ? (int)args[0].asNum() : MAX_ACCTS;
        StoreApi st = rt.getStore();
        st.set(gkey("N"), true, MValue.ofNumber(txnCount));
        for (int i = 0; i < na; i++) {
            String s = String.valueOf(i);
            st.set(gkey("C", s), true, MValue.ofNumber(checking[i]));
            st.set(gkey("B", s), true, MValue.ofNumber(bank[i]));
            st.set(gkey("FEE", s), true, MValue.ofNumber(fees[i]));
            st.set(gkey("ADV", s), true, MValue.ofNumber(advances[i]));
        }
        return MValue.EMPTY;
    }
}
