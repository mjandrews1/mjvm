package mjvm.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jvmumps.SqlStore;
import jvmumps.StoreApi;
import jvmumps.Stores;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * mJVM CLI (M7) — drop-in shaped like {@code rsm} (Reference Standard M).
 *
 * <p>Flags mirror rsm -h: mjvm -V short version mjvm -h help menu mjvm -i [db] environment info
 * mjvm -v vol … create database volume mjvm -j jobs [db] initialize and start environment mjvm [ -x
 * cmds ] start in direct mode mjvm -k [db] stop / shut down
 *
 * <p>Extended (mJVM): mjvm --class -c '…' compile to .class then run (M2) mjvm file.m run a source
 * file mjvm --store cdb2|sqlite|memory pick StoreApi backend (mesh/SQL plane = cdb2)
 */
public final class Mjvm {
  public static final String VERSION = "V2.2.2 (mJVM)";
  public static final String BANNER =
      "mJVM "
          + VERSION
          + " — JVM-native M (JvMumps StoreApi)\n"
          + "Drop-in CLI shaped like Reference Standard M (rsm)\n";

  public static void main(String[] args) throws Exception {
    int rc = run(args);
    if (rc != 0) System.exit(rc);
  }

  public static int run(String[] args) throws Exception {
    boolean useClass = false;
    boolean restricted = false;
    String code = null;
    String file = null;
    String envName = "manager";
    String volume = null;
    String dbFile = null;
    String storeKind = null;
    Integer maxJobs = null;
    Integer blockSize = null;
    Integer dbSize = null;
    Integer mapSize = null;
    Integer gbuf = null;
    Integer rbuf = null;
    String mode = null; // create|start|kill|info|direct

    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      switch (a) {
        case "--store" -> {
          storeKind = args[++i];
        }
        case "-V" -> {
          System.out.println(VERSION);
          return 0;
        }
        case "-h", "-help", "--help" -> {
          printHelp();
          return 0;
        }
        case "-i" -> {
          mode = "info";
          if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
            dbFile = args[++i];
          }
        }
        case "-v" -> {
          mode = "create";
          volume = args[++i];
        }
        case "-j" -> {
          if (mode == null) mode = "start";
          maxJobs = Integer.parseInt(args[++i]);
        }
        case "-k" -> {
          mode = "kill";
          if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
            dbFile = args[++i];
          }
        }
        case "-b" -> blockSize = Integer.parseInt(args[++i]);
        case "-s" -> dbSize = Integer.parseInt(args[++i]);
        case "-m" -> mapSize = Integer.parseInt(args[++i]);
        case "-e" -> envName = args[++i];
        case "-g" -> gbuf = Integer.parseInt(args[++i]);
        case "-r" -> rbuf = Integer.parseInt(args[++i]);
        case "-x" -> code = args[++i];
        case "-R" -> restricted = true;
        case "--class" -> useClass = true;
        case "-c" -> code = args[++i];
        default -> {
          if (a.startsWith("-")) {
            System.err.println("mjvm: unknown option " + a);
            return 2;
          }
          if (file == null && dbFile == null) {
            // bare filename: db if .dat-like else source
            if (a.endsWith(".m") || a.endsWith(".mps")) {
              file = a;
            } else {
              dbFile = a;
              if (mode == null) mode = "direct";
            }
          } else if (file == null) {
            file = a;
          }
        }
      }
    }

    if (mode == null) {
      mode = (code != null || file != null) ? "run" : "direct";
    }
    // rsm: RSM_DBFILE (we also accept MJVM_DBFILE)
    if (dbFile == null) {
      String env = System.getenv("MJVM_DBFILE");
      if (env == null || env.isEmpty()) env = System.getenv("RSM_DBFILE");
      if (env != null && !env.isEmpty()) dbFile = env;
    }

    return switch (mode) {
      case "info" -> doInfo(dbFile);
      case "create" -> doCreate(volume, blockSize, dbSize, mapSize, envName, dbFile);
      case "start" -> doStart(maxJobs == null ? 16 : maxJobs, gbuf, rbuf, dbFile, envName);
      case "kill" -> doKill(dbFile);
      case "run" -> doRun(code, file, useClass, dbFile, storeKind);
      default -> doDirect(storeKind, dbFile, code, envName, restricted, useClass);
    };
  }

  @SuppressWarnings("RedundantThrows")
  static void printHelp() {
    System.out.print(BANNER);
    System.out.println(
        """
        Show information:
          mjvm -V\t\t\tOutput short version string
          mjvm -h\t\t\tOutput help menu
          mjvm -i [<database-file>]\tOutput environment info

        Create database volume:
          mjvm -v <volume-name>\t\tName of volume (1-32 alpha characters)
              -b <block-size>\t\tSize of database blocks (1-256 KiB)
              -s <database-size>\tInitial size of database (blocks)
             [-m <map-size>]\t\tSize of map block (KiB)
             [-e <environment-name>]\tName of manager UCI
             [<database-file>]\t\tName of database file

        Initialize and start environment:
          mjvm -j <max-jobs>\t\tSize of job table (1-4096 jobs)
             [-g <global-buffers>]\tGlobal buffers (MiB)
             [-r <routine-buffers>]\tRoutine buffers (MiB)
             [<database-file>]\t\tName of database file

        Start job and attach to environment:
          mjvm\t\t\t\tStarts in direct mode in manager UCI
             [-e <environment-name>]\tName of initial UCI environment
             [-x <M-commands>]\t\tString of M commands to execute
             [-R] [<database-file>]\tStarts in restricted mode

        Stop jobs and shut down environment:
          mjvm -k [<database-file>]\tKill environment

        Extended (mJVM):
          mjvm --class -c '<M>'\tCompile to .class then run (M2)
          mjvm file.m\t\t\tRun a source file
        """);
  }

  @SuppressWarnings("RedundantThrows")
  static Path envFile(String dbFile) {
    Path db = Path.of(dbFile == null ? "mjvm.dat" : dbFile);
    return db.resolveSibling(db.getFileName() + ".env");
  }

  @SuppressWarnings("RedundantThrows")
  static int doInfo(String dbFile) throws Exception {
    Path db = Path.of(dbFile == null ? "mjvm.dat" : dbFile);
    Path env = envFile(dbFile);
    if (dbFile == null) {
      System.out.println("Please pass database file path or set RSM_DBFILE");
    }
    System.out.println("mJVM " + VERSION);
    System.out.println("Drop-in CLI shaped like Reference Standard M (rsm)");
    System.out.println("Database Version: 1\tCompiler Version: 1");
    System.out.println();
    System.out.println("Database Volume and Environment Configuration Information:");
    System.out.println("  database    " + db.toAbsolutePath());
    System.out.println("  exists      " + Files.exists(db));
    if (Files.exists(env)) {
      for (String ln : Files.readAllLines(env)) {
        System.out.println("  " + ln);
      }
    } else {
      System.out.println("  status      not created (use mjvm -v …)");
    }
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static int doCreate(
      String volume, Integer bs, Integer sz, Integer ms, String envName, String dbFile)
      throws Exception {
    if (volume == null || !volume.matches("[A-Za-z]{1,32}")) {
      System.err.println("mjvm -v: volume-name must be 1-32 alpha characters");
      return 2;
    }
    Path db = Path.of(dbFile == null ? volume.toLowerCase(java.util.Locale.ROOT) + ".dat" : dbFile);
    if (Files.exists(db)) {
      System.err.println("mjvm: database already exists: " + db);
      return 1;
    }
    // create empty SQLite-backed store
    SqlStore st = new SqlStore(db.toString());
    st.close();
    List<String> env = new ArrayList<>();
    env.add("volume " + volume);
    env.add("env " + envName);
    env.add("block-size " + (bs == null ? 8 : bs));
    env.add("db-size " + (sz == null ? 1024 : sz));
    if (ms != null) env.add("map-size " + ms);
    env.add("created " + java.time.Instant.now());
    Files.write(envFile(db.toString()), env);
    System.out.println("Created database " + volume + " at " + db);
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static int doStart(int maxJobs, Integer gbuf, Integer rbuf, String dbFile, String envName)
      throws Exception {
    Path db = Path.of(dbFile == null ? "mjvm.dat" : dbFile);
    if (!Files.exists(db)) {
      System.err.println("mjvm: no such database: " + db + " (use mjvm -v first)");
      return 1;
    }
    Path env = envFile(db.toString());
    List<String> lines =
        Files.exists(env) ? new ArrayList<>(Files.readAllLines(env)) : new ArrayList<>();
    lines.removeIf(l -> l.startsWith("status ") || l.startsWith("max-jobs "));
    lines.add("max-jobs " + maxJobs);
    lines.add("status running");
    lines.add("env " + envName);
    Files.write(env, lines);
    System.out.println("I AM READY.");
    System.out.println(
        "Environment started: "
            + db
            + " jobs="
            + maxJobs
            + " env="
            + envName
            + (gbuf != null ? " gbuf=" + gbuf : "")
            + (rbuf != null ? " rbuf=" + rbuf : ""));
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static int doKill(String dbFile) throws Exception {
    Path env = envFile(dbFile);
    if (Files.exists(env)) {
      List<String> lines = new ArrayList<>(Files.readAllLines(env));
      lines.removeIf(l -> l.startsWith("status "));
      lines.add("status stopped");
      Files.write(env, lines);
    }
    System.out.println("Environment shut down.");
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static int doRun(String code, String file, boolean useClass, String dbFile, String storeKind)
      throws Exception {
    if (code == null && file != null) {
      code = Files.readString(Path.of(file));
    }
    if (code == null) {
      code = new String(System.in.readAllBytes());
    }
    StoreApi store = Stores.open(storeKind, dbFile);
    MRuntime rt = new MRuntime(store);
    runCode(rt, code, useClass);
    System.out.print(rt.output());
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static int doDirect(
      String storeKind,
      String dbFile,
      String code,
      String envName,
      boolean restricted,
      boolean useClass)
      throws Exception {
    StoreApi store = Stores.open(storeKind, dbFile);
    MRuntime rt = new MRuntime(store);
    if (code != null) {
      runCode(rt, code, useClass);
      System.out.print(rt.output());
      return 0;
    }
    if (restricted) {
      System.out.println("mJVM restricted mode");
    }
    System.out.println(BANNER.trim());
    System.out.println(
        "direct mode · env="
            + envName
            + " · store="
            + Stores.describe(store)
            + (dbFile != null ? " · db=" + dbFile : ""));
    // REPL: Console if interactive, else read System.in (piped/scripted)
    Console cons = System.console();
    if (cons != null) {
      String ln;
      while ((ln = cons.readLine("> ")) != null) {
        if (ln.trim().equals("H") || ln.trim().equalsIgnoreCase("HALT")) break;
        try {
          runCode(rt, ln, useClass);
          System.out.print(rt.output());
          rt.reset();
        } catch (Exception e) {
          System.out.println(e.getMessage());
        }
      }
    } else {
      // Non-interactive: read commands from stdin
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      String ln;
      while ((ln = br.readLine()) != null) {
        if (ln.trim().equals("H") || ln.trim().equalsIgnoreCase("HALT")) break;
        if (ln.trim().isEmpty()) continue;
        try {
          runCode(rt, ln, useClass);
          System.out.print(rt.output());
          rt.reset();
        } catch (Exception e) {
          System.out.println(e.getMessage());
        }
      }
    }
    return 0;
  }

  @SuppressWarnings("RedundantThrows")
  static void runCode(MRuntime rt, String code, boolean useClass) throws Exception {
    MFrontend fe = new MFrontend();
    List<Ir> prog = code.indexOf('\n') >= 0 ? fe.compileProgram(code) : fe.compileLine(code);
    if (useClass) {
      // JavaSourceEmitter: compile to Java source, JIT compiles to bytecode
      // 29x faster than IR interpreter
      var runner = mjvm.compiler.JavaSourceEmitter.compile(prog, "MjvmGen" + System.nanoTime(), rt);
      runner.run();
    } else {
      IrMachine.run(prog, rt, fe.lastTags());
    }
  }
}
