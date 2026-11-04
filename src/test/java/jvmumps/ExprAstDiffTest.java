package jvmumps;

import java.util.*;

/**
 * Differential test for the ExprAst cache (perf item 5): every expression in the battery must
 * evaluate identically through the AST path ({@link ExprEval#eval}) and the legacy evaluating
 * parser ({@link ExprEval#evalLegacy}), including {@code lastRef} side effects. The one expected
 * fallback is $SELECT (lazy semantics), which must land in {@link ExprEval#noAstSrcs()} and still
 * match. Run: java -cp build:lib/* jvmumps.ExprAstDiffTest
 */
public final class ExprAstDiffTest {

  static int pass = 0, fail = 0;

  public static void main(String[] args) {
    System.out.println("=== ExprAst differential (AST cache vs legacy parser) ===");

    var env =
        new ExprEval.Env() {
          final Map<String, MValue> vars = new HashMap<>();
          final Map<String, MValue> globals = new HashMap<>();

          @Override
          public MValue get(String name) {
            if (name.startsWith("^")) return globals.getOrDefault(name, MValue.EMPTY);
            return vars.getOrDefault(name, MValue.EMPTY);
          }

          @Override
          public void set(String name, MValue v) {
            if (name.startsWith("^")) globals.put(name, v);
            else vars.put(name, v);
          }

          @Override
          public MValue extrinsic(String name, List<MValue> a, List<String> byrefs) {
            StringBuilder sb = new StringBuilder("EXT:").append(name);
            for (int k = 0; k < a.size(); k++) {
              String br = byrefs != null && byrefs.get(k) != null ? byrefs.get(k) : null;
              sb.append('|').append(br != null ? "." + br : a.get(k).asStr());
            }
            return MValue.ofString(sb.toString());
          }
        };
    ExprEval ev = new ExprEval(env);
    ev.set("x", MValue.ofNumber(5));
    ev.set("i", MValue.ofNumber(2));
    ev.set("name", MValue.ofString("abc"));
    env.set("a(1)", MValue.ofNumber(42));
    env.set("a(1,\"x\")", MValue.ofString("v"));
    env.set("^G(1)", MValue.ofNumber(7));
    env.set("^G(1,2)", MValue.ofString("g12"));

    String[] battery = {
      // arithmetic (strictly left-to-right in M — C30)
      "1+2*3",
      "2**3**2",
      "10-4-3",
      "7#3",
      "-7#3",
      "7\\2",
      "7/2",
      "-5+3",
      "+5",
      "1+2",
      // concat
      "1_2",
      "\"a\"_\"b\"_1",
      "x_\"-\"_i",
      // comparisons (numeric fast path + string + collation)
      "1=1",
      "1'=2",
      "2<10",
      "\"a\"<\"b\"",
      "\"10\"=10",
      "\"1.0\"=1",
      "1]2",
      "\"ab\"[\"b\"",
      "\"ab\"]\"b\"",
      "1]]\"a\"",
      "\"a\"']]\"1\"",
      "2>1",
      "2<=2",
      // logic / unary not
      "1&0",
      "1!0",
      "'1&1",
      "'0",
      "''1",
      // patterns
      "\"abc\"?3A",
      "\"abc\"'?1N",
      "\"a1b2\"?1A1N1A1N",
      "name?3A",
      // intrinsics (generic path)
      "$P(\"a^b^c\",\"^\",2)",
      "$P(name,\"b\",1)",
      "$L(\"abc\")",
      "$E(\"abc\",2)",
      "$E(name,1,2)",
      "$F(255,\"X\")",
      "$J(5,3,2)",
      "$TR(\"aAbB\",\"AB\",\"xy\")",
      "$A(\"A\")",
      "$C(65)",
      "$P($P(\"a^b^c^d\",\"^\",2),\"^\",1)",
      // ref intrinsics ($D/$G/$O family)
      "$D(^G(1))",
      "$D(a(1,\"x\"))",
      "$G(^G(1,2))",
      "$G(^G)",
      "$O(^G)",
      "$D(a)",
      // variables / subscripts
      "x+1",
      "a(1)",
      "a(1,\"x\")",
      "^G(1)",
      "^G(1,2)",
      "a(i)",
      "x",
      // extrinsics incl. byref
      "$$FN(1,2)",
      "$$FN(.x)",
      "$$FN(x,.i)",
      // indirection
      "@(\"x\")",
      "@(\"a(1)\")",
      // parens
      "(1+2)",
      "((x))",
      // $X/$Y, $TEXT
      "$X",
      "$Y",
      "$TEXT(+1)",
      // long expression (>31 chars, misses MRuntime fast path)
      "$P($P($P(\"a^b^c^d^e\",\"^\",3),\"^\",1),\"^\",1)_\"-\"_$L(\"abcdef\")",
    };

    for (String src : battery) {
      check(ev, src);
    }

    // $SELECT must fall back to legacy and still match
    String sel = "$S(x>3:\"big\",1:\"small\")";
    MValue a = ev.eval(sel);
    MValue b = ev.evalLegacy(sel);
    boolean inLedger = ExprEval.noAstSrcs().contains(sel);
    if (a.asStr().equals(b.asStr()) && inLedger) {
      pass++;
      System.out.println("  ok (fallback ledger) " + sel + " = " + a.asStr());
    } else {
      fail++;
      System.out.println(
          "  FAIL " + sel + ": ast=" + a.asStr() + " legacy=" + b.asStr() + " inNoAst=" + inLedger);
    }

    // error case: both paths must throw MError on trailing input
    try {
      ev.eval("1+");
      fail++;
      System.out.println("  FAIL: 1+ did not throw via AST path");
    } catch (MError e) {
      try {
        ev.evalLegacy("1+");
        fail++;
        System.out.println("  FAIL: 1+ did not throw via legacy path");
      } catch (MError e2) {
        pass++;
        System.out.println("  ok (both throw) 1+ : " + e.getMessage());
      }
    }

    // cache idempotence: second eval of a cached expr gives the same answer
    MValue r1 = ev.eval("x+1");
    MValue r2 = ev.eval("x+1");
    if (r1.asStr().equals(r2.asStr())) {
      pass++;
      System.out.println("  ok (cache idempotent) x+1 = " + r1.asStr());
    } else {
      fail++;
      System.out.println("  FAIL cache idempotence: " + r1.asStr() + " vs " + r2.asStr());
    }

    // mjvm#43: IR-level node cache — evalOp (node cached on the Ir op) must match evalExpr,
    // including the $SELECT legacy fallback and op-cache idempotence
    {
      var rt = new mjvm.runtime.MRuntime(new MStore());
      rt.setVar("x", MValue.ofNumber(5));
      String[] ops = {
        "x+1", "$P(\"a^b\",\"^\",2)", "$S(x>3:\"big\",1:\"small\")", "1+2*3", "x_\"!\""
      };
      for (String src : ops) {
        mjvm.ir.Ir op = new mjvm.ir.Ir(mjvm.ir.Ir.EVAL_EXPR, "", src, 0, 0);
        MValue a1 = rt.evalOp(op);
        MValue a2 = rt.evalOp(op); // second call uses the node cached on the op
        MValue be = rt.evalExpr(src);
        if (fmt(a1).equals(fmt(a2)) && fmt(a1).equals(fmt(be))) {
          pass++;
          System.out.println("  ok evalOp " + src + " = " + fmt(a1));
        } else {
          fail++;
          System.out.println(
              "  FAIL evalOp " + src + ": op=" + fmt(a1) + "/" + fmt(a2) + " evalExpr=" + fmt(be));
        }
      }
    }

    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(
        fail == 0 ? "  RESULT: ExprAst differential OK" : "  RESULT: ExprAst differential FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  static void check(ExprEval ev, String src) {
    String astVal, legVal, astRef, legRef;
    try {
      ev.lastRef = null;
      MValue a = ev.eval(src);
      astVal = fmt(a);
      astRef = ev.lastRef;
    } catch (MError e) {
      astVal = "ERR:" + e.getMessage();
      astRef = null;
    }
    try {
      ev.lastRef = null;
      MValue b = ev.evalLegacy(src);
      legVal = fmt(b);
      legRef = ev.lastRef;
    } catch (MError e) {
      legVal = "ERR:" + e.getMessage();
      legRef = null;
    }
    if (astVal.equals(legVal) && Objects.equals(astRef, legRef)) {
      pass++;
      System.out.println(
          "  ok " + src + " = " + astVal + (astRef != null ? "  lastRef=" + astRef : ""));
    } else {
      fail++;
      System.out.println(
          "  FAIL " + src + ": ast=" + astVal + "/" + astRef + " legacy=" + legVal + "/" + legRef);
    }
  }

  static String fmt(MValue v) {
    if (v == null) return "null";
    return v.isnum ? "N:" + ExprEval.mstr(v.asNum()) : "S:" + v.asStr();
  }
}
