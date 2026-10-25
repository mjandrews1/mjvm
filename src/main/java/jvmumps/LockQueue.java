package jvmumps;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Queued reader/writer locks for {@link StoreApi} concurrency (JavaM #59).
 *
 * <p><b>Models</b>
 *
 * <ul>
 *   <li><b>1W-nR</b> — one writer exclusive, up to {@code maxReaders} readers
 *   <li><b>nW-nR</b> — up to {@code maxWriters} writers, up to {@code maxReaders} readers (writers
 *       do not share a critical section with readers or other writers)
 * </ul>
 *
 * <p>Waiters are <b>FIFO-queued</b>. A writer arriving does not starve readers beyond the head of
 * queue: grants proceed strictly in queue order.
 *
 * <p><b>File-record / multi-process:</b> this class is in-process. SqlStore uses the {@code mlocks}
 * table for cross-process exclusive LOCK; a future multiproc queue can reuse the same grant policy.
 */
public final class LockQueue {
  public enum Mode {
    /** Single writer, many readers (classic RW). */
    ONE_WRITER_MANY_READERS,
    /** Many writers (serialized one-at-a-time), many readers. */
    MANY_WRITERS_MANY_READERS
  }

  private static final class Waiter {
    final boolean writer;
    final String pid;
    final Thread thread;
    boolean granted;

    Waiter(boolean writer, String pid) {
      this.writer = writer;
      this.pid = pid;
      this.thread = Thread.currentThread();
    }
  }

  private final Mode mode;
  private final int maxReaders;
  private final int maxWriters;
  private final Object mu = new Object();

  /** Re-entrant writer holds (M LOCK +X twice). */
  private final java.util.Map<String, Integer> writeHolds = new java.util.HashMap<>();

  private final java.util.Map<Thread, String> lastWriter = new java.util.HashMap<>();
  private final Deque<Waiter> queue = new ArrayDeque<>();
  private int activeReaders;
  private int activeWriters;

  public LockQueue(Mode mode, int maxReaders, int maxWriters) {
    if (maxReaders < 1 || maxWriters < 1) {
      throw new IllegalArgumentException("caps must be >= 1");
    }
    this.mode = mode;
    this.maxReaders = maxReaders;
    // One LockQueue = one resource: writer is always exclusive.
    // nW mode means many *writer waiters* queued, not simultaneous writes.
    this.maxWriters = 1;
  }

  public static LockQueue oneWriter(int maxReaders) {
    return new LockQueue(Mode.ONE_WRITER_MANY_READERS, maxReaders, 1);
  }

  /** Many queued writers (still exclusive section), up to maxReaders. */
  public static LockQueue manyWriters(int maxWriters, int maxReaders) {
    return new LockQueue(Mode.MANY_WRITERS_MANY_READERS, maxReaders, maxWriters);
  }

  public int activeReaders() {
    synchronized (mu) {
      return activeReaders;
    }
  }

  private String lastWriterPid(Thread th) {
    return lastWriter.get(th);
  }

  /** True if this pid already holds the write side (M LOCK re-entrancy). */
  public boolean heldByCurrentWriter(String pid) {
    synchronized (mu) {
      Integer h = writeHolds.get(pid);
      return h != null && h > 0;
    }
  }

  /** Backward-compatible probe: any write hold on this thread. */
  public boolean heldByCurrentWriter() {
    synchronized (mu) {
      String pid = lastWriter.get(Thread.currentThread());
      return pid != null && writeHolds.containsKey(pid);
    }
  }

  public int activeWriters() {
    synchronized (mu) {
      return activeWriters;
    }
  }

  public int queued() {
    synchronized (mu) {
      return queue.size();
    }
  }

  /** Acquire read side; false on timeout. */
  public boolean acquireRead(String pid, long timeoutMs) {
    return acquire(false, pid, timeoutMs);
  }

  /** Acquire write side; false on timeout. */
  public boolean acquireWrite(String pid, long timeoutMs) {
    return acquire(true, pid, timeoutMs);
  }

  public void releaseRead() {
    synchronized (mu) {
      if (activeReaders > 0) {
        activeReaders--;
      }
      pump();
    }
  }

  /** Drop every write hold for {@code pid} (StoreApi full unlock). */
  public void releaseWriteAll(String pid) {
    synchronized (mu) {
      Integer h = pid == null ? null : writeHolds.remove(pid);
      lastWriter.entrySet().removeIf(e -> pid != null && pid.equals(e.getValue()));
      if (h == null || h <= 0) {
        return;
      }
      if (activeWriters > 0) {
        activeWriters--;
      }
      pump();
    }
  }

  /** Release one write hold for {@code pid} (M LOCK -X). */
  public void releaseWrite(String pid) {
    synchronized (mu) {
      Integer h = pid == null ? null : writeHolds.get(pid);
      if (h == null || h <= 0) {
        return;
      }
      if (h > 1) {
        writeHolds.put(pid, h - 1);
        return;
      }
      writeHolds.remove(pid);
      if (lastWriter.get(Thread.currentThread()) != null
          && lastWriter.get(Thread.currentThread()).equals(pid)) {
        lastWriter.remove(Thread.currentThread());
      }
      if (activeWriters > 0) {
        activeWriters--;
      }
      pump();
    }
  }

  /** Release the write hold recorded on this thread (no-op if none). */
  public void releaseWrite() {
    synchronized (mu) {
      String pid = lastWriterPid(Thread.currentThread());
      if (pid == null) {
        return;
      }
      // drop thread map first so nested releaseWrite() is safe
      lastWriter.remove(Thread.currentThread());
      Integer h = writeHolds.get(pid);
      if (h == null || h <= 0) {
        return;
      }
      if (h > 1) {
        writeHolds.put(pid, h - 1);
        lastWriter.put(Thread.currentThread(), pid);
        return;
      }
      writeHolds.remove(pid);
      if (activeWriters > 0) {
        activeWriters--;
      }
      pump();
    }
  }

  private boolean acquire(boolean writer, String pid, long timeoutMs) {
    Waiter w = new Waiter(writer, pid);
    long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
    synchronized (mu) {
      // fast path: empty queue and capacity
      if (writer) {
        Integer h = writeHolds.get(pid);
        if (h != null && h > 0) {
          writeHolds.put(pid, h + 1);
          lastWriter.put(Thread.currentThread(), pid);
          return true;
        }
      }
      if (queue.isEmpty() && canGrant(writer)) {
        take(writer);
        if (writer) {
          writeHolds.put(pid, 1);
          lastWriter.put(Thread.currentThread(), pid);
        }
        return true;
      }
      queue.addLast(w);
      pump();
      while (!w.granted) {
        long wait = deadline - System.currentTimeMillis();
        if (wait <= 0) {
          queue.remove(w);
          pump();
          return false;
        }
        try {
          mu.wait(wait);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          queue.remove(w);
          pump();
          return false;
        }
      }
      return true;
    }
  }

  private boolean canGrant(boolean writer) {
    if (writer) {
      return activeWriters < maxWriters && activeReaders == 0;
    }
    // reader: no writers running
    return activeWriters == 0 && activeReaders < maxReaders;
  }

  private void take(boolean writer) {
    if (writer) {
      activeWriters++;
    } else {
      activeReaders++;
    }
  }

  /** Grant head of queue while capacity allows (strict FIFO). */
  private void pump() {
    while (!queue.isEmpty()) {
      Waiter h = queue.peekFirst();
      if (!canGrant(h.writer)) {
        return;
      }
      queue.removeFirst();
      take(h.writer);
      if (h.writer) {
        writeHolds.put(h.pid, 1);
        lastWriter.put(h.thread, h.pid);
      }
      h.granted = true;
      mu.notifyAll();
    }
  }
}
