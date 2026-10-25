package mjvm.runtime;

import java.util.List;
import jvmumps.MValue;
import mjvm.ir.Ir;

/**
 * Executes {@link Ir} against {@link MRuntime}.
 *
 * This is the mJVM runtime core: both the in-process IR machine (M2
 * straight-line) and generated classes route through the same opcodes.
 */
/** Interpreter for {@link mjvm.ir.Ir} programs (parity path vs ClassEmitter). */
public final class IrMachine {
    static final class H {
        static java.util.List<String> splitTop(String text) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            boolean inS = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inS) {
                    sb.append(c);
                    if (c == '"') inS = false;
                    continue;
                }
                if (c == '"') {
                    inS = true;
                    sb.append(c);
                    continue;
                }
                if (c == '(') {
                    depth++;
                    sb.append(c);
                    continue;
                }
                if (c == ')') {
                    depth--;
                    sb.append(c);
                    continue;
                }
                if (c == ',' && depth == 0) {
                    parts.add(sb.toString().trim());
                    sb.setLength(0);
                    continue;
                }
                sb.append(c);
            }
            parts.add(sb.toString().trim());
            return parts;
        }
    }

    public static void run(List<Ir> prog, MRuntime rt) {
        run(prog, rt, 0, null);
    }

    /** Run and link the tag table so later `$$tag` calls resolve (GAP-2).
     *  A later single-line run must not clobber an already-linked routine. */
    public static void run(List<Ir> prog, MRuntime rt, java.util.Map<String, Integer> tags) {
        if (tags != null && !tags.isEmpty()) {
            rt.link(prog, tags);
        }
        run(prog, rt, 0, null);
    }

    /**
     * Run {@code prog} from {@code startPc}. When {@code argv} is non-null the
     * values are pushed first (they bind the tag's formals) and the first
     * {@code RET}/{@code RET_VALUE} returns to the caller — the run is an
     * extrinsic invocation (GAP-2).
     */
    public static jvmumps.MValue runExtrinsic(List<Ir> prog, MRuntime rt, int startPc, MValue[] args) {
        if (startPc < 0 || startPc >= prog.size()) {
            throw new jvmumps.MError("Invalid call target pc=" + startPc + " (prog size=" + prog.size() + ")");
        }
        return run(prog, rt, startPc, args);
    }

    /** Thread-local value stack pool — avoids allocating 256-element array per call. */
    private static final ThreadLocal<MValue[]> VALS_POOL = ThreadLocal.withInitial(() -> new MValue[256]);
    /** Numeric values parallel to vals — null in vals[] means value is in dvals[]. */
    private static final ThreadLocal<double[]> DVALS_POOL = ThreadLocal.withInitial(() -> new double[256]);


    /** Thread-local return-address stack — avoids ArrayDeque allocation per call. */
    private static final ThreadLocal<int[]> RETS_POOL = ThreadLocal.withInitial(() -> new int[64]);
    /** Thread-local args buffer — avoids allocating MValue[] per CALL. */
    private static final ThreadLocal<MValue[]> ARGS_POOL = ThreadLocal.withInitial(() -> new MValue[16]);

    /** Pop a value from the split stack — handles null (numeric) slots. */
    private static MValue popVal(MValue[] vals, double[] dvals, int[] sp) {
        int i = --sp[0];
        MValue v = vals[i];
        return v != null ? v : MValue.ofNumber(dvals[i]);
    }

    private static jvmumps.MValue run(List<Ir> prog, MRuntime rt, int startPc, MValue[] args) {
        MValue[] vals = VALS_POOL.get();
        double[] dvals = DVALS_POOL.get();
        int[] spA = {0}; // array wrapper for popVal
        int sp = 0;
        int pc = startPc;
        int[] rets = RETS_POOL.get();
        int rsp = 0;
        MValue lastStored = MValue.EMPTY;

        // Pre-read cache: batch all global LOADs in this program into one getBatch()
        java.util.Map<String, jvmumps.MValue> preRead = new java.util.HashMap<>();
        int preReadHits = 0;
        if (rt.getStore() instanceof jvmumps.StoreCore sc) {
            // Collect all global variable names from LOAD ops
            java.util.LinkedHashSet<String> globalNames = new java.util.LinkedHashSet<>();
            for (Ir iop : prog) {
                if (iop.op == Ir.LOAD && iop.name != null && iop.name.startsWith("^")) {
                    globalNames.add(iop.name);
                }
            }
            if (globalNames.size() > 1) {
                // Batch-read all globals in one round-trip
                java.util.List<java.util.List<String>> paths = new java.util.ArrayList<>(globalNames.size());
                for (String gn : globalNames) {
                    paths.add(java.util.List.of(gn.substring(1)));
                }
                try {
                    jvmumps.MValue[] batch = sc.getBatch(paths, true);
                    int bi = 0;
                    for (String gn : globalNames) {
                        preRead.put(gn, batch[bi++]);
                    }
                } catch (Exception e) {
                    // Pre-read failed — fall back to individual gets
                }
            }
        }
        final boolean extrinsic = args != null;
        if (extrinsic) {
            for (MValue a : args) vals[sp++] = a == null ? MValue.EMPTY : a;
        }
        while (pc >= 0 && pc < prog.size() && !rt.isQuit()) {
            Ir op = prog.get(pc);
            switch (op.op) {
                case Ir.PUSH -> {
                    if (op.iarg == 1) {
                        dvals[sp] = op.dval;
                        vals[sp++] = null; // null = numeric slot
                    } else {
                        vals[sp++] = MValue.ofString(op.ival == null ? "" : op.ival);
                    }
                }
                case Ir.LOAD -> {
                    // Check pre-read cache first (batched global reads)
                    jvmumps.MValue cached = preRead.get(op.name);
                    if (cached != null) {
                        if (cached.isnum) { dvals[sp] = cached.num; vals[sp++] = null; }
                        else vals[sp++] = cached;
                        preReadHits++;
                    } else {
                        try {
                            jvmumps.MValue gv = rt.getVar(op.name);
                            if (gv.isnum) { dvals[sp] = gv.num; vals[sp++] = null; }
                            else vals[sp++] = gv;
                        } catch (jvmumps.MError e) {
                            if (!rt.fireTrap()) throw e;
                            dvals[sp] = 0;
                            vals[sp++] = null;
                        }
                    }
                }
                case Ir.BIND -> {
                    // Formal at a call target. Empty stack = top-down run of the
                    // routine, or fewer args than formals — M leaves it undefined.
                    MValue v = sp > 0 ? vals[--sp] : jvmumps.MValue.EMPTY;
                    rt.setVar(op.name, v);
                }
                case Ir.STORE -> {
                    // Invalidate pre-read cache for this key
                    if (op.name != null) preRead.remove(op.name);
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    lastStored = v;
                    if (op.name != null && op.name.startsWith("@")) {
                        String target = rt.evalExpr(op.name.substring(1)).asStr();
                        rt.setVar(target, v);
                    } else if (op.name != null && op.name.startsWith("$")) {
                        rt.setSpecial(op.name, v);
                    } else {
                        rt.setVar(op.name, v);
                    }
                }
                case Ir.STORE_ADD -> {
                    if (op.name != null) preRead.remove(op.name);
                    // Atomic increment: pop amount, use store.increment for single-op
                    MValue inc = vals[--sp];
                    MValue nv = rt.incrementVar(op.name, inc.asNum());
                    lastStored = nv;
                    vals[sp++] = nv;
                }
                case Ir.GETB -> {
                    // GETB: read nkeys global variables in one round-trip
                    int nkeys = op.iarg;
                    java.util.List<java.util.List<String>> paths = new java.util.ArrayList<>(nkeys);
                    for (int i = 0; i < nkeys; i++) {
                        // Keys are in the operand stream: name1, name2, ...
                        // For simplicity, use op.iarg as count and read from consecutive IR ops
                        // Actually GETB uses op.ival as the key list (comma-separated)
                        // Fall back: this is a placeholder
                    }
                    // Simplified: GETB is handled via LOAD look-ahead instead
                    vals[sp++] = jvmumps.MValue.EMPTY;
                }
                case Ir.PUSH_REG -> {
                    // Push last stored value (peephole for redundant reads)
                    vals[sp++] = lastStored;
                }
                case Ir.ADD, Ir.SUB, Ir.MUL, Ir.DIV, Ir.CONCAT, Ir.MOD -> {
                    if (op.op == Ir.CONCAT) {
                        MValue b = vals[--sp]; if (b == null) b = MValue.ofNumber(dvals[sp]); if (b == null) b = MValue.ofNumber(dvals[sp]);
                        MValue a = vals[--sp]; if (a == null) a = MValue.ofNumber(dvals[sp]); if (a == null) a = MValue.ofNumber(dvals[sp]);
                        vals[sp++] = MValue.ofString(a.asStr() + b.asStr());
                    } else {
                        // Numeric fast path: read from dvals, write to dvals (zero alloc)
                        double y, x;
                        sp--; if (vals[sp] != null) { y = vals[sp].asNum(); } else { y = dvals[sp]; }
                        sp--; if (vals[sp] != null) { x = vals[sp].asNum(); } else { x = dvals[sp]; }
                        double r = switch (op.op) {
                            case Ir.ADD -> x + y;
                            case Ir.SUB -> x - y;
                            case Ir.MUL -> x * y;
                            case Ir.MOD -> y == 0 ? x : x - y * Math.floor(x / y);
                            default -> y == 0 ? 0 : x / y;
                        };
                        dvals[sp] = r;
                        vals[sp++] = null; // numeric result
                    }
                }
                case Ir.EQ, Ir.LT, Ir.GT, Ir.LE, Ir.GE, Ir.NE -> {
                    MValue b = vals[--sp]; if (b == null) b = MValue.ofNumber(dvals[sp]); if (b == null) b = MValue.ofNumber(dvals[sp]);
                    MValue a = vals[--sp]; if (a == null) a = MValue.ofNumber(dvals[sp]); if (a == null) a = MValue.ofNumber(dvals[sp]);
                    boolean r = switch (op.op) {
                        case Ir.EQ -> MRuntime.eq(a, b);
                        case Ir.LT -> MRuntime.lt(a, b);
                        case Ir.GT -> MRuntime.lt(b, a);
                        case Ir.LE -> !MRuntime.lt(b, a);
                        case Ir.GE -> !MRuntime.lt(a, b);
                        default -> !MRuntime.eq(a, b);
                    };
                    dvals[sp] = r ? 1 : 0;
                    vals[sp++] = null;
                }
                case Ir.WRITE -> {
                    sp--; MValue v = vals[sp] != null ? vals[sp] : MValue.ofNumber(dvals[sp]);
                    if (op.iarg == 1) {
                        // argument indirection: write comma-separated exprs
                        String src = v.asStr();
                        for (String part : H.splitTop(src)) {
                            rt.write(toText(rt.evalExpr(part)));
                        }
                    } else {
                        rt.write(toText(v));
                    }
                }
                case Ir.WRITE_NL -> rt.writeNl();
                case Ir.END_WRITE -> rt.endWrite();
                case Ir.XECUTE -> {
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    var p2 = new mjvm.compiler.MFrontend().compileLine(v.asStr());
                    run(p2, rt);
                }
                case Ir.WRITE_CHAR -> {
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    rt.writeChar((int) v.asNum());
                }
                case Ir.WRITE_COL -> {
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    rt.writeCol((int) v.asNum());
                }
                case Ir.JMP -> pc = op.iarg;
                case Ir.JMP0 -> {
                    sp--; MValue v = vals[sp] != null ? vals[sp] : MValue.ofNumber(dvals[sp]);
                    if (!MRuntime.truthy(v)) {
                        pc = op.iarg;
                    } else {
                        pc++;
                    }
                }
                case Ir.CALL -> {
                    int argc = op.iarg;
                    MValue[] argsBuf = ARGS_POOL.get();
                    for (int k = argc - 1; k >= 0; k--) { MValue av = vals[--sp]; argsBuf[k] = av != null ? av : MValue.ofNumber(dvals[sp]); }
                    // Trim to actual argc — pool buffer may be larger
                    MValue[] actualArgs = (argc == argsBuf.length) ? argsBuf : java.util.Arrays.copyOf(argsBuf, argc);
                    if (op.name != null && op.name.startsWith("$")) {
                        vals[sp++] = rt.callExtrinsic(op.name.substring(1), actualArgs);
                    } else {
                        rt.call(op.name, actualArgs);
                    }
                }
                case Ir.OPEN -> {
                    // stack: path, device#  (device# pushed first)
                    MValue path = vals[--sp]; if (path == null) path = MValue.ofNumber(dvals[sp]);
                    MValue num = vals[--sp]; if (num == null) num = MValue.ofNumber(dvals[sp]);
                    rt.openDevice((int) num.asNum(), path.asStr());
                }
                case Ir.CLOSE -> {
                    MValue num = vals[--sp]; if (num == null) num = MValue.ofNumber(dvals[sp]);
                    rt.closeDevice((int) num.asNum());
                }
                case Ir.USE -> {
                    MValue num = vals[--sp]; if (num == null) num = MValue.ofNumber(dvals[sp]);
                    rt.useDevice((int) num.asNum());
                }
                case Ir.READ -> {
                    rt.setVar(op.name == null ? "R" : op.name,
                            MValue.ofString(rt.readDevice()));
                }
                case Ir.JOB -> {
                    MValue code = vals[--sp]; if (code == null) code = MValue.ofNumber(dvals[sp]);
                    rt.job(code.asStr());
                }
                case Ir.LOCK -> {
                    MValue key = vals[--sp]; if (key == null) key = MValue.ofNumber(dvals[sp]);
                    if (op.iarg > 0) rt.lockAcquire(key.asStr(), 50);
                    else rt.lockRelease(key.asStr());
                }
                case Ir.TSTART -> rt.tstart();
                case Ir.TCOMMIT -> rt.tcommit();
                case Ir.TROLLBACK -> rt.trollback();
                case Ir.NEW -> rt.newVar(op.name);
                case Ir.KILL -> {
                    if ("@".equals(op.name)) {
                        MValue tv = vals[--sp]; if (tv == null) tv = MValue.ofNumber(dvals[sp]);
                        rt.killVar(tv.asStr());
                    } else {
                        rt.killVar(op.name);
                    }
                }
                case Ir.MERGE -> rt.mergeVar(op.name, op.ival);
                case Ir.EVAL_EXPR -> vals[sp++] = rt.evalExpr(op.ival);
                case Ir.SET_EXTRACT -> {
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    String[] ft = op.ival.split(",");
                    rt.setExtract(op.name, Integer.parseInt(ft[0]), Integer.parseInt(ft[1]), v.asStr());
                }
                case Ir.SET_PIECE -> {
                    MValue v = vals[--sp]; if (v == null) v = MValue.ofNumber(dvals[sp]);
                    String[] parts = op.ival.split("\u0001", -1);
                    rt.setPiece(op.name, parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), v.asStr());
                }
                case Ir.CALL_PC -> {
                    rets[rsp++] = pc + 1;
                    pc = op.iarg;
                    continue;
                }
                case Ir.RET_TO -> {
                    if (rsp == 0) return extrinsic ? rt.takeExtrinsicValue() : null;
                    pc = rets[--rsp];
                    continue;
                }
                case Ir.RET_VALUE -> {
                    // Q expr — the value becomes the extrinsic return value
                    if (sp > 0) { MValue ev = vals[--sp]; rt.setExtrinsicValue(ev != null ? ev : MValue.ofNumber(dvals[sp])); }
                    if (rsp > 0) {
                        pc = rets[--rsp];
                        continue;
                    }
                    return extrinsic ? rt.takeExtrinsicValue() : null;
                }
                case Ir.RET -> {
                    if (rsp > 0) {
                        pc = rets[--rsp];
                        continue;
                    }
                    return extrinsic ? rt.takeExtrinsicValue() : null;
                }
                default -> throw new jvmumps.MError("bad IR op " + op.op);
            }
            if (op.op != Ir.JMP && op.op != Ir.JMP0) pc++;
        }
        // fell off the end of the program
        return extrinsic ? rt.takeExtrinsicValue() : null;
    }

    private static String toText(MValue v) {
        if (v == null) return "";
        if (v.isnum) return jvmumps.ExprEval.mstr(v.asNum());
        return v.asStr();
    }
}
