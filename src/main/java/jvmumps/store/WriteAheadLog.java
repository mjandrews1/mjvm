package jvmumps.store;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.CRC32;

/**
 * Binary Write-Ahead Log with batched fsync.
 *
 * <p>Every store mutation is appended to the WAL before the cache is updated. On crash, WAL replay
 * recovers all committed writes. Batched fsync bounds the data loss window to the fsync interval
 * (default 100ms).
 *
 * <p>Entry format (binary): magic(4B) | seq(8B) | op(1B) | keyLen(2B) | valLen(4B) | key | val |
 * crc(4B)
 *
 * <p>Thread-safe: appends are synchronized. Replay is single-threaded.
 */
public final class WriteAheadLog implements Closeable {

  private static final int MAGIC = 0x57414C44; // "WALD"
  private static final int HEADER_SIZE = 4 + 8 + 1 + 2 + 4; // magic+seq+op+keyLen+valLen
  private static final int CRC_SIZE = 4;

  /** Operations. */
  public static final byte OP_SET = 1;

  public static final byte OP_KILL = 2;

  private final Path walPath;
  private long nextSeq = 1;
  private final Object lock = new Object();
  private long lastFsync = System.currentTimeMillis();
  private final long fsyncIntervalMs;

  /** Write buffer — batch appends, flush to channel periodically. */
  private final ByteArrayOutputStream writeBuffer = new ByteArrayOutputStream();

  private static final int BUFFER_FLUSH_SIZE = 8192; // 8KB

  private final FileChannel channel;
  private final java.nio.channels.FileLock fileLock;

  public WriteAheadLog(String path, long fsyncIntervalMs) throws IOException {
    this.walPath = Path.of(path);
    this.fsyncIntervalMs = fsyncIntervalMs;
    // Create parent dir if needed
    Path parent = walPath.getParent();
    if (parent != null) Files.createDirectories(parent);
    this.channel =
        FileChannel.open(
            walPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND);
    // Acquire exclusive flock — prevents multi-process corruption
    try {
      this.fileLock = channel.lock();
    } catch (java.nio.channels.OverlappingFileLockException e) {
      throw new IOException("WAL file is locked by another process: " + path);
    }
    // Scan existing entries to find next sequence number
    this.nextSeq = scanNextSeq() + 1;
  }

  /** Append a SET entry. Returns sequence number. */
  public long appendSet(String key, String value) throws IOException {
    return append(OP_SET, key, value);
  }

  /** Append a KILL entry. Returns sequence number. */
  public long appendKill(String key) throws IOException {
    return append(OP_KILL, key, "");
  }

  private long append(byte op, String key, String value) throws IOException {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length > 65535) throw new IOException("WAL key too long: " + keyBytes.length);
    // value length is int, always <= Integer.MAX_VALUE

    // Build entry
    int entrySize = HEADER_SIZE + keyBytes.length + valBytes.length + CRC_SIZE;
    ByteBuffer buf = ByteBuffer.allocate(entrySize);
    long seq;
    synchronized (lock) {
      seq = nextSeq++;
      buf.putInt(MAGIC);
      buf.putLong(seq);
      buf.put(op);
      buf.putShort((short) keyBytes.length);
      buf.putInt(valBytes.length);
      buf.put(keyBytes);
      buf.put(valBytes);
      // CRC over everything except the CRC field itself
      CRC32 crc = new CRC32();
      crc.update(buf.array(), 0, buf.position());
      buf.putInt((int) crc.getValue());

      // Buffer write (batched)
      writeBuffer.write(buf.array(), 0, buf.position());
    }

    // Batched fsync (handles buffer flush on timer)
    maybeFsync();
    return seq;
  }

  /** Flush write buffer to channel. Called from maybeFsync (timer-based). */
  private void flushBuffer() throws IOException {
    synchronized (lock) {
      if (writeBuffer.size() > 0) {
        channel.write(ByteBuffer.wrap(writeBuffer.toByteArray()));
        writeBuffer.reset();
      }
    }
  }

  /** Batched fsync — flush buffer + sync if interval elapsed. */
  private void maybeFsync() throws IOException {
    long now = System.currentTimeMillis();
    synchronized (lock) {
      if (now - lastFsync >= fsyncIntervalMs) {
        flushBuffer();
        channel.force(false);
        lastFsync = now;
      }
    }
  }

  /** Force fsync now (for shutdown). Flushes buffer first. */
  public void fsync() throws IOException {
    synchronized (lock) {
      if (writeBuffer.size() > 0) {
        channel.write(ByteBuffer.wrap(writeBuffer.toByteArray()));
        writeBuffer.reset();
      }
      channel.force(false);
      lastFsync = System.currentTimeMillis();
    }
  }

  /**
   * Replay all valid entries. Returns entries in order. Stops at first bad CRC (torn write from
   * crash).
   */
  public java.util.List<Entry> replay() throws IOException {
    java.util.List<Entry> entries = new java.util.ArrayList<>();
    try (FileChannel readCh = FileChannel.open(walPath, StandardOpenOption.READ)) {
      ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
      while (readCh.read(header) == HEADER_SIZE) {
        header.flip();
        int magic = header.getInt();
        if (magic != MAGIC) break; // corruption
        long seq = header.getLong();
        byte op = header.get();
        int keyLen = Short.toUnsignedInt(header.getShort());
        int valLen = header.getInt();

        // Read key + value + crc
        int payloadSize = keyLen + valLen + CRC_SIZE;
        ByteBuffer payload = ByteBuffer.allocate(payloadSize);
        if (readCh.read(payload) != payloadSize) break; // truncated
        payload.flip();

        byte[] keyBytes = new byte[keyLen];
        payload.get(keyBytes);
        byte[] valBytes = new byte[valLen];
        payload.get(valBytes);
        int storedCrc = payload.getInt();

        // Verify CRC
        CRC32 crc = new CRC32();
        crc.update(header.array(), 0, HEADER_SIZE);
        crc.update(keyBytes);
        crc.update(valBytes);
        if ((int) crc.getValue() != storedCrc) break; // torn write

        entries.add(
            new Entry(
                seq,
                op,
                new String(keyBytes, StandardCharsets.UTF_8),
                new String(valBytes, StandardCharsets.UTF_8)));
        header.clear();
      }
    }
    return entries;
  }

  /** Truncate WAL (all entries now on disk). */
  public void truncate() throws IOException {
    synchronized (lock) {
      channel.truncate(0);
      channel.force(false);
      lastFsync = System.currentTimeMillis();
    }
  }

  /** Scan WAL to find highest sequence number. */
  private long scanNextSeq() throws IOException {
    if (!Files.exists(walPath)) return 0;
    long maxSeq = 0;
    try (FileChannel readCh = FileChannel.open(walPath, StandardOpenOption.READ)) {
      ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
      while (readCh.read(header) == HEADER_SIZE) {
        header.flip();
        int magic = header.getInt();
        if (magic != MAGIC) break;
        long seq = header.getLong();
        if (seq > maxSeq) maxSeq = seq;
        byte op = header.get();
        int keyLen = Short.toUnsignedInt(header.getShort());
        int valLen = header.getInt();
        // Skip payload
        readCh.position(readCh.position() + keyLen + valLen + CRC_SIZE);
        header.clear();
      }
    }
    return maxSeq;
  }

  @Override
  public void close() throws IOException {
    fsync();
    if (fileLock != null && fileLock.isValid()) fileLock.release();
    channel.close();
  }

  /** WAL entry. */
  public static final class Entry {
    public final long seq;
    public final byte op;
    public final String key;
    public final String value;

    Entry(long seq, byte op, String key, String value) {
      this.seq = seq;
      this.op = op;
      this.key = key;
      this.value = value;
    }
  }
}
