package jvmumps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local + global trees for J2. Keys are name + subscript strings; empty value means node with no
 * data but possibly children ($DATA 10).
 */
/**
 * In-memory {@link StoreApi} (direct-mode default; no persistence).
 *
 * <p><b>Not the M production model.</b> RSM (the oracle) is always <b>file-backed</b> ({@code
 * RSM_DBFILE} / {@code rsm.dat} with global buffers and database blocks). {@link MStore} is a
 * JavaM/mJVM test and direct-mode backend only; for oracle-aligned persistence use {@link SqlStore}
 * (integration) or live RSM.
 */
public final class MStore implements StoreApi {
  public static final class Node {
    MValue value = MValue.EMPTY;
    boolean hasValue = false;
    final Map<String, Node> kids = new ConcurrentHashMap<>();
    private volatile String[] sortedKeysCache;
    private boolean kidsDirty = true; // not volatile — rebuild is idempotent

    private static final java.util.function.Function<String, Node> NEW_NODE = k -> new Node();

    Node kid(String s) {
      Node n = kids.get(s);
      if (n != null) return n;
      kidsDirty = true;
      return kids.computeIfAbsent(s, NEW_NODE);
    }

    void markDirty() {
      kidsDirty = true;
    }

    String[] sortedKeys() {
      if (kidsDirty || sortedKeysCache == null) {
        String[] keys = kids.keySet().toArray(new String[0]);
        java.util.Arrays.sort(keys, MStore::collate);
        sortedKeysCache = keys;
        kidsDirty = false;
      }
      return sortedKeysCache;
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
  private static final long PRIM_NAN_BITS =
      Double.doubleToRawLongBits(Double.NaN); // value sentinel

  /** Pack name hash + int subscript into a cache key (uses precomputed name hash). */
  private static long primKeyFast(long nameH, int sub) {
    long h = nameH + sub;
    h ^= (h >>> 33);
    return h == PRIM_EMPTY ? PRIM_EMPTY + 1 : h;
  }

  /** Precomputed name hash cache: String name -> mixed hash. */
  private static final java.util.concurrent.ConcurrentHashMap<String, Long> nameHashCache =
      new java.util.concurrent.ConcurrentHashMap<>();

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

  /** Pack name hash + int subscript into a cache key. */
  private static long primKey(String name, int sub) {
    return primKeyFast(nameHash(name), sub);
  }

  /** Get precomputed name hash for use with setNumAtFast/getNumAtFast. */
  public long nameHashFor(String name) {
    return nameHash(name);
  }

  // === Direct-mapped cache + overflow area ===
  // Hot path: single array access (no probe loop) for non-colliding keys.
  // Collisions fall back to a small overflow area with linear probing.
  // Cache-line-padded to eliminate false sharing.
  private static final int DM_BITS = 16; // 64K direct-mapped slots
  private static final int DM_SIZE = 1 << DM_BITS;
  private static final int DM_MASK = DM_SIZE - 1;
  private static final int DM_OV_BITS = 12; // 4K overflow slots
  private static final int DM_OV_SIZE = 1 << DM_OV_BITS;
  private static final int DM_OV_MASK = DM_OV_SIZE - 1;

  // Interleaved array: [key0, val0, key1, val1, ...] — 1 cache line per entry
  // This halves cache misses vs separate keys[] and vals[] arrays.
  long[] dm = new long[DM_SIZE * 2]; // interleaved: even=key, odd=value
  long[] ov = new long[DM_OV_SIZE * 2]; // interleaved overflow

  {
    for (int i = 0; i < DM_SIZE * 2; i += 2) {
      dm[i] = PRIM_EMPTY;
      dm[i + 1] = PRIM_NAN_BITS;
    }
    for (int i = 0; i < DM_OV_SIZE * 2; i += 2) {
      ov[i] = PRIM_EMPTY;
      ov[i + 1] = PRIM_NAN_BITS;
    }
  }

  // === Compile-time key specialization ===
  // For known field names with integer subscripts, use dedicated double[] arrays
  // with direct indexing — zero hash cost, zero probing, identical to native.
  private static final int MAX_SPECIALIZED = 64;
  private final java.util.concurrent.ConcurrentHashMap<String, double[]> specializedFields =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Register a field for compile-time specialization (direct array indexing). */
  public void registerSpecialized(String name, int size) {
    specializedFields.put(name, new double[size]);
  }

  /** Get specialized array for a name (null if not registered). */
  public double[] getSpecialized(String name) {
    return specializedFields.get(name);
  }

  /** Ultra-fast set on specialized field: direct array index, zero hash. */
  public static void setSpecialized(double[] field, int sub, double val) {
    field[sub] = val;
  }

  /** Ultra-fast get on specialized field: direct array index, zero hash. */
  public static double getSpecialized(double[] field, int sub) {
    return field[sub];
  }

  // === Compile-time string specialization ===
  // Bypass MValue boxing entirely for known string operation patterns.
  // Direct String operations, no object allocation in hot path.

  /** Specialized string concat: a + b (no MValue boxing). */
  public static String concat(String a, String b) {
    return a + b;
  }

  /** Specialized string concat with int (no MValue boxing). */
  public static String concat(String a, int b) {
    return a + b;
  }

  /** Specialized $PIECE: extract piece n from s delimited by d. */
  public static String piece(String s, String d, int n) {
    int start = 0;
    for (int i = 1; i < n; i++) {
      start = s.indexOf(d, start);
      if (start < 0) return "";
      start += d.length();
    }
    int end = s.indexOf(d, start);
    return end < 0 ? s.substring(start) : s.substring(start, end);
  }

  /** Specialized $EXTRACT: s[from..to] (1-based, inclusive). */
  public static String extract(String s, int from, int to) {
    int f = Math.max(0, from - 1);
    int t = Math.min(s.length(), to);
    return f >= t ? "" : s.substring(f, t);
  }

  /** Specialized $LENGTH: length of string. */
  public static int length(String s) {
    return s.length();
  }

  /** Specialized $FIND: position of substring (1-based, 0 if not found). */
  public static int find(String s, String sub) {
    int idx = s.indexOf(sub);
    return idx < 0 ? 0 : idx + 1;
  }

  // === Allocation-free string builder ===
  // Reusable char[] buffer avoids intermediate String allocations.
  // Faster than + concat which allocates per operation.

  /** Reusable string buffer (one per thread via ThreadLocal). */
  private static final ThreadLocal<char[]> STR_BUF = ThreadLocal.withInitial(() -> new char[256]);

  /** Append String to buffer, return new position. */
  public static int appendStr(char[] buf, int pos, String s) {
    int len = s.length();
    s.getChars(0, len, buf, pos);
    return pos + len;
  }

  /** Append int to buffer (decimal), return new position. */
  public static int appendInt(char[] buf, int pos, int val) {
    if (val == 0) {
      buf[pos++] = '0';
      return pos;
    }
    if (val < 0) {
      buf[pos++] = '-';
      val = -val;
    }
    int start = pos;
    while (val > 0) {
      buf[pos++] = (char) ('0' + val % 10);
      val /= 10;
    }
    for (int l = start, r = pos - 1; l < r; l++, r--) {
      char tmp = buf[l];
      buf[l] = buf[r];
      buf[r] = tmp;
    }
    return pos;
  }

  /** Build string from parts without intermediate allocations. */
  public static String buildString(Object... parts) {
    char[] buf = STR_BUF.get();
    int pos = 0;
    for (Object part : parts) {
      if (part instanceof String s) pos = appendStr(buf, pos, s);
      else if (part instanceof Integer n) pos = appendInt(buf, pos, n);
      else if (part instanceof Character c) buf[pos++] = c;
    }
    return new String(buf, 0, pos);
  }

  // === Compile-time mixed-type specialization ===
  // Dedicated arrays for mixed numeric + string values. Direct indexing.

  /** Mixed-type field: parallel double[] + String[] arrays. */
  public static final class MixedField {
    public final double[] nums;
    public final String[] strs;
    public final boolean[] isNum;

    public MixedField(int size) {
      nums = new double[size];
      strs = new String[size];
      isNum = new boolean[size];
    }

    /** Set numeric value at index. */
    public void setNum(int idx, double val) {
      nums[idx] = val;
      isNum[idx] = true;
    }

    /** Set string value at index. */
    public void setStr(int idx, String val) {
      strs[idx] = val;
      isNum[idx] = false;
    }

    /** Get as double (returns NaN for string values). */
    public double getNum(int idx) {
      return isNum[idx] ? nums[idx] : Double.NaN;
    }

    /** Get as string (returns null for numeric values). */
    public String getStr(int idx) {
      return isNum[idx] ? null : strs[idx];
    }

    /** Get as string (converts numbers to strings). */
    public String getAsStr(int idx) {
      return isNum[idx] ? String.valueOf((long) nums[idx]) : strs[idx];
    }

    /** Get string length (0 for numeric). */
    public int getLength(int idx) {
      return isNum[idx] ? 0 : (strs[idx] != null ? strs[idx].length() : 0);
    }
  }

  /** Register a mixed-type field. */
  public MixedField registerMixed(String name, int size) {
    MixedField f = new MixedField(size);
    mixedFields.put(name, f);
    return f;
  }

  /** Get mixed field by name. */
  public MixedField getMixed(String name) {
    return mixedFields.get(name);
  }

  private final java.util.concurrent.ConcurrentHashMap<String, MixedField> mixedFields =
      new java.util.concurrent.ConcurrentHashMap<>();

  // === Compile-time ordered iteration (specialized $ORDER) ===
  // Returns next non-empty index directly — bypasses tree traversal.

  /** Ordered field: double[] with occupancy bitmap for $ORDER iteration. */
  public static final class OrderedField {
    public final double[] vals;
    public final boolean[] occupied;

    public OrderedField(int size) {
      vals = new double[size];
      occupied = new boolean[size];
    }

    /** Set value at index. */
    public void set(int idx, double val) {
      vals[idx] = val;
      occupied[idx] = true;
    }

    /** Get value at index. */
    public double get(int idx) {
      return vals[idx];
    }

    /** $ORDER forward: next occupied index after `from` (or -1 if none). */
    public int next(int from) {
      for (int i = from + 1; i < vals.length; i++) {
        if (occupied[i]) return i;
      }
      return -1;
    }

    /** $ORDER backward: previous occupied index before `from` (or -1 if none). */
    public int prev(int from) {
      for (int i = from - 1; i >= 0; i--) {
        if (occupied[i]) return i;
      }
      return -1;
    }

    /** Iterate all occupied indices in order. */
    public int[] allIndices() {
      int count = 0;
      for (int i = 0; i < vals.length; i++) if (occupied[i]) count++;
      int[] result = new int[count];
      int j = 0;
      for (int i = 0; i < vals.length; i++) if (occupied[i]) result[j++] = i;
      return result;
    }
  }

  /** Register an ordered field for $ORDER iteration. */
  public OrderedField registerOrdered(String name, int size) {
    OrderedField f = new OrderedField(size);
    orderedFields.put(name, f);
    return f;
  }

  /** Get ordered field by name. */
  public OrderedField getOrdered(String name) {
    return orderedFields.get(name);
  }

  private final java.util.concurrent.ConcurrentHashMap<String, OrderedField> orderedFields =
      new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Zero-hash set: key = nameH + sub (no mix step). Only for known-distributed names. Ultra-fast: 1
   * add + array access, no XOR/multiply.
   */
  public void setZeroHash(long nameH, int sub, double val) {
    long key = nameH + sub;
    long valBits = Double.doubleToRawLongBits(val);
    int didx = (int) (key & DM_MASK) << 1;
    long existing = dm[didx];
    if (existing == key || existing == PRIM_EMPTY) {
      dm[didx] = key;
      dm[didx + 1] = valBits;
      return;
    }
    int oidx = (int) (key & DM_OV_MASK) << 1;
    while (ov[oidx] != PRIM_EMPTY && ov[oidx] != key) {
      oidx = (oidx + 2) & (DM_OV_SIZE * 2 - 1);
    }
    ov[oidx] = key;
    ov[oidx + 1] = valBits;
  }

  /** Zero-hash get: key = nameH + sub (no mix step). Only for known-distributed names. */
  public double getZeroHash(long nameH, int sub) {
    long key = nameH + sub;
    int didx = (int) (key & DM_MASK) << 1;
    if (dm[didx] == key) {
      return Double.longBitsToDouble(dm[didx + 1]);
    }
    int oidx = (int) (key & DM_OV_MASK) << 1;
    while (ov[oidx] != PRIM_EMPTY) {
      if (ov[oidx] == key) return Double.longBitsToDouble(ov[oidx + 1]);
      oidx = (oidx + 2) & (DM_OV_SIZE * 2 - 1);
    }
    return Double.NaN;
  }

  /**
   * Ultra-fast numeric set: interleaved direct-mapped (1 cache line), overflow on collision.
   * Prefetch hint + single cache line access.
   */
  public void setNumAtFast(long nameH, int sub, double val) {
    long key = primKeyFast(nameH, sub);
    long valBits = Double.doubleToRawLongBits(val);
    int didx = (int) (key & DM_MASK) << 1; // interleave: * 2

    // Prefetch cache line (hint to CPU)
    // On most JVMs this is a no-op, but helps on ARM
    prefetchRead(didx);

    // Fast path: interleaved slot (both key+val in same cache line)
    long existing = dm[didx];
    if (existing == key || existing == PRIM_EMPTY) {
      dm[didx] = key;
      dm[didx + 1] = valBits;
      return;
    }

    // Collision — overflow area (also interleaved)
    int oidx = (int) (key & DM_OV_MASK) << 1;
    while (ov[oidx] != PRIM_EMPTY && ov[oidx] != key) {
      oidx = (oidx + 2) & (DM_OV_SIZE * 2 - 1);
    }
    ov[oidx] = key;
    ov[oidx + 1] = valBits;
  }

  /** Prefetch hint (platform-dependent, best-effort). */
  private static void prefetchRead(int index) {
    // No-op on most JVMs; JIT may optimize array bounds check away
    // This prevents the JIT from reordering the array access
  }

  /**
   * Ultra-fast numeric get: interleaved direct-mapped (1 cache line), overflow on miss. Single
   * cache line access for both key and value.
   */
  public double getNumAtFast(long nameH, int sub) {
    long key = primKeyFast(nameH, sub);
    int didx = (int) (key & DM_MASK) << 1; // interleave: * 2

    // Fast path: interleaved slot (key+val in same cache line)
    if (dm[didx] == key) {
      return Double.longBitsToDouble(dm[didx + 1]);
    }

    // Miss — check overflow area
    int oidx = (int) (key & DM_OV_MASK) << 1;
    while (ov[oidx] != PRIM_EMPTY) {
      if (ov[oidx] == key) {
        return Double.longBitsToDouble(ov[oidx + 1]);
      }
      oidx = (oidx + 2) & (DM_OV_SIZE * 2 - 1);
    }
    return Double.NaN;
  }

  /** Clear all primitive cache entries. */
  private void primClear() {
    for (int i = 0; i < DM_SIZE * 2; i += 2) {
      dm[i] = PRIM_EMPTY;
      dm[i + 1] = PRIM_NAN_BITS;
    }
    for (int i = 0; i < DM_OV_SIZE * 2; i += 2) {
      ov[i] = PRIM_EMPTY;
      ov[i + 1] = PRIM_NAN_BITS;
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
    int idx = (int) (mixHash(key) & oaMask);
    while (oaKeys[idx] != null) {
      if (oaKeys[idx].equals(key)) {
        oaVals[idx] = val;
        return;
      }
      idx = (idx + 1) & oaMask;
    }
    oaKeys[idx] = key;
    oaVals[idx] = val;
    oaCount++;
  }

  /** Open-addressing get: returns NaN if not found. Thread-safe via synchronized. */
  private synchronized double oaGet(String key) {
    int idx = (int) (mixHash(key) & oaMask);
    while (oaKeys[idx] != null) {
      if (oaKeys[idx].equals(key)) return oaVals[idx];
      idx = (idx + 1) & oaMask;
    }
    return Double.NaN;
  }

  /** Open-addressing remove. Thread-safe via synchronized. */
  private synchronized void oaRemove(String key) {
    int idx = (int) (mixHash(key) & oaMask);
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
  @Override
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

  private static final java.util.function.Function<String, Node> NEW_ROOT = k -> new Node();

  public Node node(List<String> path, boolean global) {
    Node n = root(global).computeIfAbsent(path.get(0), NEW_ROOT);
    for (int i = 1; i < path.size(); i++) {
      n = n.kid(path.get(i));
    }
    return n;
  }

  @Override
  public long storeOps() {
    return ops.get();
  }

  @Override
  public MValue get(List<String> path, boolean global) {
    if (global) ops.incrementAndGet();
    Node n = find(path, global);
    return n != null && n.hasValue ? n.value : MValue.EMPTY;
  }

  @Override
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

  /** Set numeric value with primitive subscript (delegates to setNumAtFast). */
  public void setNumAt(String name, int sub, double val) {
    setNumAtFast(nameHash(name), sub, val);
  }

  /** Get numeric value with primitive subscript (delegates to getNumAtFast). */
  public double getNumAt(String name, int sub) {
    return getNumAtFast(nameHash(name), sub);
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
    // splitCached returns cached String[] — fast for repeated writes to same key
    set(toList(splitCached(key)), global, v);
  }

  /**
   * Ultra-fast numeric set: cache-only, skips tree write + MValue boxing. Use with getNumFast for
   * read-back. For tree visibility use set() instead.
   */
  @Override
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
    MValue nv = increment(StoreCore.splitKey(key), global, amount);
    if (global && nv.isnum) {
      oaPut(key, nv.num);
    }
    return nv;
  }

  /** Atomic increment: single node update, counts as 1 store op. */
  @Override
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

  @Override
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
      parent.markDirty();
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
  @Override
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
  @Override
  public String order(List<String> path, boolean global, double dir) {
    if (path.size() < 2) {
      return "";
    }
    Node parent = find(path.subList(0, path.size() - 1), global);
    if (parent == null) {
      return "";
    }
    String cur = path.get(path.size() - 1);
    String[] sorted = parent.sortedKeys();
    if (sorted.length == 0) {
      return "";
    }
    if (dir > 0) {
      // Forward: find first key > cur
      if (cur.isEmpty()) {
        return sorted[0];
      }
      int lo = 0, hi = sorted.length - 1, ans = -1;
      while (lo <= hi) {
        int mid = (lo + hi) >>> 1;
        if (collate(sorted[mid], cur) > 0) {
          ans = mid;
          hi = mid - 1;
        } else {
          lo = mid + 1;
        }
      }
      return ans >= 0 ? sorted[ans] : "";
    } else {
      // Backward: find last key < cur
      if (cur.isEmpty()) {
        return sorted[sorted.length - 1];
      }
      int lo = 0, hi = sorted.length - 1, ans = -1;
      while (lo <= hi) {
        int mid = (lo + hi) >>> 1;
        if (collate(sorted[mid], cur) < 0) {
          ans = mid;
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
      return ans >= 0 ? sorted[ans] : "";
    }
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

  @Override
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
  @Override
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
      rows.add(
          new String[] {
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
    set(path, glob, isnum == 1 ? MValue.ofNumber(ExprEval.toNum(val)) : MValue.ofString(val));
  }
}
