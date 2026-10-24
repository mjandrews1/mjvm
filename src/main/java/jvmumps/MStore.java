package jvmumps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local + global trees for J2.
 * Keys are name + subscript strings; empty value means node with no data
 * but possibly children ($DATA 10).
 */
/**
 * In-memory {@link StoreApi} (direct-mode default; no persistence).
 *
 * <p><b>Not the M production model.</b> RSM (the oracle) is always
 * <b>file-backed</b> ({@code RSM_DBFILE} / {@code rsm.dat} with global
 * buffers and database blocks). {@link MStore} is a JavaM/mJVM test and
 * direct-mode backend only; for oracle-aligned persistence use
 * {@link SqlStore} (integration) or live RSM.
 */
public final class MStore implements StoreApi {
    public static final class Node {
        MValue value = MValue.EMPTY;
        boolean hasValue = false;
        final Map<String, Node> kids = new ConcurrentHashMap<>();

        Node kid(String s) {
            return kids.computeIfAbsent(s, k -> new Node());
        }
    }

    private final Map<String, Node> locals = new ConcurrentHashMap<>();
    private final Map<String, Node> globals = new ConcurrentHashMap<>();
    private final AtomicLong ops = new AtomicLong(0);

    // === Key interning: String key -> cached path segments (avoids splitKey alloc) ===
    private final ConcurrentHashMap<String, String[]> keyCache = new ConcurrentHashMap<>();

    // === Primitive-key cache: striped CAS-based open-addressing ===
    // Each stripe is a flat AtomicLongArray pair with linear probing.
    // Lock-free reads AND writes via CAS (no synchronized).
    // Key = pack(name.hashCode(), subscript) -> value (double bits).
    private static final int PRIM_BITS = 16; // 64K entries per stripe
    private static final int PRIM_SIZE = 1 << PRIM_BITS;
    private static final int PRIM_MASK = PRIM_SIZE - 1;
    private static final int PRIM_STRIPES = 16;
    private static final long PRIM_EMPTY = Long.MIN_VALUE + 1; // key sentinel
    private static final long PRIM_NAN_BITS = Double.doubleToRawLongBits(Double.NaN); // value sentinel

    // Per-stripe data: AtomicLongArray for CAS operations
    private final java.util.concurrent.atomic.AtomicLongArray[] primKeys = new java.util.concurrent.atomic.AtomicLongArray[PRIM_STRIPES];
    private final java.util.concurrent.atomic.AtomicLongArray[] primVals = new java.util.concurrent.atomic.AtomicLongArray[PRIM_STRIPES];
    {
        for (int s = 0; s < PRIM_STRIPES; s++) {
            primKeys[s] = new java.util.concurrent.atomic.AtomicLongArray(PRIM_SIZE);
            primVals[s] = new java.util.concurrent.atomic.AtomicLongArray(PRIM_SIZE);
            for (int i = 0; i < PRIM_SIZE; i++) {
                primKeys[s].set(i, PRIM_EMPTY);
                primVals[s].set(i, PRIM_NAN_BITS);
            }
        }
    }

    /** Precomputed name hash cache: String name -> mixed hash. */
    private static final ConcurrentHashMap<String, Long> nameHashCache = new ConcurrentHashMap<>();

    /** Get precomputed mixed hash for a name (cached). */
    private static long nameHash(String name) {
        Long cached = nameHashCache.get(name);
        if (cached != null) return cached;
        long h = name.hashCode() * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h = h == PRIM_EMPTY ? PRIM_EMPTY + 1 : h;
        nameHashCache.put(name, h);
        return h;
    }

    /** Pack name hash + int subscript into a cache key (uses precomputed name hash). */
    private static long primKey(String name, int sub) {
        long h = nameHash(name) + sub;
        h ^= (h >>> 33);
        return h == PRIM_EMPTY ? PRIM_EMPTY + 1 : h;
    }

    /** Pack precomputed name hash + int subscript (ultra-fast path). */
    private static long primKeyFast(long nameH, int sub) {
        long h = nameH + sub;
        h ^= (h >>> 33);
        return h == PRIM_EMPTY ? PRIM_EMPTY + 1 : h;
    }

    /** Get precomputed name hash for use with setNumAtFast/getNumAtFast. */
    public long nameHashFor(String name) {
        return nameHash(name);
    }

    /** Get stripe index from key. */
    private static int primStripe(long key) {
        return (int)((key >>> 48) & (PRIM_STRIPES - 1));
    }

    /** Ultra-fast numeric set with precomputed name hash (CAS, lock-free).
     *  Single volatile read per probe step, CAS only on insert. */
    public void setNumAtFast(long nameH, int sub, double val) {
        long key = primKeyFast(nameH, sub);
        long valBits = Double.doubleToRawLongBits(val);
        int stripe = primStripe(key);
        java.util.concurrent.atomic.AtomicLongArray keys = primKeys[stripe];
        java.util.concurrent.atomic.AtomicLongArray vals = primVals[stripe];
        int idx = (int)(key & PRIM_MASK);
        for (;;) {
            long existing = keys.getOpaque(idx); // relaxed read — faster on ARM
            if (existing == key) {
                vals.setRelease(idx, valBits); // release store — pair with getOpaque
                return;
            }
            if (existing == PRIM_EMPTY) {
                if (keys.compareAndSet(idx, PRIM_EMPTY, key)) {
                    vals.setRelease(idx, valBits);
                    return;
                }
                // CAS failed — retry from same slot
                continue;
            }
            idx = (idx + 1) & PRIM_MASK;
        }
    }

    /** Ultra-fast numeric get with precomputed name hash (lock-free read).
     *  Single volatile read per probe step, relaxed ordering. */
    public double getNumAtFast(long nameH, int sub) {
        long key = primKeyFast(nameH, sub);
        int stripe = primStripe(key);
        java.util.concurrent.atomic.AtomicLongArray keys = primKeys[stripe];
        java.util.concurrent.atomic.AtomicLongArray vals = primVals[stripe];
        int idx = (int)(key & PRIM_MASK);
        for (;;) {
            long existing = keys.getOpaque(idx); // relaxed read
            if (existing == key) {
                long valBits = vals.getAcquire(idx); // acquire — pairs with setRelease
                if (valBits == PRIM_NAN_BITS) {
                    Thread.onSpinWait();
                    continue;
                }
                return Double.longBitsToDouble(valBits);
            }
            if (existing == PRIM_EMPTY) {
                return Double.NaN;
            }
            idx = (idx + 1) & PRIM_MASK;
        }
    }

    /** Ultra-fast numeric set with primitive subscript (CAS, lock-free). */
    public void setNumAt(String name, int sub, double val) {
        setNumAtFast(nameHash(name), sub, val);
    }

    /** Ultra-fast numeric get with primitive subscript (lock-free read). */
    public double getNumAt(String name, int sub) {
        return getNumAtFast(nameHash(name), sub);
    }

    /** Clear all primitive cache entries. */
    private void primClear() {
        for (int s = 0; s < PRIM_STRIPES; s++) {
            for (int i = 0; i < PRIM_SIZE; i++) {
                primKeys[s].set(i, PRIM_EMPTY);
                primVals[s].set(i, PRIM_NAN_BITS);
            }
        }
    }

    // === Open-addressing cache for String keys (synchronized for thread safety) ===
    // Flat arrays: no boxing, linear probing. Synchronized on write/read.
    private static final int OA_INIT_BITS = 16; // 64K slots
    private int oaBits = OA_INIT_BITS;
    private int oaSize = 1 << oaBits;
    private int oaMask = oaSize - 1;
    private int oaCount = 0;
    private String[] oaKeys = new String[oaSize];
    private double[] oaVals = new double[oaSize];
    private static final double OA_LOAD = 0.6;

    /** Open-addressing put: linear probing. Thread-safe via synchronized. */
    private synchronized void oaPut(String key, double val) {
        if (oaCount > oaSize * OA_LOAD) oaResize();
        int idx = (int)(mixHash(key) & oaMask);
        while (oaKeys[idx] != null) {
            if (oaKeys[idx].equals(key)) { oaVals[idx] = val; return; }
            idx = (idx + 1) & oaMask;
        }
        oaKeys[idx] = key;
        oaVals[idx] = val;
        oaCount++;
    }

    /** Open-addressing get: returns NaN if not found. Thread-safe via synchronized. */
    private synchronized double oaGet(String key) {
        int idx = (int)(mixHash(key) & oaMask);
        while (oaKeys[idx] != null) {
            if (oaKeys[idx].equals(key)) return oaVals[idx];
            idx = (idx + 1) & oaMask;
        }
        return Double.NaN;
    }

    /** Open-addressing remove. Thread-safe via synchronized. */
    private synchronized void oaRemove(String key) {
        int idx = (int)(mixHash(key) & oaMask);
        while (oaKeys[idx] != null) {
            if (oaKeys[idx].equals(key)) {
                // Tombstone: shift remaining cluster
                oaKeys[idx] = null;
                oaCount--;
                // Re-insert cluster that may have been displaced
                int next = (idx + 1) & oaMask;
                while (oaKeys[next] != null) {
                    String k = oaKeys[next];
                    double v = oaVals[next];
                    oaKeys[next] = null;
                    oaCount--;
                    oaPut(k, v);
                    next = (next + 1) & oaMask;
                }
                return;
            }
            idx = (idx + 1) & oaMask;
        }
    }

    /** Clear all cache entries. Thread-safe via synchronized. */
    private synchronized void oaClear() {
        java.util.Arrays.fill(oaKeys, null);
        oaCount = 0;
    }

    /** Resize to 2x capacity. */
    private void oaResize() {
        String[] oldKeys = oaKeys;
        double[] oldVals = oaVals;
        int oldSize = oaSize;
        oaBits++;
        oaSize = 1 << oaBits;
        oaMask = oaSize - 1;
        oaKeys = new String[oaSize];
        oaVals = new double[oaSize];
        oaCount = 0;
        for (int i = 0; i < oldSize; i++) {
            if (oldKeys[i] != null) oaPut(oldKeys[i], oldVals[i]);
        }
    }

    /** Mix hash for open addressing (Murmur-style finalizer). */
    private static long mixHash(String key) {
        long h = 0;
        for (int i = 0; i < key.length(); i++) {
            h = h * 0x9E3779B97F4A7C15L + key.charAt(i);
        }
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return h;
    }
    /** Displaced local nodes for NEW restore. */
    private final List<Map<String, Node>> scopeStack = new ArrayList<>();

    /** NEW (names==null: hide all locals). Displaced nodes are restored on pop. */
    public void newScope(List<String> names) {
        Map<String, Node> displaced = new ConcurrentHashMap<>();
        if (names == null) {
            displaced.putAll(locals);
            locals.clear();
        } else {
            for (String n : names) {
                Node old = locals.remove(n);
                if (old != null) {
                    displaced.put(n, old);
                }
            }
        }
        scopeStack.add(displaced);
    }

    public void restoreScope() {
        if (scopeStack.isEmpty()) {
            return;
        }
        locals.putAll(scopeStack.remove(scopeStack.size() - 1));
    }

    private Map<String, Node> root(boolean global) {
        return global ? globals : locals;
    }

    public Node node(List<String> path, boolean global) {
        String name = path.get(0);
        Node n = root(global).computeIfAbsent(name, k -> new Node());
        for (int i = 1; i < path.size(); i++) {
            n = n.kid(path.get(i));
        }
        return n;
    }

    public long storeOps() {
        return ops.get();
    }

    public MValue get(List<String> path, boolean global) {
        if (global) ops.incrementAndGet();
        Node n = find(path, global);
        return n != null && n.hasValue ? n.value : MValue.EMPTY;
    }

    public void set(List<String> path, boolean global, MValue v) {
        if (global) ops.incrementAndGet();
        Node n = node(path, global);
        n.value = v;
        n.hasValue = true;
    }

    // --- String key fast path with key interning + primitive cache ---

    /** Interned key split — returns cached path segments (avoids ArrayList alloc). */
    private String[] splitCached(String key) {
        String[] cached = keyCache.get(key);
        if (cached != null) return cached;
        // Split into path segments
        int count = 1;
        for (int i = 0; i < key.length(); i++) if (key.charAt(i) == 1) count++;
        String[] parts = new String[count];
        int idx = 0, start = 0;
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) == 1) {
                parts[idx++] = key.substring(start, i);
                start = i + 1;
            }
        }
        parts[idx] = key.substring(start);
        keyCache.put(key, parts);
        return parts;
    }

    /** Convert interned path segments to List for tree navigation. */
    private static java.util.List<String> toList(String[] parts) {
        return java.util.Arrays.asList(parts);
    }

    @Override
    public MValue get(String key, boolean global) {
        if (global) {
            double v = oaGet(key);
            if (!Double.isNaN(v)) return MValue.ofNumber(v);
        }
        return get(toList(splitCached(key)), global);
    }

    /** Ultra-fast get with primitive subscript: primCache first, then tree. */
    public double getNumAtFast(String name, int sub, boolean global) {
        double v = getNumAt(name, sub);
        if (!Double.isNaN(v)) return v;
        // Fallback to tree via string key
        String key = name + '\u0001' + sub;
        return get(toList(splitCached(key)), global).asNum();
    }

    /** Ultra-fast set with primitive subscript: primCache + tree. */
    public void setNumAtFull(String name, int sub, double val, boolean global) {
        setNumAt(name, sub, val);
        String key = name + '\u0001' + sub;
        set(toList(splitCached(key)), global, MValue.ofNumber(val));
    }

    @Override
    public void set(String key, boolean global, MValue v) {
        if (global && v.isnum) {
            oaPut(key, v.num);
        }
        set(toList(splitCached(key)), global, v);
    }

    /** Ultra-fast numeric set: cache-only, skips tree write + MValue boxing.
     *  Use with getNumFast for read-back. For tree visibility use set() instead. */
    public void setNum(String key, boolean global, double val) {
        if (global) {
            oaPut(key, val);
        }
    }

    /** Sync cached values to tree (for correctness before ORDER/QUERY). */
    public void syncCache(boolean global) {
        // No-op: cache is write-through for set(), write-back for setNum()
        // This method exists for API compatibility
    }

    /** Ultra-fast numeric get: returns double, skips MValue boxing. */
    public double getNumFast(String key, boolean global) {
        if (global) {
            double v = oaGet(key);
            if (!Double.isNaN(v)) return v;
        }
        return get(toList(splitCached(key)), global).asNum();
    }

    @Override
    public void kill(String key, boolean global) {
        kill(StoreCore.splitKey(key), global);
    }

    @Override
    public MValue increment(String key, boolean global, double amount) {
        return increment(StoreCore.splitKey(key), global, amount);
    }

    /** Atomic increment: single node update, counts as 1 store op. */
    public MValue increment(List<String> path, boolean global, double amount) {
        if (global) ops.incrementAndGet();
        Node n = node(path, global);
        double cur = n.hasValue ? n.value.asNum() : 0;
        double next = cur + amount;
        MValue nv = MValue.ofNumber(next);
        n.value = nv;
        n.hasValue = true;
        return nv;
    }

    public void kill(List<String> path, boolean global) {
        if (global) {
            ops.incrementAndGet();
            // Clear caches (kills can remove subtrees)
            oaClear();
            primClear();
        }
        if (path.size() == 1) {
            root(global).remove(path.get(0));
            return;
        }
        Node parent = find(path.subList(0, path.size() - 1), global);
        if (parent != null) {
            parent.kids.remove(path.get(path.size() - 1));
        }
    }

    public Node find(List<String> path, boolean global) {
        Node n = root(global).get(path.get(0));
        for (int i = 1; i < path.size() && n != null; i++) {
            n = n.kids.get(path.get(i));
        }
        return n;
    }

    /** $DATA: 0/1/10/11 */
    public int data(List<String> path, boolean global) {
        Node n = find(path, global);
        if (n == null) {
            return 0;
        }
        boolean hasVal = n.hasValue;
        boolean hasDesc = !n.kids.isEmpty();
        if (hasVal && hasDesc) {
            return 11;
        }
        if (hasVal) {
            return 1;
        }
        return hasDesc ? 10 : 0;
    }

    /** $ORDER next sibling key at path[0..n-2] after last subscript. */
    public String order(List<String> path, boolean global, double dir) {
        if (path.size() < 2) {
            return "";
        }
        Node parent = find(path.subList(0, path.size() - 1), global);
        if (parent == null) {
            return "";
        }
        String cur = path.get(path.size() - 1);
        String best = null;
        for (Map.Entry<String, Node> e : parent.kids.entrySet()) {
            String k = e.getKey();
            // $ORDER(A("")) / $ORDER(A(""),-1) — empty cursor starts the walk
            if (cur.isEmpty()) {
                if (best == null || (dir > 0 ? collate(k, best) < 0 : collate(k, best) > 0)) {
                    best = k;
                }
                continue;
            }
            int c = collate(k, cur);
            if (dir > 0 && c <= 0) {
                continue;
            }
            if (dir <= 0 && c >= 0) {
                continue;
            }
            if (best == null || (dir > 0 ? collate(k, best) < 0 : collate(k, best) > 0)) {
                best = k;
            }
        }
        return best == null ? "" : best;
    }

    static int collate(String a, String b) {
        // simplified: numeric vs string — numeric first
        boolean an = ExprEval.numericStr(a);
        boolean bn = ExprEval.numericStr(b);
        if (an && bn) {
            return Double.compare(ExprEval.toNum(a), ExprEval.toNum(b));
        }
        if (an != bn) {
            return an ? -1 : 1;
        }
        return a.compareTo(b);
    }

    public void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal) {
        Node s = find(src, sGlobal);
        if (s == null) {
            return;
        }
        Node d = node(dst, dGlobal);
        copy(s, d);
    }

    private void copy(Node s, Node d) {
        if (s.hasValue) {
            d.value = s.value;
            d.hasValue = true;
        }
        for (Map.Entry<String, Node> e : s.kids.entrySet()) {
            copy(e.getValue(), d.kid(e.getKey()));
        }
    }

    /** $QUERY: next defined node in preorder tree walk from a closed ref path. */
    public String queryNext(List<String> path, boolean global, int dir) {
        if (path == null || path.isEmpty() || path.get(0).isEmpty()) {
            return queryAnyStart(dir);
        }
        String name = path.get(0);
        Node root = global ? globals.get(name) : locals.get(name);
        if (root == null) {
            // $Q(name) with no children yet — next after empty is still empty
            return "";
        }
        List<String[]> found = new java.util.ArrayList<>();
        walk(name, new String[0], root, found, dir);
        String[] cur = path.subList(1, path.size()).toArray(new String[0]);
        int at = -1;
        for (int i = 0; i < found.size(); i++) {
            if (java.util.Arrays.equals(found.get(i), cur)) {
                at = i;
                break;
            }
        }
        int nxt = dir > 0 ? at + 1 : at - 1;
        if (at < 0) {
            nxt = dir > 0 ? 0 : found.size() - 1;
        }
        if (nxt < 0 || nxt >= found.size()) {
            return "";
        }
        return fmtClosed(name, found.get(nxt));
    }

    /** $Q("") — first/last defined node across globals then locals. */
    public String queryAnyStart(int dir) {
        for (Map.Entry<String, Node> e : globals.entrySet()) {
            List<String[]> found = new java.util.ArrayList<>();
            walk(e.getKey(), new String[0], e.getValue(), found, dir);
            if (!found.isEmpty()) {
                return fmtClosed(e.getKey(), found.get(dir > 0 ? 0 : found.size() - 1));
            }
        }
        for (Map.Entry<String, Node> e : locals.entrySet()) {
            List<String[]> found = new java.util.ArrayList<>();
            walk(e.getKey(), new String[0], e.getValue(), found, dir);
            if (!found.isEmpty()) {
                return fmtClosed(e.getKey(), found.get(dir > 0 ? 0 : found.size() - 1));
            }
        }
        return "";
    }

    static String fmtClosed(String name, String[] subs) {
        StringBuilder sb = new StringBuilder(name);
        if (subs.length > 0) {
            sb.append('(');
            for (int i = 0; i < subs.length; i++) {
                if (i > 0) sb.append(',');
                if (numeric(subs[i])) sb.append(subs[i]);
                else sb.append('"').append(subs[i]).append('"');
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public static boolean numeric(String s) {
        return ExprEval.numericStr(s);
    }

    void walk(String name, String[] pref, Node n, List<String[]> out, int dir) {
        if (n.hasValue) {
            out.add(pref.clone());
        }
        // children always in normal collate order; dir only steps the index
        List<String> keys = new java.util.ArrayList<>(n.kids.keySet());
        java.util.Collections.sort(keys, (a, b) -> collate(a, b));
        for (String k : keys) {
            String[] np = new String[pref.length + 1];
            System.arraycopy(pref, 0, np, 0, pref.length);
            np[pref.length] = k;
            walk(name, np, n.kids.get(k), out, dir);
        }
    }


    /** Flat rows for SQLite: [name, subsJson, val, isnum]. */
    public java.util.List<String[]> dump() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (var e : locals.entrySet()) {
            dumpWalk(e.getKey(), new String[0], e.getValue(), rows);
        }
        for (var e : globals.entrySet()) {
            dumpWalk(e.getKey(), new String[0], e.getValue(), rows);
        }
        return rows;
    }

    void dumpWalk(String name, String[] pref, Node n, java.util.List<String[]> rows) {
        if (n.hasValue) {
            rows.add(new String[] {
                name,
                subsJson(pref),
                n.value.isnum ? mstrSafe(n.value.num) : n.value.str,
                n.value.isnum ? "1" : "0"
            });
        }
        for (var e : n.kids.entrySet()) {
            String[] np = new String[pref.length + 1];
            System.arraycopy(pref, 0, np, 0, pref.length);
            np[pref.length] = e.getKey();
            dumpWalk(name, np, e.getValue(), rows);
        }
    }

    static String mstrSafe(double d) {
        return ExprEval.mstr(d);
    }

    public static String subsJson(String[] subs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < subs.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(subs[i].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    public static String[] parseSubs(String json) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < json.length() && json.charAt(i) != '[') i++;
        i++;
        while (i < json.length() && json.charAt(i) != ']') {
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ',')) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) != '"') break;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    sb.append(json.charAt(i + 1));
                    i += 2;
                } else {
                    sb.append(json.charAt(i++));
                }
            }
            if (i < json.length() && json.charAt(i) == '"') i++;
            out.add(sb.toString());
        }
        return out.toArray(new String[0]);
    }

    /** Restore a row from SQLite. */
    public void putRow(String name, String subsJson, String val, int isnum) {
        java.util.List<String> path = new java.util.ArrayList<>();
        path.add(name);
        for (String s : parseSubs(subsJson)) path.add(s);
        boolean glob = name.startsWith("^");
        set(path, glob, isnum == 1
                ? MValue.ofNumber(ExprEval.toNum(val))
                : MValue.ofString(val));
    }
}
