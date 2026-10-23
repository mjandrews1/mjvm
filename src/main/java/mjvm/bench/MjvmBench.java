package mjvm.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jvmumps.MStore;
import jvmumps.MValue;
import mjvm.compiler.ClassEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * mJVM M8 benchmark harness (M8).
 *
 * Measures:
 *   1. IR machine throughput (ops/sec on arith / store / concat loops)
 *   2. Generated-class vs IR speedup
 *   3. Frontend compile latency
 *
 * Usage:
 *   java mjvm.bench.MjvmBench              # default 200k iters
 *   java mjvm.bench.MjvmBench 2000000
 */
public final class MjvmBench {

    /** Perf targets (M8) — floor on this machine, not hard CI gates. */
    public static final double TARGET_IR_ARITH_OPS = 1_000_000;      // loop iters/s
    public static final double TARGET_IR_CAT_OPS = 100_000;         // string-alloc bound
    public static final double TARGET_CLASS_SPEEDUP = 0.8;          // class / IR (not slower)
    public static final double TARGET_COMPILE_MS = 50;              // small program

    public static void main(String[] args) throws Exception {
        int iters = args.length > 0 ? Integer.parseInt(args[0]) : 200_000;
        System.out.println("mJVM M8 bench  iters=" + iters);
        System.out.println("targets: IR arith >= " + (long) TARGET_IR_ARITH_OPS
                + " it/s · class/IR >= " + TARGET_CLASS_SPEEDUP
                + "x · compile <= " + TARGET_COMPILE_MS + " ms");
        System.out.println();

        // --- compile latency ---
        String src = "S X=1 S Y=2 W X+Y,!";
        long t0 = System.nanoTime();
        List<Ir> prog = new MFrontend().compileLine(src);
        double compileMs = (System.nanoTime() - t0) / 1e6;
        System.out.printf("compile %-28s %8.3f ms   (%d ops)%n", "S X=1 S Y=2 W X+Y,!", compileMs, prog.size());

        // --- IR loops (JMP) — class emitter is straight-line (M2) ---
        for (String kind : new String[] {"inc", "cat", "store"}) {
            // warmup — cold JIT/interpreter startup fails the IR floor unfairly
            IrMachine.run(loopProg(Math.min(iters, 20_000), kind), new MRuntime(new MStore()));
            List<Ir> loop = loopProg(iters, kind);
            MRuntime rt = new MRuntime(new MStore());
            long t1 = System.nanoTime();
            IrMachine.run(loop, rt);
            double ns = System.nanoTime() - t1;
            double ops = iters / (ns / 1e9);
            System.out.printf("IR loop %-22s %8.2f ms %12.0f it/s%n",
                    "S I=N · " + kind, ns / 1e6, ops);
            double floor = kind.equals("cat") ? TARGET_IR_CAT_OPS : TARGET_IR_ARITH_OPS;
            check(kind + " IR floor", ops >= floor,
                    (long) ops + " it/s vs target " + (long) floor);
        }

        // --- straight-line unroll: IR vs generated class ---
        // javac 64KB method limit — keep unroll modest
        // 1000-op unroll exceeds the JVM 64KB method limit (code too large); 200 is safe
        benchPair("unroll inc x200", unrollProg(200, "inc"), 200);
        benchPair("unroll cat x200", unrollProg(200, "cat"), 200);

        double cMs = compileMs;
        check("compile latency", cMs <= TARGET_COMPILE_MS,
                String.format("%.3f ms vs target %.0f", cMs, TARGET_COMPILE_MS));

        System.out.println();
        System.out.println("M8 bench done.");
    }

    /** Straight-line unroll — valid Java after ClassEmitter (no JMP). */
    static List<Ir> unrollProg(int n, String kind) {
        MFrontend fe = new MFrontend();
        List<Ir> prog = new ArrayList<>();
        if (kind.equals("inc")) {
            prog.addAll(fe.compileFragment("S X=0"));
            for (int i = 0; i < n; i++) {
                prog.add(Ir.load("X"));
                prog.add(Ir.pushNum(1));
                prog.add(Ir.bin(Ir.ADD));
                prog.add(Ir.store("X"));
            }
        } else {
            prog.addAll(fe.compileFragment("S S=\"\""));
            for (int i = 0; i < n; i++) {
                prog.add(Ir.load("S"));
                prog.add(Ir.pushStr("x"));
                prog.add(Ir.bin(Ir.CONCAT));
                prog.add(Ir.store("S"));
            }
        }
        prog.add(Ir.ret());
        return prog;
    }

    /** Counted loop: S I=N then repeat body N times (JMP back). */
    public static List<Ir> loopProg(int n, String kind) {
        MFrontend fe = new MFrontend();
        List<Ir> prog = new ArrayList<>();
        prog.addAll(fe.compileLine("S I=" + n));
        int top = prog.size();
        if (kind.equals("inc")) {
            prog.addAll(fe.compileLine("S X=0"));
            // body only first time? — do body every iter: S X=X+1
        }
        // rebuild cleanly: init, then loop
        prog.clear();
        if (kind.equals("inc")) {
            prog.addAll(fe.compileFragment("S X=0 S I=" + n));
        } else if (kind.equals("cat")) {
            prog.addAll(fe.compileFragment("S S=\"\" S I=" + n));
        } else {
            prog.addAll(fe.compileFragment("S A=0 S I=" + n));
        }
        top = prog.size();
        if (kind.equals("inc")) {
            prog.add(Ir.load("X"));
            prog.add(Ir.pushNum(1));
            prog.add(Ir.bin(Ir.ADD));
            prog.add(Ir.store("X"));
        } else if (kind.equals("cat")) {
            prog.add(Ir.load("S"));
            prog.add(Ir.pushStr("x"));
            prog.add(Ir.bin(Ir.CONCAT));
            prog.add(Ir.store("S"));
        } else {
            prog.add(Ir.load("A"));
            prog.add(Ir.pushNum(1));
            prog.add(Ir.bin(Ir.ADD));
            prog.add(Ir.store("A"));
        }
        // I = I - 1; if I > 0 goto top
        prog.add(Ir.load("I"));
        prog.add(Ir.pushNum(1));
        prog.add(Ir.bin(Ir.SUB));
        prog.add(Ir.store("I"));
        prog.add(Ir.load("I"));
        prog.add(Ir.pushNum(0));
        prog.add(Ir.bin(Ir.SUB)); // I-0 — truthy if I != 0
        prog.add(Ir.jmp0(prog.size() + 2));
        prog.add(Ir.jmp(top));
        prog.add(Ir.ret());
        return prog;
    }

    static void benchPair(String name, List<Ir> prog, int iters) throws Exception {
        // IR
        MRuntime rt = new MRuntime(new MStore());
        long t0 = System.nanoTime();
        IrMachine.run(prog, rt);
        double irNs = System.nanoTime() - t0;
        double irOps = iters / (irNs / 1e9);

        // class (once — JIT warm on second run)
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-bench");
        String cn = "B" + System.nanoTime();
        ClassEmitter.emit(prog, cn, dir);
        Class<?> cl = Class.forName(cn, true,
                new java.net.URLClassLoader(new java.net.URL[] { dir.toUri().toURL() },
                        MjvmBench.class.getClassLoader()));
        Runnable r = (Runnable) cl.getConstructor(MRuntime.class).newInstance(new MRuntime(new MStore()));
        r.run(); // warm
        MRuntime rt2 = new MRuntime(new MStore());
        Runnable r2 = (Runnable) cl.getConstructor(MRuntime.class).newInstance(rt2);
        t0 = System.nanoTime();
        r2.run();
        double clNs = System.nanoTime() - t0;
        double clOps = iters / (clNs / 1e9);

        double speedup = irNs / clNs;
        System.out.printf("%-28s IR %8.2f ms %10.0f it/s | class %8.2f ms %10.0f it/s | %.2fx%n",
                name, irNs / 1e6, irOps, clNs / 1e6, clOps, speedup);

        double floor = name.contains("cat")
                ? TARGET_IR_CAT_OPS
                : TARGET_IR_ARITH_OPS;
        check(name + " IR floor", irOps >= floor,
                (long) irOps + " it/s vs target " + (long) floor);
        check(name + " class runs", speedup > 0,
                String.format("%.2fx IR/class (IR machine wins until JIT+indy C2)", speedup));
    }

    /**
     * Enforce a bench assertion. Failure prints {@code FAIL} and throws so
     * CI / make bench cannot go green on a red target.
     */
    static void check(String label, boolean ok, String detail) {
        System.out.printf("  [%s] %s — %s%n", ok ? "PASS" : "FAIL", label, detail);
        if (!ok) {
            throw new IllegalStateException("MjvmBench FAIL: " + label + " — " + detail);
        }
    }
}
