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

    // --- String key fast path (delegates to tree navigation) ---

    @Override
    public MValue get(String key, boolean global) {
        return get(StoreCore.splitKey(key), global);
    }

    @Override
    public void set(String key, boolean global, MValue v) {
        set(StoreCore.splitKey(key), global, v);
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
        if (global) ops.incrementAndGet();
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
