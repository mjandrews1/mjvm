package jvmumps.store;

import java.io.File;

/**
 * Store metrics: throughput, latency, cache hit rate, WAL size.
 * Disk space monitoring with configurable warn/fail thresholds.
 */
public final class StoreMetrics {

    // Throughput
    private long reads;
    private long writes;
    private long cacheHits;
    private long cacheMisses;
    private long evictions;
    private long walAppends;
    private long walFlushes;

    // Latency (nanoseconds)
    private long readNs;
    private long writeNs;

    // Disk monitoring
    private final long warnBytes;   // warn when free < this (default 2 GB)
    private final long failBytes;   // fail when free < this (default 500 MB)
    private boolean diskWarned;

    public StoreMetrics() {
        this(2L * 1024 * 1024 * 1024, 500L * 1024 * 1024);
    }

    public StoreMetrics(long warnBytes, long failBytes) {
        this.warnBytes = warnBytes;
        this.failBytes = failBytes;
    }

    // Recording

    public void recordRead(long ns) {
        reads++;
        readNs += ns;
    }

    public void recordWrite(long ns) {
        writes++;
        writeNs += ns;
    }

    public void recordCacheHit()  { cacheHits++; }
    public void recordCacheMiss() { cacheMisses++; }
    public void recordEviction()  { evictions++; }
    public void recordWalAppend() { walAppends++; }
    public void recordWalFlush()  { walFlushes++; }

    // Disk monitoring

    /**
     * Check disk space on the path's filesystem.
     * @return "OK", "WARN", or "FAIL"
     */
    public String checkDisk(String path) {
        File f = new File(path);
        long free = f.getUsableSpace();
        if (free < failBytes) {
            return "FAIL";
        }
        if (free < warnBytes) {
            diskWarned = true;
            return "WARN";
        }
        return "OK";
    }

    public long diskFreeBytes(String path) {
        return new File(path).getUsableSpace();
    }

    public double diskFreePercent(String path) {
        File f = new File(path);
        long total = f.getTotalSpace();
        if (total == 0) return 0;
        return 100.0 * f.getUsableSpace() / total;
    }

    // Accessors

    public long reads()         { return reads; }
    public long writes()        { return writes; }
    public long cacheHits()     { return cacheHits; }
    public long cacheMisses()   { return cacheMisses; }
    public long evictions()     { return evictions; }
    public long walAppends()    { return walAppends; }
    public long walFlushes()    { return walFlushes; }

    public double cacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0 : 100.0 * cacheHits / total;
    }

    public double avgReadUs() {
        return reads == 0 ? 0 : readNs / 1000.0 / reads;
    }

    public double avgWriteUs() {
        return writes == 0 ? 0 : writeNs / 1000.0 / writes;
    }

    public boolean diskWarned() { return diskWarned; }

    /** Snapshot as string for logging. */
    public String snapshot() {
        return String.format(
            "reads=%d writes=%d cacheHit=%.1f%% evictions=%d " +
            "walAppends=%d walFlushes=%d avgRead=%.1fus avgWrite=%.1fus",
            reads, writes, cacheHitRate(), evictions,
            walAppends, walFlushes, avgReadUs(), avgWriteUs());
    }
}
