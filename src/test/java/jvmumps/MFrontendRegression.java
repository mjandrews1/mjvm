package jvmumps;

import java.nio.file.*;
import java.util.*;
import mjvm.compiler.*;
import mjvm.runtime.*;
import mjvm.ir.Ir;

/**
 * MFrontend regression tests — compiler bugs that silently produced wrong IR.
 *
 * Bug 1: $I() peephole comma detection
 *   S %SEQ=$I(^BF("J","SEQ")) split at the subscript comma, producing
 *   STORE_ADD(^BF("J") + EVAL_EXPR("SEQ")) instead of STORE_ADD(^BF("J","SEQ")).
 *   Root cause: inner.indexOf(',') did not respect paren depth.
 *
 * Bug 2: double-space in concat expression
 *   S %X=A_"|"_  B (two spaces after concat operator) caused a parse error.
 *   The error handler replaced the line with RET, silently truncating the function.
 *
 * Usage: java -cp build:lib/... jvmumps.MFrontendRegression
 */
public final class MFrontendRegression {

    public static void main(String[] args) throws Exception {
        System.out.println("=== MFrontend Regression Tests ===");
        System.out.println();
        int pass = 0, fail = 0;

        int[] r;

        // ================================================================
        // Bug 1: $I() with subscripted global containing commas
        // ================================================================
        r = testDollarIncrement();
        pass += r[0]; fail += r[1];

        // ================================================================
        // Bug 2: double-space in concat expression
        // ================================================================
        r = testDoubleSpaceConcat();
        pass += r[0]; fail += r[1];

        // ================================================================
        // Bug 1 extended: $I() with amount argument
        // ================================================================
        r = testDollarIncrementWithAmount();
        pass += r[0]; fail += r[1];

        // ================================================================
        // Bug 1 extended: $I() on plain local (no commas)
        // ================================================================
        r = testDollarIncrementLocal();
        pass += r[0]; fail += r[1];

        // ================================================================
        // Bug 2 extended: double-space in various positions
        // ================================================================
        r = testDoubleSpaceVariants();
        pass += r[0]; fail += r[1];

        // ================================================================
        // Bug 3: PUSH_REG peephole with conditional store (jump target)
        // ================================================================
        r = testPushRegPeephole();
        pass += r[0]; fail += r[1];

        // Summary
        System.out.println();
        System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
        System.out.println(fail == 0 ? "  RESULT: MFrontend regression OK" : "  RESULT: MFrontend regression FAIL");
        System.exit(fail > 0 ? 1 : 0);
    }

    // ================================================================
    // Bug 1: $I(^G(sub1,sub2)) — comma inside subscripts must not split
    // ================================================================
    static int[] testDollarIncrement() {
        System.out.println("--- Bug 1: $I() with comma in subscripts ---");
        int pass = 0, fail = 0;

        // $I(^BF("J","SEQ")) must increment the full global path
        String code =
            "T N %S\n" +
            "S ^BF(\"J\",\"SEQ\")=0\n" +
            "S %S=$I(^BF(\"J\",\"SEQ\"))\n" +
            "W %S,!\n" +
            "W ^BF(\"J\",\"SEQ\"),!\n" +
            "Q\n";
        String[] exp = {"1\n1"};

        int[] r = runOne("dollar-incr-comma", code, exp);
        pass += r[0]; fail += r[1];

        // Two increments must return 1 then 2
        String code2 =
            "T N %A,%B\n" +
            "S ^BF(\"J\",\"SEQ\")=0\n" +
            "S %A=$I(^BF(\"J\",\"SEQ\"))\n" +
            "S %B=$I(^BF(\"J\",\"SEQ\"))\n" +
            "W %A,!,%B,!\n" +
            "Q\n";
        String[] exp2 = {"1\n2"};

        r = runOne("dollar-incr-twice", code2, exp2);
        pass += r[0]; fail += r[1];

        // Deeply nested subscripts with multiple commas
        String code3 =
            "T N %S\n" +
            "S ^X(\"a\",\"b\",\"c\")=0\n" +
            "S %S=$I(^X(\"a\",\"b\",\"c\"))\n" +
            "W %S,!\n" +
            "Q\n";
        String[] exp3 = {"1"};

        r = runOne("dollar-incr-deep", code3, exp3);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

    // ================================================================
    // Bug 1: $I(path,amount) — amount separator is at depth 0
    // ================================================================
    static int[] testDollarIncrementWithAmount() {
        System.out.println("--- Bug 1b: $I() with amount argument ---");
        int pass = 0, fail = 0;

        // $I(^G(sub),5) must add 5, not split at subscript comma
        String code =
            "T N %S\n" +
            "S ^C(\"K\")=10\n" +
            "S %S=$I(^C(\"K\"),5)\n" +
            "W %S,!\n" +
            "W ^C(\"K\"),!\n" +
            "Q\n";
        String[] exp = {"15\n15"};

        int[] r = runOne("dollar-incr-amount", code, exp);
        pass += r[0]; fail += r[1];

        // $I(^G(a,b),3) — subscript comma AND amount comma
        String code2 =
            "T N %S\n" +
            "S ^D(\"p\",\"q\")=0\n" +
            "S %S=$I(^D(\"p\",\"q\"),3)\n" +
            "W %S,!\n" +
            "Q\n";
        String[] exp2 = {"3"};

        r = runOne("dollar-incr-sub-and-amount", code2, exp2);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

    // ================================================================
    // Bug 1: $I(local) — no commas, must still work
    // ================================================================
    static int[] testDollarIncrementLocal() {
        System.out.println("--- Bug 1c: $I() on plain local ---");
        int pass = 0, fail = 0;

        String code =
            "T N %C\n" +
            "S %C=$I(%C)\n" +
            "W %C,!\n" +
            "S %C=$I(%C)\n" +
            "W %C,!\n" +
            "Q\n";
        String[] exp = {"1\n2"};

        int[] r = runOne("dollar-incr-local", code, exp);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

    // ================================================================
    // Bug 2: double-space in concat must not cause silent RET
    // ================================================================
    static int[] testDoubleSpaceConcat() {
        System.out.println("--- Bug 2: double-space in concat ---");
        int pass = 0, fail = 0;

        // The exact pattern from BANKFULL.m JOURNAL
        String code =
            "T N %TYPE,%SVC,%AMT,%BAL,%DATA\n" +
            "S %TYPE=\"DEPOSIT\" S %SVC=1 S %AMT=500 S %BAL=500\n" +
            "S %DATA=%TYPE_\"|\"_  %SVC_\"|\"_%AMT_\"|\"_%BAL\n" +
            "W %DATA,!\n" +
            "Q\n";
        String[] exp = {"DEPOSIT|1|500|500"};

        int[] r = runOne("double-space-concat", code, exp);
        pass += r[0]; fail += r[1];

        // After the concat, more statements must execute (not RET early)
        String code2 =
            "T N %A,%B,%C\n" +
            "S %A=\"X\" S %B=\"Y\" S %C=\"Z\"\n" +
            "S %A=%A_\"|\"_  %B_\"|\"_%C\n" +
            "W %A,!\n" +
            "W \"AFTER\",!\n" +
            "Q\n";
        String[] exp2 = {"X|Y|Z\nAFTER"};

        r = runOne("double-space-no-early-ret", code2, exp2);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

    // ================================================================
    // Bug 2: double-space in various positions
    // ================================================================
    static int[] testDoubleSpaceVariants() {
        System.out.println("--- Bug 2b: double-space variants ---");
        int pass = 0, fail = 0;

        // Double space before a variable in concat
        String code =
            "T N %A,%B\n" +
            "S %A=1 S %B=2\n" +
            "W %A_\"  \"_%B,!\n" +
            "Q\n";
        String[] exp = {"1  2"};

        int[] r = runOne("double-space-in-string", code, exp);
        pass += r[0]; fail += r[1];

        // Double space in expression (not concat) — should also work
        String code2 =
            "T N %A\n" +
            "S %A=  42\n" +
            "W %A,!\n" +
            "Q\n";
        String[] exp2 = {"42"};

        r = runOne("double-space-in-expr", code2, exp2);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

    // ================================================================
    // Bug 3: Q var after conditional S var — PUSH_REG peephole must not fire
    // ================================================================
        static int[] testPushRegPeephole() {
        System.out.println("--- Bug 3: PUSH_REG peephole with conditional store ---");
        int pass = 0, fail = 0;

        // Pattern: S %OK=1 then conditional S %OK=0 then W %OK
        String code = "T N %A,%OK\nS %OK=1\nS %A=5\nI %A>3 S %OK=0\nW %OK,!";
        String[] exp = {"0"};
        int[] r = runOne("push-reg-cond-store", code, exp);
        pass += r[0]; fail += r[1];

        String code2 = "T N %A,%OK\nS %OK=1\nS %A=1\nI %A>3 S %OK=0\nW %OK,!";
        String[] exp2 = {"1"};
        r = runOne("push-reg-cond-false", code2, exp2);
        pass += r[0]; fail += r[1];

        String code3 = "T N %I,%X,%OK\nS %OK=1\nF %I=1:1:3 S %X=%I*10\nW %OK,!";
        String[] exp3 = {"1"};
        r = runOne("push-reg-after-loop", code3, exp3);
        pass += r[0]; fail += r[1];

        return new int[]{pass, fail};
    }

// ================================================================
    // Shared runner: compile code + test, compare output
    // ================================================================
    static int[] runOne(String name, String code, String[] expected) {
        try {
            MFrontend fe = new MFrontend();
            List<Ir> prog = fe.compileProgram(code);
            var rt = new MRuntime(new MStore());
            IrMachine.run(prog, rt, fe.lastTags());
            String got = rt.output().strip();
            String want = expected[0];
            if (want.equals(got)) {
                System.out.printf("  %s PASS%n", name);
                return new int[]{1, 0};
            } else {
                System.out.printf("  %s FAIL exp=\"%s\" got=\"%s\"%n", name, want, got);
                return new int[]{0, 1};
            }
        } catch (Exception e) {
            System.out.printf("  %s EXC %s%n", name, e.getMessage());
            return new int[]{0, 1};
        }
    }

    private MFrontendRegression() {}
}
