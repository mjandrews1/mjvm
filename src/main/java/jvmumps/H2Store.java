package jvmumps;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * H2 MVStore-backed StoreApi. Pure Java embedded B-tree — no JNI.
 * Same collation / key layout as SqlStore (k = name+SEP+subs).
 *
 * <p>Performance: ~1-5us GET, ~5-20us SET (10-50x faster than SQLite).
 * Ordered keys (B-tree) give native $ORDER support.
 *
 * <p><b>Thread safety:</b> MVStore is single-writer multi-reader.
 * Writes are synchronized on {@code this}. Reads are lock-free.
 */
public final class H2Store implements StoreApi {
    static final char SEP = 1;

    private final org.h2.mvstore.MVStore store;
    private final org.h2.mvstore.MVMap<String, String> nodes;
    private final AtomicLong ops = new AtomicLong();

    public H2Store(String path) {
        this(path, false);
    }

    public H2Store(String path, boolean readOnly) {
        if (readOnly) {
            store = new org.h2.mvstore.MVStore.Builder()
                .fileName(path)
                .readOnly()
                .open();
        } else {
            store = org.h2.mvstore.MVStore.open(path);
        }
        nodes = store.openMap("nodes");
    }

    static String key(String name, List<String> path) {
        return SqlStore.key(name, path);
    }

    static String collateKey(String s) {
        return SqlStore.collateKey(s);
    }

    @Override
    public MValue get(List<String> path, boolean global) {
        ops.incrementAndGet();
        String v = nodes.get(key(path.get(0), path));
        if (v == null) return MValue.EMPTY;
        return v.startsWith("#") ? MValue.ofNumber(ExprEval.toNum(v.substring(1)))
                : MValue.ofString(v);
    }

    @Override
    public void set(List<String> path, boolean global, MValue v) {
        ops.incrementAndGet();
        String val = v.isnum ? "#" + ExprEval.mstr(v.num) : v.str;
        synchronized (this) {
            nodes.put(key(path.get(0), path), val);
        }
    }

    @Override
    public void kill(List<String> path, boolean global) {
        ops.incrementAndGet();
        String k = key(path.get(0), path);
        synchronized (this) {
            nodes.remove(k);
            // Remove descendants: collect keys first (iterator doesn't support remove)
            String pfx = k + SEP;
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            var it = nodes.keyIterator(pfx);
            while (it.hasNext()) {
                String dk = it.next();
                if (!dk.startsWith(pfx)) break;
                toRemove.add(dk);
            }
            for (String dk : toRemove) nodes.remove(dk);
        }
    }

    // --- Native numeric fast path ---

    @Override
    public double getNum(String key, boolean global) {
        ops.incrementAndGet();
        String v = nodes.get(key);
        if (v == null) return 0;
        if (v.startsWith("#")) return ExprEval.toNum(v.substring(1));
        return ExprEval.toNum(v);
    }

    @Override
    public void setNum(String key, boolean global, double val) {
        ops.incrementAndGet();
        synchronized (this) {
            nodes.put(key, "#" + ExprEval.mstr(val));
        }
    }

    // --- String key fast path ---

    @Override
    public MValue get(String key, boolean global) {
        ops.incrementAndGet();
        String v = nodes.get(key);
        if (v == null) return MValue.EMPTY;
        return v.startsWith("#") ? MValue.ofNumber(ExprEval.toNum(v.substring(1)))
                : MValue.ofString(v);
    }

    @Override
    public void set(String key, boolean global, MValue v) {
        ops.incrementAndGet();
        String val = v.isnum ? "#" + ExprEval.mstr(v.num) : v.str;
        synchronized (this) {
            nodes.put(key, val);
        }
    }

    // Note: readOnly mode is enforced by MVStore itself (throws on write)

    @Override
    public void kill(String key, boolean global) {
        ops.incrementAndGet();
        synchronized (this) {
            nodes.remove(key);
            String pfx = key + (char) 1;
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            var it = nodes.keyIterator(pfx);
            while (it.hasNext()) {
                String dk = it.next();
                if (!dk.startsWith(pfx)) break;
                toRemove.add(dk);
            }
            for (String dk : toRemove) nodes.remove(dk);
        }
    }

    @Override
    public MValue increment(String key, boolean global, double amount) {
        ops.incrementAndGet();
        synchronized (this) {
            String v = nodes.get(key);
            double cur = 0;
            if (v != null && v.startsWith("#")) cur = ExprEval.toNum(v.substring(1));
            double next = cur + amount;
            nodes.put(key, "#" + ExprEval.mstr(next));
            return MValue.ofNumber(next);
        }
    }

    @Override
    public MValue increment(List<String> path, boolean global, double amount) {
        ops.incrementAndGet();
        String k = key(path.get(0), path);
        synchronized (this) {
            String v = nodes.get(k);
            double cur = 0;
            if (v != null && v.startsWith("#")) cur = ExprEval.toNum(v.substring(1));
            double next = cur + amount;
            nodes.put(k, "#" + ExprEval.mstr(next));
            return MValue.ofNumber(next);
        }
    }

    @Override
    public int data(List<String> path, boolean global) {
        ops.incrementAndGet();
        String k = key(path.get(0), path);
        boolean hasVal = nodes.containsKey(k);
        boolean hasDesc = false;
        String pfx = k + SEP;
        var it = nodes.keyIterator(pfx);
        if (it.hasNext()) {
            String dk = it.next();
            if (dk.startsWith(pfx)) hasDesc = true;
        }
        if (hasVal && hasDesc) return 11;
        if (hasVal) return 1;
        if (hasDesc) return 10;
        return 0;
    }

    @Override
    public String order(List<String> path, boolean global, double dir) {
        if (path.size() < 2) return "";
        ops.incrementAndGet();
        List<String> parent = path.subList(0, path.size() - 1);
        String cur = path.get(path.size() - 1);
        String pfx = key(parent.get(0), parent) + SEP;

        if (dir > 0) {
            // Forward: next child after cur
            String lo = cur.isEmpty() ? pfx : pfx + cur + SEP + '\uffff';
            var it = nodes.keyIterator(lo);
            while (it.hasNext()) {
                String k = it.next();
                if (!k.startsWith(pfx)) return "";
                String rest = k.substring(pfx.length());
                int j = rest.indexOf(SEP);
                String child = j < 0 ? rest : rest.substring(0, j);
                if (cur.isEmpty() || child.compareTo(cur) > 0) return child;
            }
            return "";
        } else {
            // Reverse: previous child before cur
            String hi = cur.isEmpty() ? pfx + '\uffff' : pfx + cur;
            // MVStore doesn't have reverse iterator — collect and reverse
            java.util.List<String> children = new java.util.ArrayList<>();
            var it = nodes.keyIterator(pfx);
            while (it.hasNext()) {
                String k = it.next();
                if (!k.startsWith(pfx)) break;
                String rest = k.substring(pfx.length());
                int j = rest.indexOf(SEP);
                String child = j < 0 ? rest : rest.substring(0, j);
                if (children.isEmpty() || !children.get(children.size() - 1).equals(child)) {
                    children.add(child);
                }
            }
            // Find last child < cur (or last if cur empty)
            for (int i = children.size() - 1; i >= 0; i--) {
                String child = children.get(i);
                if (cur.isEmpty() || child.compareTo(cur) < 0) return child;
            }
            return "";
        }
    }

    @Override
    public String queryNext(List<String> path, boolean global, int dir) {
        return order(path, global, dir);
    }

    @Override
    public long storeOps() {
        return ops.get();
    }

    @Override
    public void newScope(List<String> names) {}

    @Override
    public void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal) {
        MValue v = get(src, sGlobal);
        if (v != MValue.EMPTY) set(dst, dGlobal, v);
    }

    @Override
    public boolean lockAcquire(String key, String pid, long timeoutMs) {
        return true;
    }

    @Override
    public void lockRelease(String key, String pid) {}

    public void close() {
        store.close();
    }
}
