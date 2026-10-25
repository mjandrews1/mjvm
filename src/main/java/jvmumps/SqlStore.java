package jvmumps;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Live SQLite backend for the M engine (J7). Key: name + SEP + sub + SEP + … for prefix scans. */
/**
 * SQLite-backed {@link StoreApi} (WAL; tests use temp files).
 *
 * <p>Durable JavaM/mJVM store for integration ({@code Sql*Smoke}, {@code itest}). RSM remains the
 * <b>file-backed production M oracle</b> ({@code rsm.dat} / global buffers) — not replaced by
 * SQLite. Cross-process locks use {@code mlocks}; see also {@link MStore} for the ephemeral
 * backend. Lock/file-record semantics: {@code docs/SQLSTORE-LOCKS.md}.
 *
 * <p><b>Thread safety:</b> JDBC {@link Connection} is not thread-safe; all store ops and {@code
 * mlocks} updates synchronize on {@code conn}. {@link LockQueue} is in-process FIFO under its own
 * monitor (never nested under {@code conn} in the wrong order — queue lock is always acquired
 * <i>after</i> a successful mlocks insert, released before dropping conn).
 */
public final class SqlStore implements StoreApi {
  static final char SEP = 1;

  final Connection conn;

  /** In-process FIFO waiters per key (JavaM #59); mlocks is cross-process. */
  private final ConcurrentHashMap<String, LockQueue> lockQueues = new ConcurrentHashMap<>();

  /** Wait-time measurements (ms) for lockAcquire. */
  private final ConcurrentHashMap<String, Long> lastWaitMs = new ConcurrentHashMap<>();

  /** Failed mlocks inserts since last sweep (hot-path counter, under conn). */
  private int mlockMisses;

  public SqlStore(String path) throws Exception {
    Class.forName("org.sqlite.JDBC");
    conn = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (Statement st = conn.createStatement()) {
      st.execute(
          "CREATE TABLE IF NOT EXISTS nodes("
              + "k TEXT PRIMARY KEY,"
              + "name TEXT NOT NULL,"
              + "subs TEXT NOT NULL,"
              + "val TEXT NOT NULL,"
              + "isnum INTEGER NOT NULL,"
              + "ck TEXT NOT NULL DEFAULT '',"
              + "sk TEXT NOT NULL DEFAULT '')");
      st.execute("CREATE INDEX IF NOT EXISTS nodes_k ON nodes(k)");
      st.execute("CREATE INDEX IF NOT EXISTS nodes_ck ON nodes(ck)");
      st.execute(
          "CREATE TABLE IF NOT EXISTS mlocks("
              + "lk TEXT PRIMARY KEY,"
              + "pid TEXT NOT NULL,"
              + "at INTEGER NOT NULL)");
    }
  }

  /** Sortable collate key: 0+num or 1+string (numbers before strings). */
  public static String collateKey(String s) {
    if (MStore.numeric(s)) {
      double d = ExprEval.toNum(s);
      return "0" + String.format("%020.6f", d);
    }
    return "1" + s;
  }

  public static String key(String name, List<String> path) {
    StringBuilder sb = new StringBuilder(name);
    for (int i = 1; i < path.size(); i++) {
      sb.append(SEP).append(path.get(i));
    }
    return sb.toString();
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public MValue get(List<String> path, boolean global) {
    synchronized (conn) {
      try (PreparedStatement ps = conn.prepareStatement("SELECT val, isnum FROM nodes WHERE k=?")) {
        ps.setString(1, key(path.get(0), path));
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getInt(2) == 1
                ? MValue.ofNumber(ExprEval.toNum(rs.getString(1)))
                : MValue.ofString(rs.getString(1));
          }
        }
      } catch (Exception e) {
        return MValue.EMPTY;
      }
      return MValue.EMPTY;
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public void set(List<String> path, boolean global, MValue v) {
    synchronized (conn) {
      String key = key(path.get(0), path);
      StringBuilder sk = new StringBuilder();
      for (int i = 1; i < path.size(); i++) {
        if (i > 1) sk.append(SEP);
        sk.append(collateKey(path.get(i)));
      }
      try (PreparedStatement ps =
          conn.prepareStatement("INSERT OR REPLACE INTO nodes VALUES (?,?,?,?,?,?,?)")) {
        ps.setString(1, key);
        ps.setString(2, path.get(0));
        ps.setString(3, MStore.subsJson(path.subList(1, path.size()).toArray(new String[0])));
        ps.setString(4, v.isnum ? ExprEval.mstr(v.num) : v.str);
        ps.setInt(5, v.isnum ? 1 : 0);
        ps.setString(6, sk.toString());
        ps.setString(7, sk.toString());
        ps.executeUpdate();
      } catch (Exception e) {
        throw new MError("sqlite set failed: " + e);
      }
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public void kill(List<String> path, boolean global) {
    synchronized (conn) {
      String k = key(path.get(0), path);
      try (PreparedStatement ps =
          conn.prepareStatement("DELETE FROM nodes WHERE k=? OR k LIKE ? ESCAPE '\\'")) {
        ps.setString(1, k);
        ps.setString(
            2, k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + SEP + "%");
        ps.executeUpdate();
      } catch (Exception e) {
        // fallback: scan delete
        try (PreparedStatement ps = conn.prepareStatement("SELECT k FROM nodes");
            ResultSet rs = ps.executeQuery()) {
          List<String> doomed = new ArrayList<>();
          while (rs.next()) {
            String key = rs.getString(1);
            if (key.equals(k)
                || (key.startsWith(k)
                    && key.length() > k.length()
                    && key.charAt(k.length()) == SEP)) {
              doomed.add(key);
            }
          }
          for (String key : doomed) {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM nodes WHERE k=?")) {
              del.setString(1, key);
              del.executeUpdate();
            }
          }
        } catch (Exception e2) {
          throw new MError("sqlite kill failed: " + e2);
        }
      }
    }
  }

  @Override
  @SuppressWarnings({"RedundantThrows", "DLS_DEAD_LOCAL_STORE"})
  public MValue increment(List<String> path, boolean global, double amount) {
    String k = key(path.get(0), path);
    synchronized (this) {
      // Single round-trip: read current, add, write back
      MValue cur = get(path, global);
      double v = cur.asNum() + amount;
      MValue nv = MValue.ofNumber(v);
      set(path, global, nv);
      return nv;
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public int data(List<String> path, boolean global) {
    synchronized (conn) {
      String k = key(path.get(0), path);
      boolean hasVal = false;
      boolean hasDesc = false;
      try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM nodes WHERE k=? LIMIT 1")) {
        ps.setString(1, k);
        hasVal = ps.executeQuery().next();
      } catch (Exception e) {
        return 0;
      }
      // any descendant key starts with k+SEP
      String lo = k + SEP;
      String hi = lo + Character.MAX_VALUE;
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT 1 FROM nodes WHERE k>=? AND k<? LIMIT 1")) {
        ps.setString(1, lo);
        ps.setString(2, hi);
        hasDesc = ps.executeQuery().next();
      } catch (Exception e) {
        return 0;
      }
      if (hasVal && hasDesc) {
        return 11;
      }
      if (hasVal) {
        return 1;
      }
      return hasDesc ? 10 : 0;
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public String order(List<String> path, boolean global, double dir) {
    synchronized (conn) {
      if (path.size() < 2) {
        return "";
      }
      List<String> parent = path.subList(0, path.size() - 1);
      String cur = path.get(path.size() - 1);
      String pfx = key(parent.get(0), parent) + SEP;
      String lo = pfx;
      String hi = pfx + Character.MAX_VALUE;
      StringBuilder parentSk = new StringBuilder();
      for (int i = 1; i < parent.size(); i++) {
        if (i > 1) parentSk.append(SEP);
        parentSk.append(collateKey(parent.get(i)));
      }
      String skPrefix = parentSk.length() == 0 ? "" : parentSk.toString() + SEP;
      String hiMark = "\uffff";
      String curSk = cur.isEmpty() ? "" : collateKey(cur);
      // Skip cursor subtree on forward walk (multi-level $ORDER).
      String skBound =
          dir > 0
              ? (cur.isEmpty() ? skPrefix : skPrefix + curSk + SEP + hiMark)
              : (cur.isEmpty() ? skPrefix + hiMark : skPrefix + curSk);
      try (PreparedStatement ps =
          conn.prepareStatement(
              dir > 0
                  ? "SELECT k FROM nodes WHERE k>=? AND k<? AND sk>? ORDER BY sk LIMIT 1"
                  : "SELECT k FROM nodes WHERE k>=? AND k<? AND sk<? ORDER BY sk DESC LIMIT 1")) {
        ps.setString(1, lo);
        ps.setString(2, hi);
        ps.setString(3, skBound);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            String k = rs.getString(1);
            String rest = k.substring(pfx.length());
            int j = rest.indexOf(SEP);
            return j < 0 ? rest : rest.substring(0, j);
          }
        }
      } catch (Exception e) {
        return "";
      }
      return "";
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public void newScope(List<String> names) {
    // SQLite rows persist across NEW (live backend).
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal) {
    MValue v = get(src, sGlobal);
    if (v != MValue.EMPTY) {
      set(dst, dGlobal, v);
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public String queryNext(List<String> path, boolean global, int dir) {
    return order(path, global, dir);
  }

  /** Drop locks older than ttlMs (stale holders + their in-process queue holds). */
  @SuppressWarnings("RedundantThrows")
  public int lockSweep(long ttlMs) {
    long cutoff = System.currentTimeMillis() - ttlMs;
    synchronized (conn) {
      List<String[]> stale = new ArrayList<>();
      try (PreparedStatement ps =
          conn.prepareStatement("SELECT lk, pid FROM mlocks WHERE at < ?")) {
        ps.setLong(1, cutoff);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            stale.add(new String[] {rs.getString(1), rs.getString(2)});
          }
        }
      } catch (Exception e) {
        return 0;
      }
      try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mlocks WHERE at < ?")) {
        ps.setLong(1, cutoff);
        int n = ps.executeUpdate();
        for (String[] sp : stale) {
          LockQueue q = lockQueues.get(sp[0]);
          if (q != null) {
            q.releaseWriteAll(sp[1]);
          }
        }
        return n;
      } catch (Exception e) {
        return 0;
      }
    }
  }

  /**
   * LOCK + acquire. In-process threads FIFO-queue on {@link LockQueue}; cross-process exclusive
   * ownership is the {@code mlocks} table.
   *
   * <p><b>Polarity:</b> {@code true} = held (idempotent reacquire); {@code false} = timed out. Do
   * not invert in tests. Returns false if {@code timeoutMs} elapses before both are held.
   */
  @Override
  @SuppressWarnings("RedundantThrows")
  public boolean lockAcquire(String key, String pid, long timeoutMs) {
    long t0 = System.currentTimeMillis();
    long budget = Math.max(0, timeoutMs);
    long deadline = t0 + budget;
    LockQueue q = lockQueues.computeIfAbsent(key, k -> LockQueue.oneWriter(64));
    // Same pid already owns this key: idempotent (M LOCK via Set in LineTxLocks).
    if (q.heldByCurrentWriter(pid)) {
      return true;
    }
    boolean ok = false;
    while (true) {
      boolean ins = false;
      synchronized (conn) {
        try (PreparedStatement ps =
            conn.prepareStatement("INSERT OR IGNORE INTO mlocks VALUES (?,?,?)")) {
          ps.setString(1, key);
          ps.setString(2, pid);
          ps.setLong(3, System.currentTimeMillis());
          ins = ps.executeUpdate() > 0;
        } catch (Exception e) {
          ins = false;
        }
        if (!ins) {
          try (PreparedStatement ps = conn.prepareStatement("SELECT pid FROM mlocks WHERE lk=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next() && pid.equals(rs.getString(1))) {
                ins = true;
              }
            }
          } catch (Exception ignored) {
          }
        }
        if (!ins) {
          // sweep rarely — every miss is too hot under contention
          if ((++mlockMisses & 0x3f) == 0) {
            lockSweep(30_000);
          }
        }
      }
      if (ins) {
        ok = true;
        break;
      }
      if (System.currentTimeMillis() >= deadline) {
        break;
      }
      try {
        Thread.sleep(2);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (ok) {
      long left = deadline - System.currentTimeMillis();
      if (!q.acquireWrite(pid, Math.max(1, left))) {
        // could not take local queue in budget — drop mlocks and fail
        try (PreparedStatement ps =
            conn.prepareStatement("DELETE FROM mlocks WHERE lk=? AND pid=?")) {
          ps.setString(1, key);
          ps.setString(2, pid);
          ps.executeUpdate();
        } catch (Exception ignored) {
        }
        ok = false;
      }
    }
    lastWaitMs.put(key, System.currentTimeMillis() - t0);
    return ok;
  }

  /** Last lockAcquire wait duration (ms) for key; -1 if never measured. */
  @SuppressWarnings("RedundantThrows")
  public long lastLockWaitMs(String key) {
    Long v = lastWaitMs.get(key);
    return v == null ? -1L : v;
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public void lockRelease(String key, String pid) {
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mlocks WHERE lk=? AND pid=?")) {
      ps.setString(1, key);
      ps.setString(2, pid);
      ps.executeUpdate();
    } catch (Exception ignored) {
    }
    LockQueue q = lockQueues.get(key);
    if (q != null) {
      q.releaseWriteAll(pid);
    }
  }

  /** Idempotent close — drop queues and JDBC connection (leak fix). */
  @SuppressWarnings("RedundantThrows")
  public void close() {
    lockQueues.clear();
    lastWaitMs.clear();
    synchronized (conn) {
      try {
        if (!conn.isClosed()) {
          conn.close();
        }
      } catch (Exception ignored) {
      }
    }
  }
}
