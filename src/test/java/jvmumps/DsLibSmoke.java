package jvmumps;

import java.nio.file.*;
import java.util.*;

/**
 * MeshGraph DS library smoke (JavaM#28) — exercises HMAP, HSTACK, HQUEUE, HHEAP, HPQUE against both
 * engines. Run on U01 YottaDB for parity.
 *
 * <p>Usage: java -cp build:lib/... jvmumps.DsLibSmoke [lib_dir]
 */
public final class DsLibSmoke {

  static String libDir() {
    String env = System.getenv("DS_LIB");
    return env != null && !env.isEmpty() ? env : "src/main/m/lib";
  }

  public static void main(String[] args) throws Exception {
    String dir = args.length > 0 ? args[0] : libDir();
    if (!Files.isDirectory(Path.of(dir))) {
      System.out.println("## DS library smoke: SKIP (no lib dir: " + dir + ")");
      return;
    }
    System.out.println("## MeshGraph DS library smoke");
    System.out.println("### lib=" + dir);
    System.out.println();

    int pass = 0, fail = 0;
    int[] r;

    // --- HSTACK ---
    String hstack = Files.readString(Path.of(dir, "HSTACK.m"));
    String[] stackTests = {
      // depth after pushes
      "D PUSH^HSTACK(\"s2\",\"x\") D PUSH^HSTACK(\"s2\",\"y\") W $$DEPTH^HSTACK(\"s2\"),!",
      // pop empty returns 0
      "S x=$$POP^HSTACK(\"s4\",.v) W x,!",
      // LIFO order via depth
      "D PUSH^HSTACK(\"s5\",\"1\") D PUSH^HSTACK(\"s5\",\"2\") D PUSH^HSTACK(\"s5\",\"3\") W"
          + " $$DEPTH^HSTACK(\"s5\"),!",
    };
    String[] stackExpected = {"2", "0", "3"};
    r = runTests("HSTACK", hstack, stackTests, stackExpected);
    pass += r[0];
    fail += r[1];

    // --- HQUEUE ---
    String hqueue = Files.readString(Path.of(dir, "HQUEUE.m"));
    String[] queueTests = {
      "D ENQ^HQUEUE(\"q2\",\"x\") D ENQ^HQUEUE(\"q2\",\"y\") W $$QCOUNT^HQUEUE(\"q2\"),!",
      "S x=$$DEQ^HQUEUE(\"q4\",.v) W x,!",
      "D ENQ^HQUEUE(\"q5\",\"1\") D ENQ^HQUEUE(\"q5\",\"2\") W $$QCOUNT^HQUEUE(\"q5\"),!",
    };
    String[] queueExpected = {"2", "0", "2"};
    r = runTests("HQUEUE", hqueue, queueTests, queueExpected);
    pass += r[0];
    fail += r[1];

    // --- HHEAP ---
    String hheap = Files.readString(Path.of(dir, "HHEAP.m"));
    String[] heapTests = {
      "D PUSH^HHEAP(\"h2\",\"2\",\"a\") D PUSH^HHEAP(\"h2\",\"4\",\"b\") W"
          + " $$HHEAPN^HHEAP(\"h2\"),!",
      "S x=$$POP^HHEAP(\"h4\",.k,.p) W x,!",
      "D PUSH^HHEAP(\"h5\",\"9\",\"d\") D PUSH^HHEAP(\"h5\",\"1\",\"a\") W"
          + " $$HHEAPN^HHEAP(\"h5\"),!",
    };
    String[] heapExpected = {"2", "0", "2"};
    r = runTests("HHEAP", hheap, heapTests, heapExpected);
    pass += r[0];
    fail += r[1];

    // --- HPQUE ---
    String hpque = Files.readString(Path.of(dir, "HPQUE.m"));
    String[] pqTests = {
      "D ENQ^HPQUE(\"p2\",\"2\",\"a\") D ENQ^HPQUE(\"p2\",\"4\",\"b\") W $$QLEN^HPQUE(\"p2\"),!",
      "S x=$$DEQ^HPQUE(\"p4\",.pr,.pl) W x,!",
      "D ENQ^HPQUE(\"p5\",\"1\",\"first\") D ENQ^HPQUE(\"p5\",\"1\",\"second\") W"
          + " $$QLEN^HPQUE(\"p5\"),!",
    };
    String[] pqExpected = {"2", "0", "2"};
    r = runTests("HPQUE", hpque, pqTests, pqExpected);
    pass += r[0];
    fail += r[1];

    // --- HMAP ---
    String hmap = Files.readString(Path.of(dir, "HMAP.m"));
    String[] mapTests = {
      "S x=$$GET^HMAP(\"m3\",\"nope\",.v) W x,!",
      "D PUT^HMAP(\"m4\",\"k\",\"v\") D DEL^HMAP(\"m4\",\"k\") W \"del\",!",
      "D PUT^HMAP(\"m5\",\"a\",\"1\") D PUT^HMAP(\"m5\",\"b\",\"2\") W \"stored\",!",
    };
    String[] mapExpected = {"0", "del", "stored"};
    r = runTests("HMAP", hmap, mapTests, mapExpected);
    pass += r[0];
    fail += r[1];

    System.out.println();
    System.out.printf("  summary: pass=%d fail=%d%n", pass, fail);
    System.out.println("  RESULT: DS library smoke " + (fail == 0 ? "OK" : "FAILED"));
  }

  static int[] runTests(String name, String code, String[] tests, String[] expected) {
    int pass = 0, fail = 0;
    for (int i = 0; i < tests.length; i++) {
      try {
        var rt = new mjvm.runtime.MRuntime(new MStore());
        var fe = new mjvm.compiler.MFrontend();
        // Phase 1: load library (registers tags via ZLINK)
        var libProg = fe.compileProgram(code);
        mjvm.runtime.IrMachine.run(libProg, rt, fe.lastTags());
        rt.reset();
        // Phase 2: run test as a single-line program
        var testProg = fe.compileProgram(tests[i] + "\n");
        mjvm.runtime.IrMachine.run(testProg, rt, fe.lastTags());
        String got = rt.output().strip();
        String want = expected[i];
        if (want.equals(got)) {
          pass++;
        } else {
          fail++;
          System.out.printf("  %s[%d] FAIL exp=%s got=%s%n", name, i, want, got);
        }
      } catch (Exception e) {
        fail++;
        System.out.printf("  %s[%d] EXC %s%n", name, i, e.getMessage());
      }
    }
    return new int[] {pass, fail};
  }

  private DsLibSmoke() {}
}
