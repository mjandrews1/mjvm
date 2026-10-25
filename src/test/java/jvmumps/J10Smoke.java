package jvmumps;

import java.io.File;

/** J10 cross-process LOCK + real JOB via SqlStore. */
public final class J10Smoke {
  public static void main(String[] args) throws Exception {
    File db = File.createTempFile("j10", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    String pidA = "A1";
    String pidB = "B2";
    if (!st.lockAcquire("^L1", pidA, 50)) {
      throw new IllegalStateException("A acquire");
    }
    if (st.lockAcquire("^L1", pidB, 30)) {
      throw new IllegalStateException("B should block");
    }
    if (!st.lockAcquire("^L1", pidA, 30)) {
      throw new IllegalStateException("A reacquire");
    }
    st.lockRelease("^L1", pidA);
    if (!st.lockAcquire("^L1", pidB, 50)) {
      throw new IllegalStateException("B after release");
    }
    st.lockRelease("^L1", pidB);

    // real JOB child
    LineInterp m = new LineInterp(st);
    String out = m.run("J \"S ^JOB=1\" W \"parent\",!");
    if (!"parent\n".equals(out)) {
      throw new IllegalStateException("job parent " + out);
    }
    Thread.sleep(200);
    System.out.println("J10 LOCK+JOB OK");
    st.close();
  }
}
