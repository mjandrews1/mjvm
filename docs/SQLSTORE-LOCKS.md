# SqlStore lock / file-record semantics (JavaM #59)

**Scope:** `jvmumps.SqlStore` + `jvmumps.LockQueue` · **Not** RSM production (see `MStore`/`SqlStore` Javadoc — RSM is the file-backed oracle).

## Model

| Layer | Mechanism | Scope |
|-------|-----------|--------|
| **In-process** | per-key `LockQueue` (FIFO, reentrant write) | threads in one JVM |
| **Cross-process** | SQLite `mlocks` table (`lk` PK, `pid`, `at`) | multiple processes on one DB file |
| **Stale holders** | `lockSweep(ttlMs)` deletes `mlocks` older than TTL | crashed holders |

`StoreApi.lockAcquire(key, pid, timeoutMs)` / `lockRelease(key, pid)`.

## File-record meaning

- One **DB file** (`jdbc:sqlite:path`) is the **record unit** for the lock table.
- **Key** is a logical lock name (M `LOCK` ref text); not a filesystem path.
- **Reentrant:** same thread may `+X` twice; `lockRelease` drops one hold.
- **Exclusive section:** one writer per `LockQueue`; readers share up to `maxReaders`.
- **Multi-key:** distinct keys **do not block** each other (`SqlLockMultiKeySmoke`).
- **Multi-process:** exclusive ownership is **`mlocks`**; in-process queue only orders local waiters. Two JVMs each hold a local queue slot but contend on the same `mlocks` row.

## Caps / timeouts

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `maxReaders` | 64 (SqlStore queue) | concurrent readers on one key |
| writer | exclusive (1 per key) | nW = queued writers, not concurrent writes |
| `timeoutMs` | caller | acquire budget; `false` on expiry |
| `lockSweep` | 30s in acquire loop | drop stale `mlocks` |

## Measured (SqlLockStressSmoke / SqlLockMeasureSmoke, 2026-10-14)

| Case | Result |
|------|--------|
| Uncontended acquire | ~6–10 ms |
| 200×25 single key @ 2s | granted=4388 · timeouts=612 (12.2%) · p50 574ms · p99 2006ms · wall 20.5s |
| 200×25 single key @ 5s | granted=4965 · timeouts=35 (0.7%) · p50 527ms · p99 4506ms · wall 23.0s |
| 8 serial waiters | max_wait ~56 ms |
| Multi-key parallel | 4 keys × 40 rounds all granted |

Notes: 5s budget no longer hangs (was a `LockQueue.releaseWrite` hold-leak after `pump()` grant). Timeouts at 2s are budget expiry, not deadlock. `SqlLockStressSmoke [threads] [iters] [timeoutMs]`.

## Multi-process notes

1. **pid** must be unique per process (we use `ProcessHandle.current().pid()`).
2. `lockSweep` only for **stale** rows; do not sweep a live holder.
3. File-record durability = SQLite file; do not copy a live DB with held `mlocks` without a consistent snapshot.
4. **RSM** remains the production M oracle (`RSM_DBFILE`); this is JavaM/mJVM integration locking.

## Smoke

- `SqlLockMeasureSmoke` — uncontended / timeout / serial
- `SqlLockStressSmoke` — 200-thread p50/p99
- `SqlLockMultiKeySmoke` — key isolation + parallel keys


## Assert polarity checklist (tests)

`StoreApi.lockAcquire` returns **`true` = held**, **`false` = denied/timeout**.

| You want to assert | Write | Wrong (inverted) |
|--------------------|-------|------------------|
| acquire succeeded | `if (!st.lockAcquire(k, pid, t)) throw …` | `if (st.lockAcquire(…)) throw` |
| other pid stayed blocked | `if (st.lockAcquire(k, other, t)) throw …` | `if (!st.lockAcquire(…)) throw` |
| reacquire is idempotent | `if (!st.lockAcquire(k, same, t)) throw …` | `if (st.lockAcquire(…)) throw` |

Known bug fixed: `TStartUndoSmoke` inverted reacquire (`if (lockAcquire) throw`).

`MjvmBench.check(label, ok, detail)` **throws** on `!ok` (targets are enforced).


## Transaction isolation notes (#122)

| Topic | Behavior |
|-------|----------|
| **Provisional SET** | Visible to other processes/threads before TCOMMIT (no SQL isolation). TROLLBACK restores prior value or **KILL**s a new node. |
| **Per-JOB undo** | Each `LineInterp` has its own `LineTxLocks` (`TStartJobSmoke`). |
| **Phantom `$ORDER`** | A concurrent insert can appear mid-walk; M has no cursor isolation. Documented, not a bug. |
| **Cdb2 LOCK** | `mlocks` + `LockQueue` (PR #127) — same polarity as SqlStore. |
