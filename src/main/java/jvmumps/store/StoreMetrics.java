package jvmumps.store;

import java.io.File;

/**
 * Store metrics: throughput, latency, cache hit rate, WAL size. Disk space monitoring with
 * configurable warn/fail thresholds.
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

  // Latency histogram for p50/p95/p99 percentiles
  // Logarithmic buckets: 1ns to 1 second, ~1.2x per bucket
  private static final int HIST_BUCKETS = 64;
  private static final long HIST_BASE_NS = 1; // 1ns minimum
  private final long[] readHist = new long[HIST_BUCKETS];
  private final long[] writeHist = new long[HIST_BUCKETS];

  /** Map nanoseconds to histogram bucket index (logarithmic). */
  private static int histBucket(long ns) {
    if (ns <= 0) return 0;
    // log2(ns) gives bucket index — 1ns=0, 2ns=1, 4ns=2, ..., up to 2^63
    int idx = 63 - Long.numberOfLeadingZeros(ns);
    return Math.min(idx, HIST_BUCKETS - 1);
  }

  /** Get percentile from histogram (0.0-1.0). Returns nanoseconds. */
  private static long histPercentile(long[] hist, double percentile) {
    long total = 0;
    for (long c : hist) total += c;
    if (total == 0) return 0;
    long target = (long) (total * percentile);
    long cumulative = 0;
    for (int i = 0; i < HIST_BUCKETS; i++) {
      cumulative += hist[i];
      if (cumulative >= target) {
        return HIST_BASE_NS << i; // bucket center (power of 2)
      }
    }
    return HIST_BASE_NS << (HIST_BUCKETS - 1);
  }

  // Disk monitoring
  private final long warnBytes; // warn when free < this (default 2 GB)
  private final long failBytes; // fail when free < this (default 500 MB)
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
    readHist[histBucket(ns)]++;
  }

  public void recordWrite(long ns) {
    writes++;
    writeNs += ns;
    writeHist[histBucket(ns)]++;
  }

  public void recordCacheHit() {
    cacheHits++;
  }

  public void recordCacheMiss() {
    cacheMisses++;
  }

  public void recordEviction() {
    evictions++;
  }

  public void recordWalAppend() {
    walAppends++;
  }

  public void recordWalFlush() {
    walFlushes++;
  }

  // Disk monitoring

  /**
   * Check disk space on the path's filesystem.
   *
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

  public long reads() {
    return reads;
  }

  public long writes() {
    return writes;
  }

  public long cacheHits() {
    return cacheHits;
  }

  public long cacheMisses() {
    return cacheMisses;
  }

  public long evictions() {
    return evictions;
  }

  public long walAppends() {
    return walAppends;
  }

  public long walFlushes() {
    return walFlushes;
  }

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

  public boolean diskWarned() {
    return diskWarned;
  }

  // Latency percentiles (returns microseconds)

  public double readP50Us() {
    return histPercentile(readHist, 0.50) / 1000.0;
  }

  public double readP95Us() {
    return histPercentile(readHist, 0.95) / 1000.0;
  }

  public double readP99Us() {
    return histPercentile(readHist, 0.99) / 1000.0;
  }

  public double writeP50Us() {
    return histPercentile(writeHist, 0.50) / 1000.0;
  }

  public double writeP95Us() {
    return histPercentile(writeHist, 0.95) / 1000.0;
  }

  public double writeP99Us() {
    return histPercentile(writeHist, 0.99) / 1000.0;
  }

  /** Full latency report as string. */
  public String latencyReport() {
    return String.format(
        "read p50=%.1fus p95=%.1fus p99=%.1fus | write p50=%.1fus p95=%.1fus p99=%.1fus",
        readP50Us(), readP95Us(), readP99Us(), writeP50Us(), writeP95Us(), writeP99Us());
  }

  /** Snapshot as string for logging. */
  public String snapshot() {
    return String.format(
        "reads=%d writes=%d cacheHit=%.1f%% evictions=%d "
            + "walAppends=%d walFlushes=%d\n  "
            + "avg: read=%.1fus write=%.1fus | "
            + "p50: read=%.1fus write=%.1fus | "
            + "p95: read=%.1fus write=%.1fus | "
            + "p99: read=%.1fus write=%.1fus",
        reads,
        writes,
        cacheHitRate(),
        evictions,
        walAppends,
        walFlushes,
        avgReadUs(),
        avgWriteUs(),
        readP50Us(),
        writeP50Us(),
        readP95Us(),
        writeP95Us(),
        readP99Us(),
        writeP99Us());
  }
}
