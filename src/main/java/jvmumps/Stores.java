package jvmumps;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Store factory for mJVM / mesh plane.
 *
 * <ul>
 *   <li>{@code memory} — {@link MStore} (dev/test/cache, no persistence)
 *   <li>{@code tiered} — {@code TieredStore} LRU cache + H2Store + WAL (default for persistence)
 *   <li>{@code h2} — {@link H2Store} (single-node persistence)
 *   <li>{@code mapdb} — {@link MapDBStore} (single-node persistence)
 *   <li>{@code sqlite} — {@link SqlStore} (CI/conformance only — rejected in production)
 *   <li>{@code cdb2} — {@link Cdb2Store} deprecated (infrastructure frozen)
 * </ul>
 *
 * <b>Production guard (#201):</b> when {@code MJVM_PROD=1}, SqlStore is rejected. Production
 * engines are MStore (cache) + Cdb2Store (persistence/distribution).
 *
 * <p>Cdb2 transport is {@code tools/cdb2pipe} (persistent cdb2api). Selection: {@code --store cdb2}
 * or env {@code MJVM_STORE=cdb2}. Pipe/db/type from {@code CDB2PIPE} (default {@code
 * tools/cdb2pipe}), {@code CDB2DB} (default {@code testdb}), {@code CDB2TYPE} (default {@code
 * local}).
 */
@SuppressWarnings("deprecation")
public final class Stores {
  private Stores() {}

  /** True when running in production mode (SqlStore rejected). */
  public static boolean isProduction() {
    return "1".equals(System.getenv("MJVM_PROD"))
        || "production".equalsIgnoreCase(System.getProperty("mjvm.env", ""));
  }

  public static StoreApi open(String kind, String dbFile) throws Exception {
    String k = kind;
    if (k == null || k.isEmpty()) {
      k = System.getenv().getOrDefault("MJVM_STORE", "");
    }
    if (k == null || k.isEmpty()) {
      if (dbFile == null) {
        k = "memory";
      } else if (Files.exists(Path.of(dbFile)) && Files.size(Path.of(dbFile)) > 0) {
        // Existing file — detect type by extension (.dat = legacy sqlite, deprecated #33)
        k = dbFile.endsWith(".mv.db") ? "tiered" : "sqlite";
        if (k.equals("sqlite")) {
          System.err.println(
              "mjvm: existing .dat volume is legacy sqlite (deprecated #33); use tiered.");
        }
      } else {
        k = "tiered"; // new file → tiered store
      }
    }
    switch (k.toLowerCase(java.util.Locale.ROOT)) {
      case "memory", "mem", "mstore" -> {
        return new MStore();
      }
      case "sqlite", "sql", "sqlstore" -> {
        // DEPRECATED (#33) + CI-only (#201): never for new production use; mjvm+sqlite
        // were never deployed, so no migration surface exists.
        System.err.println(
            "mjvm: --store sqlite is DEPRECATED (#33) and CI-only (#201); use tiered.");
        if (isProduction()) {
          throw new MError(
              "SqlStore is CI-only and rejected in production (#201). "
                  + "Use cdb2 for persistence or memory for cache. "
                  + "Unset MJVM_PROD / mjvm.env to override.");
        }
        String path = dbFile != null ? dbFile : "mjvm.dat";
        return new SqlStore(path);
      }
      case "tiered", "tieredstore" -> {
        String diskPath = dbFile != null ? dbFile : "mjvm.mv.db";
        String walPath = diskPath + ".wal";
        return new jvmumps.store.TieredStore(diskPath, walPath, 10000, 100);
      }
      case "h2", "h2store", "mvstore" -> {
        // DEPRECATED (#33): superseded by tiered for single-node persistence.
        // Removal target: next major version. h2.jar stays load-bearing —
        // TieredStore uses H2Store as its Dafny-verified disk layer.
        System.err.println(
            "mjvm: --store h2 is DEPRECATED (#33); use tiered (default) for persistence.");
        String path = dbFile != null ? dbFile : "mjvm.mv.db";
        return new H2Store(path);
      }
      case "mapdb", "mapdbstore" -> {
        // DEPRECATED (#33): superseded by tiered. Removal target: next major version.
        System.err.println(
            "mjvm: --store mapdb is DEPRECATED (#33); use tiered (default) for persistence.");
        String path = dbFile != null ? dbFile : "mjvm.mapdb";
        return new MapDBStore(path);
      }
      case "cdb2", "comdb2", "cdb2store" -> {
        @SuppressWarnings("deprecation")
        String pipe = System.getenv().getOrDefault("CDB2PIPE", "tools/cdb2pipe");
        String db = System.getenv().getOrDefault("CDB2DB", "testdb");
        String type = System.getenv().getOrDefault("CDB2TYPE", "local");
        return new Cdb2Store(pipe, db, type);
      }
      default -> throw new MError("unknown store kind: " + kind);
    }
  }

  /** Human label for banners. */
  public static String describe(StoreApi s) {
    if (s instanceof Cdb2Store) return "cdb2 (grouped)";
    if (s instanceof SqlStore) return "sqlite";
    if (s instanceof MStore) return "memory";
    return s.getClass().getSimpleName();
  }
}
