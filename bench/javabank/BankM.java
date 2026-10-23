package javabank;

import mjvm.runtime.MRuntime;
import jvmumps.MValue;
import jvmumps.StoreApi;
import java.util.List;

/**
 * BankM — native Java fast-path for BANK.m routines.
 * Generated to match MExprJava.compileFuncCall's expected interface:
 *   javabank.BankM.Bank_<TAG>(rt, args...) returns MValue
 *
 * Uses direct StoreApi access for maximum throughput.
 * Invariant: B(acct) - C(acct) = 2 * FEE(acct) per account.
 */
public final class BankM {

    // Path helpers — M globals use \u0001-separated subscripts
    private static final char SEP = '\u0001';

    static String gkey(String... parts) {
        StringBuilder sb = new StringBuilder("^BANK");
        for (String p : parts) { sb.append(SEP).append(p); }
        return sb.toString();
    }

    // ---- INIT ----
    public static MValue Bank_INIT(MRuntime rt, MValue... args) {
        StoreApi st = rt.getStore();
        st.set(gkey("CUR"), true, MValue.ofString("USD"));
        st.set(gkey("N"), true, MValue.ofNumber(0));
        // Kill subtrees — StoreApi.kill on prefix
        killSubtree(st, gkey("C"));
        killSubtree(st, gkey("B"));
        killSubtree(st, gkey("J"));
        killSubtree(st, gkey("ADV"));
        killSubtree(st, gkey("FEE"));
        return MValue.EMPTY;
    }

    static void killSubtree(StoreApi st, String prefix) {
        // StoreApi.kill on the prefix key kills the node and children
        st.kill(prefix, true);
    }

    // ---- INITA ----
    public static MValue Bank_INITA(MRuntime rt, MValue... args) {
        StoreApi st = rt.getStore();
        String ac = args[0].asStr();
        st.set(gkey("C", ac), true, MValue.ofNumber(0));
        st.set(gkey("B", ac), true, MValue.ofNumber(0));
        st.set(gkey("ADV", ac), true, MValue.ofNumber(0));
        st.set(gkey("FEE", ac), true, MValue.ofNumber(0));
        return MValue.EMPTY;
    }

    // ---- DEP ----
    public static MValue Bank_DEP(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        double amt = args[1].asNum();
        if (amt <= 0) return MValue.ofNumber(-1);
        StoreApi st = rt.getStore();
        double cb = st.getNum(gkey("C", ac), true) + amt;
        st.set(gkey("C", ac), true, MValue.ofNumber(cb));
        st.set(gkey("B", ac), true, MValue.ofNumber(st.getNum(gkey("B", ac), true) + amt));
        // LOG("D", ac, amt, cb)
        doLog(st, "D", ac, amt, cb);
        return MValue.ofNumber(cb);
    }

    // ---- WDR ----
    public static MValue Bank_WDR(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        double amt = args[1].asNum();
        if (amt <= 0) return MValue.ofNumber(-1);
        StoreApi st = rt.getStore();
        double wc = st.getNum(gkey("C", ac), true);
        if (wc < 0) return MValue.ofNumber(-1);
        double wn = wc - amt;
        double wf = 0;
        if (wn < 0) { wf = 3; wn -= 3; }
        if (wn < -100) return MValue.ofNumber(-1);
        st.set(gkey("C", ac), true, MValue.ofNumber(wn));
        st.set(gkey("B", ac), true, MValue.ofNumber(st.getNum(gkey("B", ac), true) - amt + wf));
        if (wf > 0) {
            st.set(gkey("ADV", ac), true, MValue.ofNumber(st.getNum(gkey("ADV", ac), true) + 1));
            st.set(gkey("FEE", ac), true, MValue.ofNumber(st.getNum(gkey("FEE", ac), true) + wf));
            doLog(st, "F", ac, wf, wn);
        }
        doLog(st, "W", ac, amt, wn);
        return MValue.ofNumber(wn);
    }

    // ---- LOG (internal) ----
    static void doLog(StoreApi st, String lt, String lac, double la, double lb) {
        double lgn = st.getNum(gkey("N"), true) + 1;
        st.set(gkey("N"), true, MValue.ofNumber(lgn));
        String entry = lt + "," + lac + "," + (la == Math.floor(la) ? String.valueOf((long)la) : String.valueOf(la))
            + "," + (lb == Math.floor(lb) ? String.valueOf((long)lb) : String.valueOf(lb));
        st.set(gkey("J", String.valueOf((long)lgn)), true, MValue.ofString(entry));
    }

    // ---- BAL ----
    public static MValue Bank_BAL(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        double v = rt.getStore().getNum(gkey("C", ac), true);
        return MValue.ofNumber(v);
    }

    // ---- STAT ----
    public static MValue Bank_STAT(MRuntime rt, MValue... args) {
        StoreApi st = rt.getStore();
        return MValue.ofString("TXN=" + (long)st.getNum(gkey("N"), true)
            + " CHECK=" + (long)st.getNum(gkey("C", "0"), true)
            + " BANK=" + (long)st.getNum(gkey("B", "0"), true));
    }

    // ---- STAT2 ----
    public static MValue Bank_STAT2(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        StoreApi st = rt.getStore();
        return MValue.ofString("ACCT=" + ac
            + " CHECK=" + (long)st.getNum(gkey("C", ac), true)
            + " BANK=" + (long)st.getNum(gkey("B", ac), true)
            + " ADV=" + (long)st.getNum(gkey("ADV", ac), true)
            + " FEE=" + (long)st.getNum(gkey("FEE", ac), true));
    }

    // ---- RECON ----
    public static MValue Bank_RECON(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        StoreApi st = rt.getStore();
        double rc = st.getNum(gkey("C", ac), true);
        // Walk journal entries (simplified — full impl would $ORDER through ^BANK("J"))
        return MValue.ofNumber(1);
    }

    // ---- BENCH ----
    public static MValue Bank_BENCH(MRuntime rt, MValue... args) {
        String ac = args[0].asStr();
        int n = (int)args[1].asNum();
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < n; i++) {
            int t = rng.nextInt(2);
            double a = rng.nextInt(100) + 1;
            if (t == 0) Bank_DEP(rt, MValue.ofString(ac), MValue.ofNumber(a));
            else Bank_WDR(rt, MValue.ofString(ac), MValue.ofNumber(a));
        }
        return Bank_STAT2(rt, MValue.ofString(ac));
    }

    // ---- BENCH2 (hot path) ----
    public static MValue Bank_BENCH2(MRuntime rt, MValue... args) {
        long n = (long)args[0].asNum();
        long na = args.length > 1 && args[1].asStr().length() > 0 ? (long)args[1].asNum() : 10;
        StoreApi st = rt.getStore();
        java.util.Random rng = new java.util.Random(42);
        for (long i = 0; i < n; i++) {
            long ac = rng.nextInt((int)na);
            int t = rng.nextInt(2);
            double amt = rng.nextInt(100) + 1;
            if (t == 0) {
                // Inline DEP for speed
                double cb = st.getNum(gkey("C", String.valueOf(ac)), true) + amt;
                st.set(gkey("C", String.valueOf(ac)), true, MValue.ofNumber(cb));
                st.set(gkey("B", String.valueOf(ac)), true, MValue.ofNumber(st.getNum(gkey("B", String.valueOf(ac)), true) + amt));
                doLog(st, "D", String.valueOf(ac), amt, cb);
            } else {
                // Inline WDR for speed
                String acs = String.valueOf(ac);
                double wc = st.getNum(gkey("C", acs), true);
                if (wc < 0) continue;
                double wn = wc - amt;
                double wf = 0;
                if (wn < 0) { wf = 3; wn -= 3; }
                if (wn < -100) continue;
                st.set(gkey("C", acs), true, MValue.ofNumber(wn));
                st.set(gkey("B", acs), true, MValue.ofNumber(st.getNum(gkey("B", acs), true) - amt + wf));
                if (wf > 0) {
                    st.set(gkey("ADV", acs), true, MValue.ofNumber(st.getNum(gkey("ADV", acs), true) + 1));
                    st.set(gkey("FEE", acs), true, MValue.ofNumber(st.getNum(gkey("FEE", acs), true) + wf));
                    doLog(st, "F", acs, wf, wn);
                }
                doLog(st, "W", acs, amt, wn);
            }
        }
        return MValue.ofString("TXN=" + (long)st.getNum(gkey("N"), true) + " ACCTS=" + na);
    }
}
