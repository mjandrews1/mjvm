package jvmumps;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * J7 store persistence — SQLite peer of PyMumps W5. Table: nodes(name, subs, val, isnum) where subs
 * is a JSON-ish array string.
 */
/** Shared SQLite helpers for {@link SqlStore}. */
public final class StoreDb {
  static final String URL_PREFIX = "jdbc:sqlite:";

  static void ensureDriver() throws Exception {
    Class.forName("org.sqlite.JDBC");
  }

  public static void save(MStore store, String path) throws Exception {
    ensureDriver();
    try (Connection c = DriverManager.getConnection(URL_PREFIX + path)) {
      try (Statement st = c.createStatement()) {
        st.execute(
            "CREATE TABLE IF NOT EXISTS nodes("
                + "name TEXT NOT NULL,"
                + "subs TEXT NOT NULL,"
                + "val TEXT NOT NULL,"
                + "isnum INTEGER NOT NULL,"
                + "PRIMARY KEY(name, subs))");
        st.execute("DELETE FROM nodes");
      }
      try (PreparedStatement ins =
          c.prepareStatement("INSERT OR REPLACE INTO nodes VALUES (?,?,?,?)")) {
        for (String[] row : store.dump()) {
          ins.setString(1, row[0]);
          ins.setString(2, row[1]);
          ins.setString(3, row[2]);
          ins.setInt(4, Integer.parseInt(row[3]));
          ins.addBatch();
        }
        ins.executeBatch();
      }
    }
  }

  public static int load(MStore store, String path) throws Exception {
    File f = new File(path);
    if (!f.exists()) {
      return 0;
    }
    ensureDriver();
    int n = 0;
    try (Connection c = DriverManager.getConnection(URL_PREFIX + path);
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT name, subs, val, isnum FROM nodes")) {
      while (rs.next()) {
        store.putRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4));
        n++;
      }
    }
    return n;
  }
}
