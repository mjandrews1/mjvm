package jvmumps;

import java.io.File;

/** Full engine path over live SQLite store. */
public final class SqlEngineSmoke {
  public static void main(String[] args) throws Exception {
    File db = File.createTempFile("sqle", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String out = m.run("S ^G(1)=\"a\" S ^G(2,1)=\"c\" W ^G(1),\" \",^G(2,1),!");
    if (!"a c\n".equals(out)) {
      throw new IllegalStateException("read/write out=" + out);
    }
    out = m.run("W $D(^G(1)),\" \",$D(^G(2)),!");
    if (!"1 10\n".equals(out)) {
      throw new IllegalStateException("data out=" + out);
    }
    out = m.run("K ^G(1) W $D(^G(1)),\" \",$D(^G(2,1)),!");
    if (!"0 1\n".equals(out)) {
      throw new IllegalStateException("kill out=" + out);
    }
    // order via $O
    out = m.run("W $O(^G(\"\")),!");
    if (out.trim().isEmpty()) {
      throw new IllegalStateException("order empty");
    }
    st.close();
    System.out.println("SQL ENGINE PATH OK");
  }
}
