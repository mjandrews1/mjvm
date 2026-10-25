package jvmumps;

/**
 * Cross-process locking — Cdb2Store only (uses Comdb2 mlocks table). MStore uses in-process
 * LockQueue. SqlStore has no cross-process locks.
 */
public interface StoreLock {

  /**
   * Cross-process lock acquire.
   *
   * <p><b>Polarity:</b> {@code true} = lock is held by {@code pid} (including idempotent
   * re-acquire). {@code false} = timeout / denied. Tests must use {@code if (!lockAcquire(...))
   * throw} to assert success and {@code if (lockAcquire(...)) throw} to assert the lock stayed
   * blocked.
   */
  boolean lockAcquire(String key, String pid, long timeoutMs);

  /** Cross-process lock release (full unlock for {@code pid}). */
  void lockRelease(String key, String pid);
}
