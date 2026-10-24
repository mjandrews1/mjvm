package jvmumps;

import java.nio.file.*;

/**
 * GAUNTLET runner — executes GAUNTLET.m and reports results.
 * Tests the full M language surface.
 */
public final class GauntletRun {

    public static void main(String[] args) throws Exception {
        String bankPath = args.length > 0 ? args[0] : "src/main/m/lib/GAUNTLET.m";
        String source = Files.readString(Path.of(bankPath));

        System.out.println("=== GAUNTLET — Comprehensive M Language Benchmark ===");
        System.out.println("Testing: arithmetic, strings, comparisons, patterns, booleans,");
        System.out.println("control flow, variables, special vars, $SELECT/$CASE, indirection,");
        System.out.println("pattern matching, transactions, locks, $ETRAP, $P/$E SET,");
        System.out.println("multi-level $ORDER, performance stress");
        System.out.println();

        var store = new MStore();
        var rt = new mjvm.runtime.MRuntime(store);
        var fe = new mjvm.compiler.MFrontend();

        // Load GAUNTLET.m
        var libProg = fe.compileProgram(source);
        mjvm.runtime.IrMachine.run(libProg, rt, fe.lastTags());
        rt.reset();

        // Run the benchmark
        long t0 = System.nanoTime();
        var runProg = fe.compileProgram("D RUN^GAUNTLET\n");
        mjvm.runtime.IrMachine.run(runProg, rt, fe.lastTags());
        long elapsed = System.nanoTime() - t0;

        String output = rt.output().trim();
        System.out.println("Raw output: " + output);
        System.out.println();

        // Parse results: G|passed|failed|total_ms
        if (output.startsWith("G|")) {
            String[] parts = output.split("\\|");
            int passed = Integer.parseInt(parts[1]);
            int failed = Integer.parseInt(parts[2]);
            int totalMs = Integer.parseInt(parts[3]);
            int total = passed + failed;

            System.out.println("=== Results ===");
            System.out.printf("Passed:  %d / %d (%.1f%%)%n", passed, total, 100.0 * passed / total);
            System.out.printf("Failed:  %d%n", failed);
            System.out.printf("Time:    %d ms (M-reported)%n", totalMs);
            System.out.printf("Wall:    %.1f ms (Java)%n", elapsed / 1e6);
            System.out.println();

            if (failed == 0) {
                System.out.println("ALL GAUNTLET TESTS PASSED");
            } else {
                System.out.println("SOME GAUNTLET TESTS FAILED");
            }
            System.exit(failed == 0 ? 0 : 1);
        } else {
            System.out.println("UNEXPECTED OUTPUT FORMAT");
            System.exit(1);
        }
    }
}
