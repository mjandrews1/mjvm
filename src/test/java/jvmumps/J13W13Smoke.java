package jvmumps;

import java.io.File;

public class J13W13Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j13w", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    st.lockAcquire("^T", "p", 30);
    LineInterp m = new LineInterp(st);
    m.run("J \"W \\\"ok\\\"\"");
    Thread.sleep(150);
    int swept = st.lockSweep(0);
    if (!st.lockAcquire("^T", "q", 20)) throw new IllegalStateException("after ttl");
    LineInterp p = new LineInterp(st);
    String[] cs = {
      "W (\"Hello\"?1\"Hello\"),!", "1\n",
      "W (\"Hello\"?1\"Hell\"),!", "0\n",
      "W (\"abc\"?.1A.1A.1A),!", "1\n",
      "W (\"\"?.A),!", "1\n",
      "W (\"1\"?1N),!", "1\n",
    };
    for (int i = 0; i < cs.length; i += 2) {
      String got = p.run(cs[i]);
      if (!cs[i + 1].equals(got)) throw new IllegalStateException(cs[i] + " got " + got);
    }
    System.out.println("J13 JOB+TTL + W13 PATTERNS OK swept=" + swept);
    st.close();
  }
}
