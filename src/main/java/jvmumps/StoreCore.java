package jvmumps;

import java.util.List;

/**
 * Core store operations — required by all backends. Minimal surface for new stores (Redis, RocksDB,
 * etc.).
 */
public interface StoreCore {

  /** GET: value at path, or empty if undefined. */
  MValue get(List<String> path, boolean global);

  /** SET: write value at path (creates ancestors). */
  void set(List<String> path, boolean global, MValue v);

  /** KILL: delete node and descendants at path. */
  void kill(List<String> path, boolean global);

  /** Atomic increment: read current value, add amount, write back. Returns new value. */
  default MValue increment(List<String> path, boolean global, double amount) {
    MValue cur = get(path, global);
    double v = cur.asNum() + amount;
    MValue nv = MValue.ofNumber(v);
    set(path, global, nv);
    return nv;
  }

  // --- Native numeric fast path (skips string format/parse) ---

  /** GET numeric value directly (skips string format/parse). Default uses get(). */
  default double getNum(String key, boolean global) {
    MValue v = get(key, global);
    return v == MValue.EMPTY ? 0 : v.asNum();
  }

  /** SET numeric value directly (skips string format/parse). Default uses set(). */
  default void setNum(String key, boolean global, double val) {
    set(key, global, MValue.ofNumber(val));
  }

  // --- String key fast path (avoids List<String> allocation) ---

  /** GET by string key (name\001sub1\001sub2...). Default converts to path. */
  default MValue get(String key, boolean global) {
    return get(splitKey(key), global);
  }

  /** SET by string key. Default converts to path. */
  default void set(String key, boolean global, MValue v) {
    set(splitKey(key), global, v);
  }

  /** KILL by string key. Default converts to path. */
  default void kill(String key, boolean global) {
    kill(splitKey(key), global);
  }

  /** Atomic increment by string key. Default converts to path. */
  default MValue increment(String key, boolean global, double amount) {
    return increment(splitKey(key), global, amount);
  }

  /** Split key string into path list. */
  static List<String> splitKey(String key) {
    java.util.ArrayList<String> parts = new java.util.ArrayList<>();
    int start = 0;
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) == 1) {
        parts.add(key.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(key.substring(start));
    return parts;
  }

  /**
   * Batch GET: read multiple keys in one round-trip where possible. Default implementation loops
   * individual gets. Backends override for efficiency.
   */
  default MValue[] getBatch(List<List<String>> paths, boolean global) {
    MValue[] results = new MValue[paths.size()];
    for (int i = 0; i < paths.size(); i++) {
      results[i] = get(paths.get(i), global);
    }
    return results;
  }

  /** Number of store operations (GET + SET + KILL) performed. */
  default long storeOps() {
    return 0;
  }
}
