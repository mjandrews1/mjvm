package jvmumps;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import mjvm.cli.Mjvm;

/** mJVM M7 — CLI drop-in for rsm flags (-V/-h/-i/-v/-j/-k/-x). */
public final class MjvmM7Smoke {
  static int n = 0;

  static void chk(boolean ok, String tag) {
    if (!ok) throw new IllegalStateException(tag);
    n++;
  }

  static void chkContains(String s, String want, String tag) {
    if (!s.contains(want)) {
      throw new IllegalStateException(tag + " missing " + want + " in " + s);
    }
    n++;
  }

  static String cap(Capture c, String[] args) throws Exception {
    PrintStream old = System.out;
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bo));
    try {
      Mjvm.run(args);
    } finally {
      System.setOut(old);
    }
    return bo.toString();
  }

  static class Capture {}

  public static void main(String[] a) throws Exception {
    Path tmp = Files.createTempDirectory("mjvm-m7");
    String db = tmp.resolve("test.dat").toString();

    String out = cap(null, new String[] {"-V"});
    chkContains(out, "V1.0.1", "M7-V");

    out = cap(null, new String[] {"-h"});
    chkContains(out, "Create database volume", "M7-h");
    chkContains(out, "Initialize and start environment", "M7-h2");
    chkContains(out, "Kill environment", "M7-h3");

    out = cap(null, new String[] {"-v", "TEST", "-b", "8", "-s", "256", db});
    chkContains(out, "Created database TEST", "M7-create");
    chk(Files.exists(Path.of(db)), "M7-db-exists");
    chk(Files.exists(Path.of(db + ".env")), "M7-env-exists");

    out = cap(null, new String[] {"-i", db});
    chkContains(out, "volume TEST", "M7-info");
    chk(!out.contains("not created"), "M7-info-created");
    chkContains(out, "mJVM", "M7-info-banner");

    out = cap(null, new String[] {"-j", "8", db});
    chkContains(out, "I AM READY.", "M7-start");
    chkContains(out, "jobs=8", "M7-jobs");

    out = cap(null, new String[] {"-i", db});
    chkContains(out, "status running", "M7-status-run");

    out = cap(null, new String[] {"-x", "S X=1 W X,!"});
    chkContains(out, "1", "M7-x");

    out = cap(null, new String[] {"-k", db});
    chkContains(out, "shut down", "M7-kill");

    out = cap(null, new String[] {"-i", db});
    chkContains(out, "status stopped", "M7-status-stop");

    // rsm: RSM_DBFILE env — create without positional db
    String prev = System.getenv("RSM_DBFILE");
    // (cannot set env in-process; exercise doInfo null path instead)
    out = cap(null, new String[] {"-i"});
    chkContains(out, "RSM_DBFILE", "M7-rsm-dbfile-msg");

    // -x against a live db volume
    out = cap(null, new String[] {"-x", "S ^G=7 W ^G,!", db});
    chkContains(out, "7", "M7-x-db");

    System.out.println("MjvmM7Smoke OK cases=" + n);
  }
}
