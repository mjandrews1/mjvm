package jvmumps;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Compare SETB strategies: raw pipe protocol. */
public final class SetbBench2 {
  public static void main(String[] args) throws Exception {
    String pipe = System.getenv("CDB2PIPE");
    if (pipe == null) pipe = "tools/cdb2pipe";

    ProcessBuilder pb = new ProcessBuilder(pipe, "testdb", "local");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process proc = pb.start();
    BufferedWriter in =
        new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader out =
        new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
    out.readLine(); // PONG

    // Test 1: SETB with explicit BEGIN/COMMIT wrapping
    System.out.println("=== SETB inside explicit transaction ===");
    for (int batchSize : new int[] {50, 100, 300}) {
      // Warmup
      doSetbTxn(in, out, batchSize, "W");
      // Measure
      long t0 = System.nanoTime();
      int trials = 5;
      for (int t = 0; t < trials; t++) {
        doSetbTxn(in, out, batchSize, "T" + t);
      }
      long t1 = System.nanoTime();
      double ms = (t1 - t0) / 1e6 / trials;
      System.out.printf(
          "SETB %3d rows in txn: %8.1f ms/batch (%.2f ms/row)%n", batchSize, ms, ms / batchSize);
    }

    // Test 2: Multiple SETBs in one transaction
    System.out.println();
    System.out.println("=== Multiple SETBs in one transaction ===");
    for (int batches : new int[] {1, 5, 10}) {
      long t0 = System.nanoTime();
      int trials = 3;
      for (int t = 0; t < trials; t++) {
        in.write("BEGIN\n");
        in.flush();
        out.readLine();
        for (int b = 0; b < batches; b++) {
          doSetb(in, out, 50, "M" + t + "b" + b);
        }
        in.write("COMMIT\n");
        in.flush();
        out.readLine();
      }
      long t1 = System.nanoTime();
      double ms = (t1 - t0) / 1e6 / trials;
      System.out.printf(
          "%2d x SETB 50 in 1 txn: %8.1f ms (%.1f ms per SETB)%n", batches, ms, ms / batches);
    }

    in.write("QUIT\n");
    in.flush();
    proc.waitFor();
  }

  static void doSetb(BufferedWriter in, BufferedReader out, int n, String prefix) throws Exception {
    StringBuilder sb = new StringBuilder("SETB\t").append(n).append('\n');
    for (int i = 0; i < n; i++) {
      sb.append(
          String.format("%02x%02x%02x\t00\t%s\t1\t00\n", prefix.hashCode() & 0xff, i & 0xff, 0, i));
    }
    in.write(sb.toString());
    in.flush();
    out.readLine();
  }

  static void doSetbTxn(BufferedWriter in, BufferedReader out, int n, String prefix)
      throws Exception {
    in.write("BEGIN\n");
    in.flush();
    out.readLine();
    doSetb(in, out, n, prefix);
    in.write("COMMIT\n");
    in.flush();
    out.readLine();
  }
}
