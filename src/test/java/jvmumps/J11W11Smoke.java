package jvmumps;
import java.io.File;
public class J11W11Smoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("j11w", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    st.lockAcquire("^OLD", "dead", 10);
    int swept = st.lockSweep(0); // ttl 0 = expire immediately
    if (swept < 1) throw new IllegalStateException("sweep "+swept);
    if (!st.lockAcquire("^OLD", "now", 20)) throw new IllegalStateException("after sweep");
    st.lockRelease("^OLD", "now");
    LineInterp m = new LineInterp(st);
    // W11: pattern classes + counts edge
    String[] cs = {
      "W (\"Hello World\"?1A.AN.1\" \".AN),!", "1\n",
      "W (\"12345\"?5N),!", "1\n",
      "W (\"12\"?3N),!", "0\n",
      "W (\"a\"?1.A),!", "1\n",
    };
    for (int i=0;i<cs.length;i+=2) {
      String got=m.run(cs[i]);
      if (!cs[i+1].equals(got)) throw new IllegalStateException(cs[i]+" got "+got);
    }
    System.out.println("J11 TTL + W11 PATTERNS OK");
    st.close();
  }
}
