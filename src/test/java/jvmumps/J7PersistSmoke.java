package jvmumps;

import java.io.File;

/** J7: SQLite store persistence round-trip. */
public final class J7PersistSmoke {
  public static void main(String[] args) throws Exception {
    File db = File.createTempFile("j7p", ".db");
    db.deleteOnExit();
    String path = db.getAbsolutePath();
    LineInterp m = new LineInterp();
    m.run("S ^G(1)=\"a\" S ^G(2,1)=\"c\" S X=42");
    // use interpreter store via new API on MStore directly
    MStore st = new MStore();
    st.putRow("^G", "[\"1\"]", "a", 0);
    st.putRow("^G", "[\"2\",\"1\"]", "c", 0);
    st.putRow("X", "[]", "42", 1);
    StoreDb.save(st, path);

    MStore st2 = new MStore();
    int n = StoreDb.load(st2, path);
    if (n < 3) {
      throw new IllegalStateException("load count " + n);
    }
    java.util.List<String[]> rows = st2.dump();
    boolean saw = false;
    for (String[] r : rows) {
      if (r[0].equals("^G") && r[1].equals("[\"2\",\"1\"]") && r[2].equals("c")) {
        saw = true;
      }
    }
    if (!saw) {
      throw new IllegalStateException("missing node after reload rows=" + rows.size());
    }
    // second save overwrites cleanly
    StoreDb.save(st2, path);
    int n2 = StoreDb.load(new MStore(), path);
    if (n2 != n) {
      throw new IllegalStateException("roundtrip count " + n + " vs " + n2);
    }
    System.out.println("J7 PERSIST OK rows=" + n);
  }
}
