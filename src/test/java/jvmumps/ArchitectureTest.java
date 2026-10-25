package jvmumps;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules for mJVM — enforced as JUnit-style checks.
 *
 * <p>Layering (top to bottom): mjvm.cli → mjvm.runtime, mjvm.compiler, mjvm.ir, jvmumps
 * mjvm.compiler → mjvm.ir, jvmumps (MValue, MError) mjvm.runtime → mjvm.ir, jvmumps mjvm.ir →
 * (leaf) jvmumps.store → jvmumps (StoreApi, MValue) jvmumps → (base)
 *
 * <p>Invariants: 1. Compiler must not depend on runtime (one-way: runtime calls compiler output) 2.
 * IR must not depend on runtime or compiler (leaf module) 3. Store implementations must not depend
 * on compiler or runtime 4. No classes in jvmumps may depend on mjvm.* (base layer purity)
 *
 * <p>Usage: java -cp build:lib/... jvmumps.ArchitectureTest
 */
public final class ArchitectureTest {

  static int pass = 0, fail = 0;

  public static void main(String[] args) {
    System.out.println("=== mJVM Architecture Rules ===");
    System.out.println();

    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("jvmumps", "mjvm");

    // Rule 1: compiler must not depend on runtime EXCEPT emitters
    // (BytecodeEmitter/JavaSourceEmitter generate code that targets MRuntime —
    // this is a deliberate coupling at the codegen boundary)
    check(
        "compiler-must-not-depend-on-runtime",
        noClasses()
            .that()
            .resideInAPackage("mjvm.compiler..")
            .and()
            .haveSimpleNameNotEndingWith("Emitter")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("mjvm.runtime.."),
        classes);

    // Rule 2: IR must not depend on runtime or compiler
    check(
        "ir-is-leaf",
        noClasses()
            .that()
            .resideInAPackage("mjvm.ir..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("mjvm.runtime..", "mjvm.compiler.."),
        classes);

    // Rule 3: store implementations must not depend on compiler or runtime
    check(
        "store-independent-of-compiler-runtime",
        noClasses()
            .that()
            .resideInAPackage("jvmumps.store..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("mjvm.compiler..", "mjvm.runtime..")
            .allowEmptyShould(true),
        classes);

    // Rule 4: jvmumps base must not depend on mjvm.* (base layer purity)
    // Excludes test/bench/smoke classes which legitimately use the compiler+runtime.
    check(
        "jvmumps-is-base-layer",
        noClasses()
            .that()
            .resideInAPackage("jvmumps..")
            .and()
            .resideOutsideOfPackage("jvmumps.store..")
            .and()
            .haveNameMatching(
                ".*\\.(MStore|MValue|MError|StoreApi|StoreCore|StoreDb|StoreLock|ExprEval|ExprEngine|ExprIntrinsics|ExprTokens|Engine|Cursor|LineInterp|LineCmds|LineDevices|LineDispatch|LineEngine|LineEnv|LineErrors|LineExec|LineRefs|LineRoutine|LineState|LineTxLocks|LockQueue|PatternMatch|PemdasEval|CorpusCase|CorpusRunner|CorpusScore|JobChild|Repl|Cdb2Store|H2Store|MapDBStore|SqlStore)")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("mjvm.compiler..", "mjvm.runtime..", "mjvm.ir..")
            .allowEmptyShould(true),
        classes);

    // Rule 5: no classes should depend on deprecated Cdb2Store
    check(
        "no-new-cdb2-dependencies",
        noClasses()
            .that()
            .resideOutsideOfPackage("jvmumps..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("jvmumps.Cdb2Store"),
        classes);

    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(
        fail == 0 ? "  RESULT: Architecture rules OK" : "  RESULT: Architecture rules FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  static void check(String name, ArchRule rule, JavaClasses classes) {
    try {
      rule.check(classes);
      System.out.printf("  %s PASS%n", name);
      pass++;
    } catch (AssertionError e) {
      System.out.printf("  %s FAIL:%n%s%n", name, e.getMessage());
      fail++;
    }
  }

  private ArchitectureTest() {}
}
