package jvmumps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comdb2-backed StoreApi via persistent cdb2pipe.
 *
 * <p>Same collation / key layout as SqlStore (k = name+SEP+subs, sk = per-subscript
 * SqlStore.collateKey). $ORDER uses an indexed (name, sk) range scan so the walk cost matches
 * SQLite's nodes_sk plan rather than a full table scan.
 *
 * <p>Transport: one long-lived tools/cdb2pipe process (cdb2api). Process-spawn per op would
 * dominate and make the walk comparison unfair.
 *
 * <p><b>Write cache:</b> pending SETs are held in a {@link LinkedHashMap} keyed by hex(k). GET
 * checks the cache first (read-your-writes), avoiding a flush round-trip for read-modify-write
 * patterns like BANK.m DEP/WDR.
 *
 * <p><b>Pipelining:</b> when a flush is required before a read command, the SETB and the read are
 * written together, then both responses are read — one latency round-trip instead of two.
 *
 * <p><b>Thread safety:</b> all public ops synchronize on {@code this} (single pipe is
 * request/response). {@link #set} group-commits via SETB; {@link #close} is idempotent and always
 * reaps the pipe process.
 */
/**
 * @deprecated Cdb2Store is deprecated in mJVM. Use H2Store or MapDBStore for single-node
 *     persistence. Cdb2Store retained for reference only. Comdb2 infrastructure is frozen (tsx
 *     plane offline).
 */
@Deprecated
public final class Cdb2Store implements StoreApi {
  static final char SEP = 1;

  /** Pending write: wire row + decoded value for cache lookups. */
  private static final class PendingRow {
    final String row;
    final MValue val;

    PendingRow(String row, MValue val) {
      this.row = row;
      this.val = val;
    }
  }

  private final Process proc;
  private final BufferedWriter in;
  private final BufferedReader out;

  /** Write cache — hex(k) → pending row. LinkedHashMap preserves flush order. */
  private final LinkedHashMap<String, PendingRow> pending = new LinkedHashMap<>();

  private volatile boolean closed;

  /** In-process FIFO waiters (same policy as SqlStore LockQueue). */
  private final ConcurrentHashMap<String, LockQueue> lockQueues = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, Long> lastWaitMs = new ConcurrentHashMap<>();
  private static final int BATCH = 300; // Comdb2 ~1800 bind limit (300 rows x 6)

  public Cdb2Store(String pipePath, String db, String type) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(pipePath, db, type);
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process p = null;
    try {
      p = pb.start();
      in = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
      out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      String pong = out.readLine();
      if (pong == null || !pong.startsWith("PONG")) {
        throw new MError("cdb2pipe failed to start: " + pong);
      }
    } catch (Exception e) {
      if (p != null) {
        p.destroyForcibly();
      }
      throw e;
    }
    this.proc = p;
  }

  private static String hex(String s) {
    byte[] b = s.getBytes(StandardCharsets.UTF_8);
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) sb.append(String.format("%02x", x));
    return sb.toString();
  }

  private static String unhex(String h) {
    int n = h.length() / 2;
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) {
      b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
    }
    return new String(b, StandardCharsets.UTF_8);
  }

  private synchronized String cmd(String line) {
    try {
      in.write(line);
      in.write(10);
      in.flush();
      String r = out.readLine();
      if (r == null) throw new MError("cdb2pipe EOF");
      return r;
    } catch (MError e) {
      throw e;
    } catch (Exception e) {
      throw new MError("cdb2pipe io: " + e);
    }
  }

  /** Write a command without reading its response. Caller must {@link #recv}. */
  @SuppressWarnings("RedundantThrows")

  private void send(String line)  {
    try {
      in.write(line);
      in.write(10);
    } catch (Exception e) {
      throw new MError("cdb2pipe io: " + e);
    }
  }

  /** Read one response line. */
  @SuppressWarnings("RedundantThrows")

  private String recv()  {
    try {
      in.flush();
      String r = out.readLine();
      if (r == null) throw new MError("cdb2pipe EOF");
      return r;
    } catch (MError e) {
      throw e;
    } catch (Exception e) {
      throw new MError("cdb2pipe io: " + e);
    }
  }

  /**
   * Flush pending SETs (if any) and send {@code nextCmd} in one pipe write. Returns the response to
   * {@code nextCmd} (flush response is checked for OK).
   */
  @SuppressWarnings("RedundantThrows")

  private String flushAndCmd(String nextCmd)  {
    if (pending.isEmpty()) {
      return cmd(nextCmd);
    }
    // Build SETB payload
    int n = pending.size();
    StringBuilder sb = new StringBuilder("SETB\t").append(n).append('\n');
    for (PendingRow pr : pending.values()) {
      sb.append(pr.row).append('\n');
    }
    pending.clear();
    // Pipeline: write SETB + nextCmd, then read both responses
    send(sb.toString());
    send(nextCmd);
    String flushResp = recv();
    if (!flushResp.startsWith("OK")) throw new MError("cdb2 setb: " + flushResp);
    return recv();
  }

  private static String key(String name, List<String> path) {
    return SqlStore.key(name, path);
  }

  private static String sk(List<String> path) {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < path.size(); i++) {
      if (i > 1) sb.append(SEP);
      sb.append(SqlStore.collateKey(path.get(i)));
    }
    return sb.toString();
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public MValue get(List<String> path, boolean global)  {
    String k = key(path.get(0), path);
    String hk = hex(k);
    synchronized (this) {
      // Read-your-writes: check pending cache first
      PendingRow pr = pending.get(hk);
      if (pr != null) return pr.val;
      String r = flushAndCmd("GET\t" + hk);
      if (r.startsWith("VAL ")) {
        String[] p = r.split(" ");
        String v = unhex(p[1]);
        int isnum = Integer.parseInt(p[2]);
        return isnum == 1 ? MValue.ofNumber(ExprEval.toNum(v)) : MValue.ofString(v);
      }
      return MValue.EMPTY;
    }
  }

  /**
   * Batch GET: read multiple keys in one pipe round-trip. Returns MValue array in same order as
   * keys. Empty for missing keys. Used by bank DEP/WDR to read C, B, N in one round-trip instead of
   * three.
   */
  @Override
  public MValue[] getBatch(List<List<String>> paths, boolean global) {
    int n = paths.size();
    MValue[] results = new MValue[n];
    synchronized (this) {
      // Check write cache first
      java.util.List<Integer> misses = new java.util.ArrayList<>();
      for (int i = 0; i < n; i++) {
        String hk = hex(key(paths.get(i).get(0), paths.get(i)));
        PendingRow pr = pending.get(hk);
        if (pr != null) {
          results[i] = pr.val;
        } else {
          misses.add(i);
        }
      }
      if (misses.isEmpty()) return results;

      // Build GETB command for cache misses
      StringBuilder sb = new StringBuilder("GETB\t").append(misses.size()).append('\n');
      for (int idx : misses) {
        sb.append(hex(key(paths.get(idx).get(0), paths.get(idx)))).append('\n');
      }

      // Pipeline: flush pending SETs + GETB in one write
      String resp;
      if (!pending.isEmpty()) {
        int pn = pending.size();
        StringBuilder flushSb = new StringBuilder("SETB\t").append(pn).append('\n');
        for (PendingRow pr : pending.values()) {
          flushSb.append(pr.row).append('\n');
        }
        pending.clear();
        send(flushSb.toString());
        send(sb.toString());
        String flushResp = recv();
        if (!flushResp.startsWith("OK")) throw new MError("cdb2 setb: " + flushResp);
      } else {
        send(sb.toString());
        try {
          in.flush();
        } catch (java.io.IOException e) {
          throw new MError("cdb2pipe io: " + e);
        }
      }

      // Read n responses
      for (int j = 0; j < misses.size(); j++) {
        String r = recv();
        int idx = misses.get(j);
        if (r.startsWith("VAL ")) {
          String[] p = r.split(" ");
          String v = unhex(p[1]);
          int isnum = Integer.parseInt(p[2]);
          results[idx] = isnum == 1 ? MValue.ofNumber(ExprEval.toNum(v)) : MValue.ofString(v);
        } else {
          results[idx] = MValue.EMPTY;
        }
      }
    }
    return results;
  }

  /** Group-commit pending SETs (write buffer). Safe to call anytime. */
  public synchronized void flush() {
    flushLocked();
  }

  @SuppressWarnings("RedundantThrows")


  private void flushLocked()  {
    if (pending.isEmpty()) return;
    int n = pending.size();
    StringBuilder sb = new StringBuilder("SETB\t").append(n).append('\n');
    for (PendingRow pr : pending.values()) {
      sb.append(pr.row).append('\n');
    }
    pending.clear();
    String r = cmd(sb.toString());
    if (!r.startsWith("OK")) throw new MError("cdb2 setb: " + r);
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public void set(List<String> path, boolean global, MValue v)  {
    String val = v.isnum ? ExprEval.mstr(v.num) : v.str;
    String k = key(path.get(0), path);
    String row =
        hex(k)
            + "\t"
            + hex(sk(path))
            + "\t"
            + hex(val)
            + "\t"
            + (v.isnum ? 1 : 0)
            + "\t"
            + hex(path.get(0));
    synchronized (this) {
      if (closed) {
        throw new MError("cdb2store closed");
      }
      pending.put(hex(k), new PendingRow(row, v));
      if (pending.size() >= BATCH) {
        flushLocked();
      }
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public void kill(List<String> path, boolean global)  {
    String k = key(path.get(0), path);
    String hk = hex(k);
    synchronized (this) {
      // Remove pending entries for this key and descendants
      pending
          .keySet()
          .removeIf(
              pk -> {
                String pkPlain = unhex(pk);
                return pkPlain.equals(k) || pkPlain.startsWith(k + SEP);
              });
      String r = flushAndCmd("KILL\t" + hk);
      if (!r.startsWith("OK")) throw new MError("cdb2 kill: " + r);
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public int data(List<String> path, boolean global)  {
    synchronized (this) {
      String r = flushAndCmd("DATA\t" + hex(key(path.get(0), path)));
      if (r.startsWith("NUM ")) return Integer.parseInt(r.substring(4));
      return 0;
    }
  }

  @Override
  @SuppressWarnings({"RedundantThrows", "DLS_DEAD_LOCAL_STORE"})

  public String order(List<String> path, boolean global, double dir)  {
    if (path.size() < 2) return "";
    synchronized (this) {
      List<String> parent = path.subList(0, path.size() - 1);
      String cur = path.get(path.size() - 1);
      String pfx = key(parent.get(0), parent) + SEP;
      String lo = pfx;
      String hi = pfx + Character.MAX_VALUE;
      StringBuilder parentSk = new StringBuilder();
      for (int i = 1; i < parent.size(); i++) {
        if (i > 1) parentSk.append(SEP);
        parentSk.append(SqlStore.collateKey(parent.get(i)));
      }
      String skPrefix = parentSk.length() == 0 ? "" : parentSk.toString() + SEP;
      String hiMark = "\uffff";
      String curSk = cur.isEmpty() ? "" : SqlStore.collateKey(cur);
      // Forward after cur must skip the whole subtree of cur (sk = curSk+SEP+…).
      // Otherwise $ORDER returns cur again forever (ydb-mesh related() hang).
      String skLo;
      String skHi;
      if (dir > 0) {
        skLo = cur.isEmpty() ? skPrefix : skPrefix + curSk + SEP + hiMark;
        skHi = skPrefix + hiMark;
      } else {
        skLo = skPrefix;
        skHi = cur.isEmpty() ? skPrefix + hiMark : skPrefix + curSk;
      }
      // For reverse: SQL is sk<@sk AND sk>@skhi, so pass upper as @sk, lower as @skhi
      String argSk = dir > 0 ? skLo : skHi;
      String argSkhi = dir > 0 ? skHi : skLo;
      String r =
          flushAndCmd(
              "ORDER\t"
                  + hex(parent.get(0))
                  + "\t"
                  + hex(argSk)
                  + "\t"
                  + hex(argSkhi)
                  + "\t"
                  + (dir > 0 ? 1 : -1));
      if (!r.startsWith("KEY ")) return "";
      String k = unhex(r.substring(4));
      String rest = k.substring(pfx.length());
      int j = rest.indexOf(SEP);
      return j < 0 ? rest : rest.substring(0, j);
    }
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public String queryNext(List<String> path, boolean global, int dir)  {
    return order(path, global, dir);
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public void newScope(List<String> names)  {}

  @Override
  @SuppressWarnings("RedundantThrows")

  public void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal)  {
    MValue v = get(src, sGlobal);
    if (v != MValue.EMPTY) set(dst, dGlobal, v);
  }

  /**
   * Cross-process LOCK via Comdb2 {@code mlocks} + in-process {@link LockQueue}. Same contract as
   * {@link SqlStore#lockAcquire}: idempotent reacquire, full unlock on {@link #lockRelease}.
   */
  @Override
  @SuppressWarnings("RedundantThrows")

  public boolean lockAcquire(String key, String pid, long timeoutMs)  {
    long t0 = System.currentTimeMillis();
    long deadline = t0 + Math.max(0, timeoutMs);
    LockQueue q = lockQueues.computeIfAbsent(key, k -> LockQueue.oneWriter(64));
    if (q.heldByCurrentWriter(pid)) {
      return true;
    }
    boolean ok = false;
    while (true) {
      boolean ins = false;
      synchronized (this) {
        String r = cmd("LOCKA\t" + hex(pid) + "\t" + hex(key));
        ins = r.startsWith("OK");
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
        synchronized (this) {
          cmd("LOCKR\t" + hex(pid) + "\t" + hex(key));
        }
        ok = false;
      }
    }
    lastWaitMs.put(key, System.currentTimeMillis() - t0);
    return ok;
  }

  @Override
  @SuppressWarnings("RedundantThrows")

  public void lockRelease(String key, String pid)  {
    synchronized (this) {
      try {
        cmd("LOCKR\t" + hex(pid) + "\t" + hex(key));
      } catch (Exception ignored) { ignored.printStackTrace(); }
    }
    LockQueue q = lockQueues.get(key);
    if (q != null) {
      q.releaseWriteAll(pid);
    }
  }

  /** Idempotent close — always reaps the cdb2pipe process. */
  @SuppressWarnings("RedundantThrows")

  public void close()  {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      lockQueues.clear();
      lastWaitMs.clear();
      try {
        flushLocked();
        cmd("QUIT");
      } catch (Exception ignored) { ignored.printStackTrace(); }
      try {
        in.close();
        out.close();
      } catch (Exception ignored) { ignored.printStackTrace(); }
    }
    proc.destroyForcibly();
    try {
      proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
