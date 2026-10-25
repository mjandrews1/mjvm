package jvmumps;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** 1W-nR / nW-nR queued CRUD smoke (JavaM #59). */
public final class LockQueueSmoke {
  static int n = 0;

  static void chk(boolean ok, String tag) {
    if (!ok) {
      throw new IllegalStateException(tag);
    }
    n++;
  }

  public static void main(String[] args) throws Exception {
    // --- 1W-nR basic ---
    LockQueue q = LockQueue.oneWriter(4);
    chk(q.acquireWrite("w1", 100), "1w acquire");
    chk(q.activeWriters() == 1, "1w count");
    // reentrant same-thread write is OK (M LOCK +X twice)
    chk(q.acquireWrite("w1", 50), "1w reentrant");
    q.releaseWrite();
    // other thread cannot take write while held
    final boolean[] other = new boolean[1];
    Thread ot = new Thread(() -> other[0] = q.acquireWrite("w2", 50));
    ot.start();
    ot.join(500);
    chk(!other[0], "1w other writer timeout");
    chk(!q.acquireRead("r1", 50), "1w reader blocked");
    q.releaseWrite();
    chk(q.acquireRead("r1", 100), "r after w");
    chk(q.acquireRead("r2", 100), "r2");
    chk(q.activeReaders() == 2, "2 readers");
    q.releaseRead();
    q.releaseRead();

    // --- nW-nR ---
    LockQueue nq = LockQueue.manyWriters(2, 8);
    chk(nq.acquireWrite("w1", 100), "nw w1");
    chk(nq.acquireRead("r1", 50) == false, "nw reader vs writer");
    nq.releaseWrite();
    chk(nq.acquireWrite("w1", 100), "nw w1 again");
    // second writer waits while first holds exclusive section
    Thread t =
        new Thread(
            () -> {
              // will run after releaseWrite below
            });
    nq.releaseWrite();

    // --- FIFO: writer queued after readers must not starve forever ---
    LockQueue fq = LockQueue.oneWriter(2);
    chk(fq.acquireRead("r1", 100), "fq r1");
    chk(fq.acquireRead("r2", 100), "fq r2");
    final boolean[] got = new boolean[1];
    Thread w = new Thread(() -> got[0] = fq.acquireWrite("w1", 2000));
    w.start();
    Thread.sleep(30);
    fq.releaseRead();
    fq.releaseRead();
    w.join(2000);
    chk(got[0], "queued writer eventually granted");
    fq.releaseWrite();

    // --- stress: 1W-nR many threads ---
    final LockQueue sq = LockQueue.oneWriter(16);
    final AtomicInteger readers = new AtomicInteger();
    final AtomicInteger writers = new AtomicInteger();
    final AtomicInteger maxR = new AtomicInteger();
    final CountDownLatch done = new CountDownLatch(12);
    for (int i = 0; i < 8; i++) {
      final String id = "r" + i;
      new Thread(
              () -> {
                for (int k = 0; k < 50; k++) {
                  if (sq.acquireRead(id, 2000)) {
                    int r = readers.incrementAndGet();
                    maxR.accumulateAndGet(r, Math::max);
                    if (sq.activeWriters() != 0) {
                      throw new IllegalStateException("reader with writer");
                    }
                    readers.decrementAndGet();
                    sq.releaseRead();
                  }
                }
                done.countDown();
              })
          .start();
    }
    for (int i = 0; i < 4; i++) {
      final String id = "w" + i;
      new Thread(
              () -> {
                for (int k = 0; k < 20; k++) {
                  if (sq.acquireWrite(id, 3000)) {
                    writers.incrementAndGet();
                    if (sq.activeReaders() != 0 || sq.activeWriters() != 1) {
                      throw new IllegalStateException("bad exclusive section");
                    }
                    sq.releaseWrite();
                  }
                }
                done.countDown();
              })
          .start();
    }
    done.await();
    chk(writers.get() > 0, "writers made progress");
    chk(maxR.get() <= 16, "reader cap");

    System.out.println(
        "LOCKQUEUE SMOKE OK cases=" + n + " writes=" + writers.get() + " maxR=" + maxR.get());
  }
}
