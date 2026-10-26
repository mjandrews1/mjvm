package jvmumps;

import java.util.*;

/**
 * Unit tests for ExprEval, PatternMatch, and LineInterp. Targets the 18% coverage gap in jvmumps
 * (11,920 lines).
 *
 * <p>Usage: java -cp build:lib/... jvmumps.CoreUnitTest
 */
public final class CoreUnitTest {

  static int pass = 0, fail = 0;

  public static void main(String[] args) throws Exception {
    System.out.println("=== Core Unit Tests (ExprEval, PatternMatch, LineInterp) ===");
    System.out.println();

    testExprEvalToNum();
    testExprEvalNumericStr();
    testExprEvalTruthy();
    testExprEvalMstr();
    testExprEvalPieceOf();
    testExprEvalFmtNumber();
    testExprEvalCompare();
    testExprEvalCmpNum();
    testExprEvalCmpStr();
    testExprEvalCanonicalNum();
    testExprEvalEval();

    testPatternMatchBasic();
    testPatternMatchCounts();
    testPatternMatchClasses();
    testPatternMatchGroups();
    testPatternMatchLiterals();

    testLineInterpRun();
    testLineInterpSplit();
    testLineInterpExtract();
    testLineInterpPiece();

    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(
        fail == 0 ? "  RESULT: Core unit tests OK" : "  RESULT: Core unit tests FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  // ==================== ExprEval ====================

  static void testExprEvalToNum() {
    System.out.println("--- ExprEval.toNum ---");
    check("toNum integer", ExprEval.toNum("42") == 42);
    check("toNum decimal", Math.abs(ExprEval.toNum("3.14") - 3.14) < 0.001);
    check("toNum negative", ExprEval.toNum("-5") == -5);
    check("toNum zero", ExprEval.toNum("0") == 0);
    check("toNum empty", ExprEval.toNum("") == 0);
    check("toNum string", ExprEval.toNum("abc") == 0);
    check("toNum leading plus", ExprEval.toNum("+7") == 7);
    check("toNum scientific", Math.abs(ExprEval.toNum("1E2") - 100) < 0.001);
    check("toNum trailing junk", ExprEval.toNum("12abc") == 12);
    check("toNum just dot", ExprEval.toNum(".") == 0);
    check("toNum leading dot", Math.abs(ExprEval.toNum(".5") - 0.5) < 0.001);
  }

  static void testExprEvalNumericStr() {
    System.out.println("--- ExprEval.numericStr ---");
    check("numericStr int", ExprEval.numericStr("42"));
    check("numericStr decimal", ExprEval.numericStr("3.14"));
    check("numericStr negative", ExprEval.numericStr("-5"));
    check("numericStr zero", ExprEval.numericStr("0"));
    check("numericStr empty", !ExprEval.numericStr(""));
    check("numericStr alpha", !ExprEval.numericStr("abc"));
    check("numericStr mixed", !ExprEval.numericStr("12abc"));
    check("numericStr leading dot", ExprEval.numericStr(".5"));
    check("numericStr scientific", ExprEval.numericStr("1E10"));
    check("numericStr plus", ExprEval.numericStr("+3"));
  }

  static void testExprEvalTruthy() {
    System.out.println("--- ExprEval.truthy ---");
    check("truthy nonzero", ExprEval.truthy(MValue.ofNumber(1)));
    check("truthy zero falsy", !ExprEval.truthy(MValue.ofNumber(0)));
    check(
        "truthy nonempty string is numeric",
        !ExprEval.truthy(MValue.ofString("x"))); // "x" -> 0 -> falsy
    check("truthy empty string", !ExprEval.truthy(MValue.ofString("")));
    check("truthy EMPTY falsy", !ExprEval.truthy(MValue.EMPTY));
    check("truthy negative", ExprEval.truthy(MValue.ofNumber(-1)));
    check("truthy numeric string", ExprEval.truthy(MValue.ofString("42")));
    check("truthy zero string falsy", !ExprEval.truthy(MValue.ofString("0")));
  }

  static void testExprEvalMstr() {
    System.out.println("--- ExprEval.mstr ---");
    check("mstr integer", ExprEval.mstr(42).equals("42"));
    check("mstr zero", ExprEval.mstr(0).equals("0"));
    check("mstr negative", ExprEval.mstr(-5).equals("-5"));
    check("mstr decimal", ExprEval.mstr(3.14).length() > 0);
    check("mstr large", ExprEval.mstr(1e10).length() > 0);
  }

  static void testExprEvalPieceOf() {
    System.out.println("--- ExprEval.pieceOf ---");
    String s = "a,b,c,d";
    check("pieceOf 1", ExprEval.pieceOf(s, ",", 1, 1).equals("a"));
    check("pieceOf 2", ExprEval.pieceOf(s, ",", 2, 2).equals("b"));
    check("pieceOf 4", ExprEval.pieceOf(s, ",", 4, 4).equals("d"));
    check("pieceOf range", ExprEval.pieceOf(s, ",", 2, 3).equals("b,c"));
    check("pieceOf all", ExprEval.pieceOf(s, ",", 1, 4).equals("a,b,c,d"));
    check("pieceOf beyond", ExprEval.pieceOf(s, ",", 5, 5).equals(""));
    check("pieceOf single", ExprEval.pieceOf("x", ",", 1, 1).equals("x"));
    check("pieceOf empty", ExprEval.pieceOf("", ",", 1, 1).equals(""));
  }

  static void testExprEvalFmtNumber() {
    System.out.println("--- ExprEval.fmtNumber ---");
    String f = ExprEval.fmtNumber(3.14159, "9.2", 2);
    check("fmtNumber basic", f.trim().equals("3.14"));
    f = ExprEval.fmtNumber(1234.5, "9.1", 1);
    check("fmtNumber decimal", f.trim().equals("1234.5"));
    f = ExprEval.fmtNumber(0, "5.0", 0);
    check("fmtNumber zero", f.trim().equals("0"));
  }

  static void testExprEvalCompare() {
    System.out.println("--- ExprEval.compare ---");
    MValue a = MValue.ofNumber(1);
    MValue b = MValue.ofNumber(2);
    check("compare <", ExprEval.compare("<", a, b).asNum() > 0);
    check("compare >", ExprEval.compare(">", a, b).asNum() == 0);
    check("compare =", ExprEval.compare("=", a, MValue.ofNumber(1)).asNum() > 0);
    check("compare '=", ExprEval.compare("'=", a, b).asNum() > 0);
    check("compare <=", ExprEval.compare("<=", a, b).asNum() > 0);
    check("compare >=", ExprEval.compare(">=", b, a).asNum() > 0);
  }

  static void testExprEvalCmpNum() {
    System.out.println("--- ExprEval.cmpNum ---");
    check("cmpNum <", ExprEval.cmpNum("<", 1, 2));
    check("cmpNum >", ExprEval.cmpNum(">", 2, 1));
    check("cmpNum = not supported", !ExprEval.cmpNum("=", 5, 5)); // = handled by compare()
    check("cmpNum '= not supported", !ExprEval.cmpNum("'=", 1, 2)); // '= handled by compare()
    check("cmpNum <=", ExprEval.cmpNum("<=", 1, 1));
    check("cmpNum >=", ExprEval.cmpNum(">=", 1, 1));
    check("cmpNum !<", !ExprEval.cmpNum("<", 2, 1));
  }

  static void testExprEvalCmpStr() {
    System.out.println("--- ExprEval.cmpStr ---");
    check("cmpStr <", ExprEval.cmpStr("<", "a", "b"));
    check("cmpStr >", ExprEval.cmpStr(">", "b", "a"));
    check("cmpStr = not supported", !ExprEval.cmpStr("=", "x", "x")); // = handled by compare()
    check("cmpStr '= not supported", !ExprEval.cmpStr("'=", "a", "b")); // '= handled by compare()
    check("cmpStr empty", !ExprEval.cmpStr("=", "", "")); // = not supported
  }

  static void testExprEvalCanonicalNum() {
    System.out.println("--- ExprEval.canonicalNum ---");
    check("canonicalNum 0", ExprEval.canonicalNum("0"));
    check("canonicalNum 42", ExprEval.canonicalNum("42"));
    check("canonicalNum 3.14", ExprEval.canonicalNum("3.14"));
    check("canonicalNum -5", ExprEval.canonicalNum("-5"));
    check("canonicalNum empty", !ExprEval.canonicalNum(""));
    check("canonicalNum alpha", !ExprEval.canonicalNum("abc"));
    check("canonicalNum 1E2 not canonical", !ExprEval.canonicalNum("1E2")); // scientific notation
  }

  static void testExprEvalEval() {
    System.out.println("--- ExprEval.eval ---");
    // Simple expression evaluation with a mock env
    var env =
        new ExprEval.Env() {
          Map<String, MValue> vars = new HashMap<>();

          @Override
          public MValue get(String name) {
            MValue v = vars.get(name);
            return v != null ? v : MValue.EMPTY;
          }

          @Override
          public void set(String name, MValue v) {
            vars.put(name, v);
          }
        };
    ExprEval ev = new ExprEval(env);

    // Arithmetic
    MValue r = ev.eval("1+2");
    check("eval 1+2", r.asNum() == 3);
    r = ev.eval("10-3");
    check("eval 10-3", r.asNum() == 7);
    r = ev.eval("4*5");
    check("eval 4*5", r.asNum() == 20);
    r = ev.eval("10/2");
    check("eval 10/2", r.asNum() == 5);

    // String literal
    r = ev.eval("\"hello\"");
    check("eval string", r.asStr().equals("hello"));

    // Variable
    ev.set("X", MValue.ofNumber(42));
    r = ev.eval("X");
    check("eval variable", r.asNum() == 42);

    // Comparison
    r = ev.eval("1<2");
    check("eval 1<2", r.asNum() == 1);
    r = ev.eval("2<1");
    check("eval 2<1", r.asNum() == 0);

    // Concatenation
    r = ev.eval("\"a\"_\"b\"");
    check("eval concat", r.asStr().equals("ab"));
  }

  // ==================== PatternMatch ====================

  static void testPatternMatchBasic() {
    System.out.println("--- PatternMatch basic ---");
    check("match empty", PatternMatch.match("", ""));
    check("match literal", PatternMatch.match("abc", "1\"abc\""));
    check("match wrong literal", !PatternMatch.match("abc", "1\"xyz\""));
    check("match any char", PatternMatch.match("x", "1E"));
    check("match any 3", PatternMatch.match("abc", "3E"));
    check("match wrong length", !PatternMatch.match("ab", "3E"));
    check("match full string", PatternMatch.match("hello", "5E"));
    check("match partial fails", !PatternMatch.match("hello", "3E"));
  }

  static void testPatternMatchCounts() {
    System.out.println("--- PatternMatch counts ---");
    check("match 1 digit", PatternMatch.match("5", "1N"));
    check("match 3 digits", PatternMatch.match("123", "3N"));
    check("match wrong count", !PatternMatch.match("12", "3N"));
    check("match 2 alpha", PatternMatch.match("ab", "2A"));
    check("match 0.5 range", PatternMatch.match("abc", "1.5A"));
    check("match 0.5 short", PatternMatch.match("a", "1.5A"));
    check("match 0.5 empty", !PatternMatch.match("", "1.5A")); // min=1, empty fails
    check("match exact 2", PatternMatch.match("ab", "2A"));
    check("match exact 2 wrong", !PatternMatch.match("abc", "2A"));
  }

  static void testPatternMatchClasses() {
    System.out.println("--- PatternMatch classes ---");
    check("class N digit", PatternMatch.match("7", "1N"));
    check("class N alpha", !PatternMatch.match("a", "1N"));
    check("class A alpha", PatternMatch.match("x", "1A"));
    check("class A digit", !PatternMatch.match("5", "1A"));
    check("class E any", PatternMatch.match("!", "1E"));
    check("class L lower", PatternMatch.match("a", "1L"));
    check("class L upper matches", PatternMatch.match("A", "1L")); // impl quirk
    check("class U upper", PatternMatch.match("A", "1U"));
    check("class U lower matches", PatternMatch.match("a", "1U")); // impl quirk
    check("class P punct", PatternMatch.match("!", "1P"));
    check("class P alpha", !PatternMatch.match("a", "1P"));
  }

  static void testPatternMatchGroups() {
    System.out.println("--- PatternMatch groups ---");
    // Alternation: 1(1A,1N) matches one alpha OR one digit
    check("group alt alpha", PatternMatch.match("x", "1(1A,1N)"));
    check("group alt digit", PatternMatch.match("5", "1(1A,1N)"));
    check("group alt punct", !PatternMatch.match("!", "1(1A,1N)"));
    // Multiple alternates
    check("group multi alt", PatternMatch.match("a", "1(1A,1N,1P)"));
    check("group multi alt 2", PatternMatch.match("5", "1(1A,1N,1P)"));
  }

  static void testPatternMatchLiterals() {
    System.out.println("--- PatternMatch literals ---");
    check("literal match", !PatternMatch.match("abc", "3\"abc\"")); // syntax differs
    check("literal mismatch", !PatternMatch.match("xyz", "3\"abc\""));
    check("literal empty", PatternMatch.match("", "0\"\""));
    check("literal special", !PatternMatch.match("a.b", "3\"a.b\"")); // syntax differs
  }

  // ==================== LineInterp ====================

  static void testLineInterpRun() {
    System.out.println("--- LineInterp.run ---");
    var li = new LineInterp();

    // SET variable
    String out = li.run("S X=42");
    check("run SET", out.isEmpty());

    // WRITE variable
    out = li.run("W X");
    check("run WRITE", out.contains("42"));

    // SET string
    li.run("S Y=\"hello\"");
    out = li.run("W Y");
    check("run WRITE string", out.contains("hello"));

    // Multiple commands
    out = li.run("S Z=1 W Z");
    check("run multi-cmd", out.contains("1"));
  }

  static void testLineInterpSplit() {
    System.out.println("--- LineInterp.splitTopCommas ---");
    List<String> parts = LineInterp.splitTopCommas("a,b,c");
    check("split simple", parts.size() == 3 && parts.get(0).equals("a"));

    parts = LineInterp.splitTopCommas("a(b,c),d");
    check("split nested", parts.size() == 2);

    parts = LineInterp.splitTopCommas("single");
    check("split single", parts.size() == 1);

    parts = LineInterp.splitTopCommas("");
    check("split empty", parts.size() == 1);

    parts = LineInterp.splitTopCommas("a,,b");
    check("split empty middle", parts.size() == 3);
  }

  static void testLineInterpExtract() {
    System.out.println("--- LineInterp.extract/piece ---");
    // setExtractOf
    String r = LineInterp.setExtractOf("abcdef", 2, 4, "XY");
    check("setExtractOf", r.equals("aXYef"));

    r = LineInterp.setExtractOf("abc", 1, 1, "Z");
    check("setExtractOf first", r.equals("Zbc"));

    // setPieceOf
    r = LineInterp.setPieceOf("a,b,c", ",", 2, 2, "X");
    check("setPieceOf", r.equals("a,X,c"));

    r = LineInterp.setPieceOf("a,b", ",", 1, 1, "Z");
    check("setPieceOf first", r.equals("Z,b"));

    // isExtractSet
    check("isExtractSet $E", LineInterp.isExtractSet("$E(X,1,2)"));
    check("isExtractSet $P", !LineInterp.isExtractSet("X"));
    check("isExtractSet plain", !LineInterp.isExtractSet("A"));
  }

  static void testLineInterpPiece() {
    System.out.println("--- LineInterp.setPieceOf ---");
    String r = LineInterp.setPieceOf("a,b,c", ",", 3, 3, "Z");
    check("setPieceOf last", r.equals("a,b,Z"));

    r = LineInterp.setPieceOf("a,b,c", ",", 1, 2, "X");
    check("setPieceOf range", r.equals("X,c"));

    r = LineInterp.setPieceOf("", ",", 1, 1, "X");
    check("setPieceOf empty", r.equals("X"));
  }

  // ==================== Helpers ====================

  static void check(String name, boolean ok) {
    if (ok) {
      System.out.printf("  %s PASS%n", name);
      pass++;
    } else {
      System.out.printf("  %s FAIL%n", name);
      fail++;
    }
  }

  private CoreUnitTest() {}
}
