package jvmumps;

import java.util.List;

/**
 * Full persistence SPI — extends all capability interfaces.
 *
 * <p><b>Capability split (issue #201):</b>
 *
 * <ul>
 *   <li>{@link StoreCore} — get/set/kill/increment (all backends)
 *   <li>{@link StoreOrder} — order/data/queryNext (MStore, Cdb2Store, SqlStore for CI)
 *   <li>{@link StoreLock} — lockAcquire/lockRelease (Cdb2Store only)
 *   <li>{@link StoreTx} — newScope/merge (Cdb2Store only)
 * </ul>
 *
 * <p>Production engines: {@link MStore} (dev/test/cache) + {@link Cdb2Store} (tsx plane). {@link
 * SqlStore} is a thin CI/conformance wrapper — no new features.
 *
 * <p>New backends should implement {@link StoreCore} only, then add {@link StoreOrder}/{@link
 * StoreLock}/{@link StoreTx} as needed.
 *
 * <p>{@code path} is subscript chain; {@code global} selects {@code ^NAME} vs local. Values are
 * {@link MValue}; numeric strings are stored as text.
 */
/**
 * <b>FROZEN</b> — JavaM is frozen as the semantic reference oracle (#199). mJVM is the production
 * engine. New features go to mJVM. Bug fixes only for JavaM.
 */
public interface StoreApi extends StoreCore, StoreOrder, StoreLock, StoreTx {

  // StoreCore: get, set, kill, increment, storeOps
  // StoreOrder: data, order, queryNext
  // StoreLock: lockAcquire, lockRelease
  // StoreTx: newScope, merge

  // Backward-compatible defaults for optional capabilities
  @Override
  default boolean lockAcquire(String key, String pid, long timeoutMs) {
    return true;
  }

  @Override
  default void lockRelease(String key, String pid) {}

  @Override
  default void newScope(List<String> names) {}

  // mjvm#31: argumentless/exclusive NEW — full local-root scope (store side).
  // Defaults are no-ops for stores without a locals tree (MapDB/H2/Cdb2/Tiered).
  default void newScopeAll() {}

  default void restoreScopeAll() {}

  /** Peek-restore keep-listed roots from the top of the all-scope stack (no pop). */
  default void restoreKeepFromScopeAll(java.util.Set<String> keep) {}

  @Override
  default void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal) {
    MValue v = get(src, sGlobal);
    if (v != MValue.EMPTY) set(dst, dGlobal, v);
  }
}
