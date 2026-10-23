# TieredStore Design — LRU Cache + Disk Backing

**Status:** Pre-design (no code)
**Goal:** Preserve MStore/H2Store performance with permanence via write-back cache.
**Constraint:** Thread-safe startup load, runtime operation, shutdown flush.

---

## 1. Architecture

```
┌─────────────────────────────────────────┐
│              TieredStore                 │
│                                          │
│  ┌──────────────┐    ┌───────────────┐  │
│  │  LRU Cache   │◄──►│  Write-Buffer │  │
│  │  (hot keys)  │    │  (dirty set)  │  │
│  └──────┬───────┘    └───────┬───────┘  │
│         │                    │          │
│  ┌──────┴────────────────────┴───────┐  │
│  │        Background Flusher         │  │
│  └──────────────────┬────────────────┘  │
│                     │                   │
│  ┌──────────────────┴────────────────┐  │
│  │     Disk Store (H2/MapDB)         │  │
│  │     (source of truth)             │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Components

| Component | Responsibility |
|-----------|---------------|
| **LRU Cache** | Hot keys in memory. Eviction policy: write-aware LRU. |
| **Write-Buffer** | Set of dirty keys. Thread-safe. |
| **Background Flusher** | Periodic or event-driven flush of dirty pages to disk. |
| **Disk Store** | Source of truth. H2Store or MapDBStore. |
| **WAL** | Write-ahead log for crash recovery (see §3). |

---

## 2. Write-Behind Semantics

### 2.1 Write path

```
set(key, value):
    cache.put(key, value)          // O(1), instant
    dirtySet.add(key)              // mark for flush
    wal.append(SET, key, value)    // append to WAL (durable)
    if dirtySet.size() >= FLUSH_THRESHOLD:
        triggerFlush()             // async
```

### 2.2 Flush path (background thread)

```
flush():
    keys = dirtySet.snapshot()
    for batch in partition(keys, BATCH_SIZE):
        disk.setBatch(batch)       // single transaction
    dirtySet.remove(keys)
    wal.truncate(keys)             // reclaim WAL space
```

### 2.3 Write-behind vs write-through

| Mode | Latency | Durability | Use case |
|------|---------|-----------|----------|
| **Write-through** | ~2x slower (disk I/O per write) | Zero data loss | Financial, clinical |
| **Write-behind** | ~MStore speed (cache only) | Data loss on crash (window) | Throughput-critical |
| **Write-behind + WAL** | ~MStore speed | Zero data loss (WAL recovers) | **Recommended** |

**Recommendation: Write-behind + WAL.**

The WAL gives us the speed of write-behind with the safety of write-through. The only cost is one append per write (sequential I/O, fast).

### 2.4 Flush triggers

| Trigger | When | Latency impact |
|---------|------|----------------|
| **Size threshold** | dirtySet.size() > N (e.g., 1000) | Minimal (async) |
| **Time interval** | Every T ms (e.g., 100ms) | Minimal (async) |
| **Shutdown** | Explicit `close()` | Blocking (must complete) |
| **WAL size** | WAL exceeds W bytes | Minimal (async) |
| **Read miss** | Evicting dirty page | **Blocking** (must flush first) |

---

## 3. Crash Recovery

### 3.1 The problem

```
Timeline:
  set(key, val)  →  cache updated  →  crash  →  disk never updated
                                        ↑
                                   data lost
```

### 3.2 WAL (Write-Ahead Log) approach

**Principle:** Every write is appended to the WAL *before* the cache is updated. The WAL is the durable record.

```
WAL entry format:
┌────────┬──────┬─────────┬───────┬─────────┐
│ op(1B) │ len  │ key     │ value │ checksum│
└────────┴──────┴─────────┴───────┴─────────┘
```

**Startup recovery:**
```
recover():
    diskState = disk.loadAll()           // load from disk
    cache = LRU(diskState)               // populate cache
    wal.replay()                         // apply WAL entries on top
    for entry in wal:
        if entry.op == SET:
            cache.put(entry.key, entry.value)
        elif entry.op == KILL:
            cache.remove(entry.key)
    wal.truncate()                       // WAL now redundant
```

**Key insight:** The WAL is *append-only* and *sequential* — fast to write. The disk store is the checkpoint. Recovery replays WAL on top of disk state.

### 3.3 Recovery guarantees

| Scenario | Data loss? | Recovery |
|----------|-----------|----------|
| Crash before WAL write | **Yes** (that one write) | Lost |
| Crash after WAL write, before cache update | No | WAL replay |
| Crash after cache update, before flush | No | WAL replay |
| Crash during flush | No | WAL replay (flush is idempotent) |
| Crash after flush, before WAL truncate | No | WAL replay (idempotent) |
| Clean shutdown | No | Flush + truncate WAL |

**Maximum data loss window:** 1 write (the one being WAL'd when crash occurred). All previous writes are in WAL or disk.

### 3.4 WAL truncation

The WAL grows without bound if never truncated. Truncate after successful flush:

```
flush():
    disk.setBatch(dirtyPages)    // checkpoint
    wal.truncate(uptoSeq)       // remove entries already on disk
```

**WAL size bound:** At most `FLUSH_THRESHOLD` entries between flushes. With threshold=1000 and 100-byte entries, WAL is ~100KB. Bounded.

### 3.5 Checksums

Each WAL entry has a checksum. On replay, skip entries with bad checksums (torn writes from crash).

```
replay():
    for entry in wal:
        if entry.checksum != compute(entry):
            stop  // torn write, discard rest
        apply(entry)
```

---

## 4. LRU Cache Design

### 4.1 Eviction policy: Write-aware LRU

Standard LRU evicts the least recently used key. But dirty pages must be flushed before eviction:

```
evict():
    key = lru.removeOldest()
    if dirtySet.contains(key):
        flushKey(key)              // blocking write to disk
    cache.remove(key)
```

**Problem:** Evicting a dirty page causes a blocking disk write — latency spike.

**Solution:** Evict clean pages preferentially. Only evict dirty pages when no clean pages remain.

```
evict():
    for key in lru.fromOldest():
        if !dirtySet.contains(key):
            cache.remove(key)      // fast path
            return
    // All pages dirty — must flush one
    key = lru.removeOldest()
    flushKey(key)                  // blocking
    cache.remove(key)
```

### 4.2 Cache sizing

| Size | Hit rate (bank) | Hit rate (VistA) | Memory |
|------|----------------|------------------|--------|
| 100 keys | ~100% (bank: 60 keys) | ~0.3% | 10KB |
| 1,000 keys | 100% | ~3% | 100KB |
| 10,000 keys | 100% | ~30% | 1MB |
| 100,000 keys | 100% | ~30% | 10MB |
| All keys | 100% | 100% | ~100MB (33K routines) |

**Recommendation:** 10,000 keys default. Bank fits entirely. VistA gets ~30% hit rate.

### 4.3 Thread safety

```
ConcurrentHashMap<String, MValue> cache   // thread-safe reads
ConcurrentHashMap<String, Boolean> dirty  // thread-safe dirty tracking
ConcurrentLinkedDeque<String> lru         // thread-safe LRU order
```

All `get()`/`set()` are lock-free (ConcurrentHashMap). LRU updates use CAS or a dedicated lock.

---

## 5. Lifecycle

### 5.1 Startup

```
open(diskPath):
    disk = new H2Store(diskPath)          // or MapDBStore
    cache = new ConcurrentHashMap<>()
    dirty = new ConcurrentHashMap<>()
    lru = new ConcurrentLinkedDeque<>()
    wal = new WAL(walPath)

    // Load from disk (checkpoint)
    for entry in disk.scanAll():
        cache.put(entry.key, entry.value)
        lru.addLast(entry.key)

    // Replay WAL (recovery)
    for entry in wal.replay():
        cache.put(entry.key, entry.value)
        dirty.add(entry.key)
    wal.truncate()

    // Start background flusher
    flusher.start()
```

**Startup time:** 33K keys × ~2us = ~66ms for full load. Acceptable.

### 5.2 Runtime

```
get(key):
    val = cache.get(key)
    if val != null:
        lru.touch(key)              // move to front
        return val
    // Cache miss — read from disk
    val = disk.get(key)
    if val != null:
        putCache(key, val)          // may evict
    return val

set(key, value):
    cache.put(key, value)
    dirty.add(key)
    lru.touch(key)
    wal.append(SET, key, value)
    if dirty.size() >= THRESHOLD:
        triggerFlush()
```

### 5.3 Shutdown

```
close():
    flusher.stop()
    flush()                         // flush all dirty pages
    wal.truncate()                  // WAL now redundant
    disk.close()
```

**Shutdown time:** 1000 dirty pages × ~2us = ~2ms. Acceptable.

---

## 6. Metrics

### 6.1 Sustained throughput

```
Measure TPS every T seconds over duration D.
Report: mean, min, max, stddev, trend (slope).
```

**What to look for:**
- Gradual decline → memory leak, cache degradation
- Sudden drops → GC pauses, disk I/O spikes
- Oscillation → lock contention, flush cycles

### 6.2 Response time (latency percentiles)

```
For each transaction, record latency.
Report: p50, p95, p99, p999, max.
```

**What to look for:**
- p99 >> p50 → tail latency (cache misses, flush stalls)
- p999 spikes → eviction of dirty pages (blocking flush)

### 6.3 Transactional accuracy under load

```
Run concurrent transactions.
Verify invariants:
  - B(acct) - C(acct) = 2 * FEE(acct)  (bank double-entry)
  - Journal count = transaction count
  - No lost updates (read-modify-write atomicity)
  - No phantom reads ($ORDER consistency)
```

**What to look for:**
- Invariant violations → race conditions
- Lost updates → non-atomic read-modify-write
- Journal gaps → WAL/flush ordering issues

---

## 7. Open Questions

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | **WAL format** | **Binary** | Compact, fast, checksum-friendly |
| 2 | **WAL fsync** | **Batched** | Every N ms (100ms default). Speed + bounded loss. |
| 3 | **Flush granularity** | Batch | Single transaction for dirty set |
| 4 | **Cache eviction** | Write-aware LRU | Prefer clean pages, block only when necessary |
| 5 | **Cache size** | **10,000 keys** | Bank fits (60 keys). VistA ~30% hit rate. |
| 6 | **Disk store** | **H2Store** | Faster than MapDBStore in benchmarks |
| 7 | **WAL location** | Separate file | Independent of disk store |
| 8 | **Multi-instance** | Per-instance | Simpler, no shared cache coordination |

---

## 8. Estimated Performance

| Metric | MStore | H2Store | TieredStore (predicted) |
|--------|--------|---------|----------------------|
| Peak TPS | 32,400 | 19,000 | ~28,000 |
| Sustained TPS | 32,400 | 19,000 | ~25,000 |
| p50 latency | 0.4 us | 1.8 us | 0.5 us (hit) |
| p99 latency | 0.4 us | 1.8 us | 3.0 us (miss/evict) |
| Persistence | ❌ | ✅ | ✅ |
| Crash safety | ❌ | ✅ | ✅ (WAL) |
| Memory (10K keys) | 1MB | 0 | 1MB |
| Startup time | 0ms | 0ms | ~66ms (33K keys) |

**Key assumption:** 95%+ cache hit rate for bank workload (60 keys in 10K cache).
For VistA (33K keys, 10K cache), ~30% hit rate — closer to H2Store speed.

---

## 9. Implementation Phases

| Phase | What | Deliverable |
|-------|------|-------------|
| **1. Metrics harness** | Time-series TPS, latency percentiles, invariant checker | `PerfMetrics.java` |
| **2. WAL** | Append-only log with checksums | `WriteAheadLog.java` |
| **3. TieredStore skeleton** | LRU cache + H2Store backing | `TieredStore.java` |
| **4. Write-behind** | Background flusher, dirty tracking | `BackgroundFlusher.java` |
| **5. Crash recovery** | WAL replay on startup | Integrated into TieredStore |
| **6. Benchmark** | 3 metrics × 3 stores (MStore, H2Store, TieredStore) | Benchmark suite |

**Estimated effort:** Phase 1-2 = 1 day. Phase 3-5 = 2-3 days. Phase 6 = 1 day.
