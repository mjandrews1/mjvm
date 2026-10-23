package jvmumps;

import mjvm.compiler.*;
import mjvm.runtime.*;

/** Benchmark: Java source emission vs IR interpreter for bank DEP pattern. */
public final class JavaSourceBench {
    public static void main(String[] args) throws Exception {
        String bankCode = "D INITA^BANK(0) W $$BENCH^BANK(0,100),!\n";
        var fe = new MFrontend();
        var bankLib = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/m/lib/BANK.m"));
        var libProg = fe.compileProgram(bankLib);
        var libTags = fe.lastTags();
        var benchProg = fe.compileProgram(bankCode);

        // Test 1: IR interpreter (baseline)
        {
            var store = new MStore();
            var rt = new MRuntime(store);
            IrMachine.run(libProg, rt, libTags);
            rt.reset();
            long t0 = System.nanoTime();
            IrMachine.run(benchProg, rt, fe.lastTags());
            long t1 = System.nanoTime();
            System.out.printf("IR interpreter:  %.1f ms%n", (t1 - t0) / 1e6);
        }

        // Test 2: Java source emission (JIT)
        {
            var store = new MStore();
            var rt = new MRuntime(store);
            // Compile bank lib via interpreter first (for tags)
            IrMachine.run(libProg, rt, libTags);
            rt.reset();
            // Generate + compile bench code to Java
            long t0 = System.nanoTime();
            var runner = JavaSourceEmitter.compile(benchProg, "BenchJIT", rt);
            runner.run();
            long t1 = System.nanoTime();
            System.out.printf("Java source:     %.1f ms (includes compile)%n", (t1 - t0) / 1e6);
        }

        // Test 3: JIT warm (compile once, run many)
        {
            var store = new MStore();
            var rt = new MRuntime(store);
            IrMachine.run(libProg, rt, libTags);
            var runner = JavaSourceEmitter.compile(benchProg, "BenchJIT2", rt);
            // Warmup
            for (int i = 0; i < 5; i++) {
                rt.reset();
                runner.run();
            }
            long t0 = System.nanoTime();
            int n = 20;
            for (int i = 0; i < n; i++) {
                rt.reset();
                runner.run();
            }
            long t1 = System.nanoTime();
            System.out.printf("Java source JIT: %.1f ms (avg of %d runs)%n", (t1 - t0) / 1e6 / n, n);
        }
    }
}
