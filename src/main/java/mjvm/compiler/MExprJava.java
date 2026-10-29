package mjvm.compiler;

/**
 * M expression → Java source transpiler. Compiles simple M expressions to direct Java code (no
 * rt.evalExpr() call). Falls back to rt.evalExpr() for complex expressions.
 *
 * <p>Handles: literals, locals (%V), globals (^G(sub)), arithmetic (+,-,*,/), comparisons
 * (=,<,>,<=,>=,=,'<,'>), string concat (_), $G(), $I(), $R(), function calls ($$fn).
 */
public final class MExprJava {

  /**
   * Try to compile an M expression to Java source that pushes onto the value stack. Returns null if
   * the expression is too complex.
   */
  public static String compile(String expr, String stackVar, String pcVar) {
    if (expr == null || expr.isEmpty()) return null;
    expr = expr.trim();
    // Try simple patterns
    String r = tryCompile(expr, stackVar, pcVar);
    return r;
  }

  static String tryCompile(String e, String s, String p) {
    // Literal number
    if (e.matches("-?\\d+(\\.\\d+)?")) {
      return "    " + s + "[" + p + "++] = jvmumps.MValue.ofNumber(" + e + ");\n";
    }
    // Literal string
    if (e.matches("\"[^\"]*\"")) {
      String inner = e.substring(1, e.length() - 1);
      return "    " + s + "[" + p + "++] = jvmumps.MValue.ofString(\"" + esc(inner) + "\");\n";
    }
    // Local variable: %V
    if (e.matches("%[A-Za-z][A-Za-z0-9]*")) {
      return "    " + s + "[" + p + "++] = rt.getVar(\"" + esc(e) + "\");\n";
    }
    // $G(global) — get global value
    if (e.startsWith("$G(") && e.endsWith(")")) {
      String inner = e.substring(3, e.length() - 1);
      String gl = compileGlobalRef(inner, s, p);
      if (gl != null) {
        return "    " + s + "[" + p + "++] = rt.getVar(" + gl + ");\n";
      }
    }
    // $I(global) — atomic increment
    if (e.startsWith("$I(") && e.endsWith(")")) {
      String inner = e.substring(3, e.length() - 1);
      String gl = compileGlobalRef(inner, s, p);
      if (gl != null) {
        return "    " + s + "[" + p + "++] = rt.increment(" + gl + ", true, 1);\n";
      }
    }
    // $R(n) — random 0..n-1
    if (e.matches("\\$R\\(\\d+\\)")) {
      String n = e.substring(3, e.length() - 1);
      return "    "
          + s
          + "["
          + p
          + "++] ="
          + " jvmumps.MValue.ofNumber(java.util.concurrent.ThreadLocalRandom.current().nextInt("
          + n
          + "));\n";
    }
    // Arithmetic: A+B, A-B, A*B, A/B where A and B are simple
    for (String op : new String[] {"+", "-", "*", "/"}) {
      int idx = findTopLevel(e, op);
      if (idx > 0) {
        String left = e.substring(0, idx).trim();
        String right = e.substring(idx + 1).trim();
        String jl = tryCompileToNum(left);
        String jr = tryCompileToNum(right);
        if (jl != null && jr != null) {
          String jop =
              switch (op) {
                case "+" -> "+";
                case "-" -> "-";
                case "*" -> "*";
                default -> "/";
              };
          String code = jl + jr;
          if (op.equals("/")) {
            code +=
                "    { double y = "
                    + s
                    + "["
                    + p
                    + "-1].asNum(); double x = "
                    + s
                    + "["
                    + p
                    + "-2].asNum(); "
                    + s
                    + "["
                    + p
                    + "-2] = jvmumps.MValue.ofNumber(y==0?0:x/y); "
                    + p
                    + "; }\n";
          } else {
            code +=
                "    "
                    + s
                    + "["
                    + p
                    + "-2] = jvmumps.MValue.ofNumber("
                    + s
                    + "["
                    + p
                    + "-2].asNum() "
                    + jop
                    + " "
                    + s
                    + "["
                    + p
                    + "-1].asNum()); "
                    + p
                    + "--;\n";
          }
          return code;
        }
      }
    }
    // Comparison: A=B, A<B, A>B, A<=B, A>=B, A'=B, A'<B, A'>B
    // No '<= / '>=: GT.M rejects those constructs (RHMISSING); leaving them out makes the
    // emitter fall back to the runtime evaluator, which rejects them too.
    for (String op : new String[] {"'=", "'<", "'>", "<=", ">=", "=", "<", ">"}) {
      int idx = findTopLevel(e, op);
      if (idx > 0) {
        String left = e.substring(0, idx).trim();
        String right = e.substring(idx + op.length()).trim();
        String jl = tryCompileToNum(left);
        String jr = tryCompileToNum(right);
        if (jl != null && jr != null) {
          String jop =
              switch (op) {
                case "=", "'=" -> "==";
                case "<", "'<" -> "<";
                case ">", "'>" -> ">";
                case "<=" -> "<=";
                case ">=" -> ">=";
                default -> "==";
              };
          boolean negate = op.startsWith("'");
          String code = jl + jr;
          code +=
              "    { double y = "
                  + s
                  + "["
                  + p
                  + "-1].asNum(); double x = "
                  + s
                  + "["
                  + p
                  + "-2].asNum(); "
                  + s
                  + "["
                  + p
                  + "-2] = jvmumps.MValue.ofNumber((x "
                  + jop
                  + " y) "
                  + (negate ? "? 0 : 1" : "? 1 : 0")
                  + "); "
                  + p
                  + "--; }\n";
          return code;
        }
      }
    }
    return null; // too complex
  }

  /** Compile expression to Java code that pushes a numeric value. Returns null if not numeric. */
  static String tryCompileToNum(String e) {
    String r = tryCompile(e, "s", "p");
    return r;
  }

  /**
   * Compile a function call like $$fn(args) or fn^RTN(args) to Java source. Generates a call to a
   * generated BankJava method.
   */
  public static String compileFuncCall(String expr, String s, String p) {
    if (expr == null) return null;
    expr = expr.trim();
    // $$fn(args) pattern
    if (expr.startsWith("$$") && expr.contains("(") && expr.endsWith(")")) {
      int paren = expr.indexOf('(');
      String fnName = expr.substring(2, paren);
      String args = expr.substring(paren + 1, expr.length() - 1);
      return compileCall(fnName, args, s, p);
    }
    // fn^RTN(args) pattern
    if (expr.contains("^") && expr.contains("(") && expr.endsWith(")")) {
      int paren = expr.indexOf('(');
      String full = expr.substring(0, paren);
      String args = expr.substring(paren + 1, expr.length() - 1);
      int caret = full.indexOf('^');
      if (caret > 0) {
        String fnName = full.substring(0, caret);
        return compileCall(fnName, args, s, p);
      }
    }
    // Bare fn(args) pattern — DISABLED (javabank.BankM not pre-compiled)
    // Falls back to rt.evalExpr which handles all cases correctly.
    return null;
  }

  static String compileCall(String fnName, String args, String s, String p) {
    // Strip ^RTN suffix if present
    int caret = fnName.indexOf('^');
    if (caret > 0) fnName = fnName.substring(0, caret);
    // Route to BankFullM
    String[] argParts = args.split(",");
    StringBuilder sb = new StringBuilder();
    sb.append("    s[p++] = javabank.BankFullM.Bank_").append(fnName).append("(rt");
    for (String arg : argParts) {
      arg = arg.trim();
      if (arg.isEmpty()) continue;
      sb.append(", ");
      // Compile arg to Java expression
      if (arg.matches("%[A-Za-z][A-Za-z0-9]*")) {
        sb.append("rt.getVar(\"").append(esc(arg)).append("\")");
      } else if (arg.matches("-?\\d+(\\.\\d+)?")) {
        sb.append("jvmumps.MValue.ofNumber(").append(arg).append(")");
      } else if (arg.matches("\"[^\"]*\"")) {
        sb.append("jvmumps.MValue.ofString(\"")
            .append(esc(arg.substring(1, arg.length() - 1)))
            .append("\")");
      } else {
        // Complex arg — use evalExpr
        sb.append("rt.evalExpr(\"").append(esc(arg)).append("\")");
      }
    }
    sb.append(");\n");
    return sb.toString();
  }

  /**
   * Compile global reference like ^BANK("C",%AC) to Java string expression. Returns null if
   * complex.
   */
  /** Compile global ref with variable subscripts — generates string concat code. */
  static String compileGlobalRefWithVars(String name, String[] parts, String s, String p) {
    StringBuilder code = new StringBuilder();
    code.append("    { String __k = \"").append(esc(name)).append("\";");
    for (String part : parts) {
      part = part.trim();
      code.append(" __k += \"\u0001\";");
      if (part.matches("%[A-Za-z][A-Za-z0-9]*")) {
        code.append(" __k += rt.getVar(\"").append(esc(part)).append("\").asStr();");
      } else if (part.matches("\"[^\"]*\"")) {
        code.append(" __k += \"").append(esc(part.substring(1, part.length() - 1))).append("\";");
      } else if (part.matches("\\d+")) {
        code.append(" __k += \"").append(part).append("\";");
      } else {
        return null;
      }
    }
    code.append(" ").append(s).append("[").append(p).append("++] = rt.getVar(__k); }\n");
    return code.toString();
  }

  static String compileGlobalRef(String e, String s, String p) {
    if (!e.startsWith("^")) return null;
    // Simple global: ^NAME
    if (e.matches("\\^[A-Za-z][A-Za-z0-9]*")) {
      return "\"" + esc(e) + "\"";
    }
    // Global with subscripts: ^NAME(sub1,sub2,...)
    int paren = e.indexOf('(');
    if (paren > 0 && e.endsWith(")")) {
      String name = e.substring(0, paren);
      String subs = e.substring(paren + 1, e.length() - 1);
      // Simple subscripts: literal strings, %vars, or numbers
      String[] parts = subs.split(",");
      StringBuilder sb = new StringBuilder("\"");
      sb.append(esc(name));
      for (String part : parts) {
        part = part.trim();
        if (part.matches("%[A-Za-z][A-Za-z0-9]*")) {
          // Variable subscript — generate string concat code
          return compileGlobalRefWithVars(name, parts, s, p);
        }
        sb.append("\\u0001");
        if (part.matches("\"[^\"]*\"")) {
          sb.append(esc(part.substring(1, part.length() - 1)));
        } else if (part.matches("\\d+")) {
          sb.append(part);
        } else {
          return null;
        }
      }
      sb.append("\"");
      return sb.toString();
    }
    return null;
  }

  /** Find top-level operator occurrence (not inside parens or strings). */
  static int findTopLevel(String e, String op) {
    int depth = 0;
    boolean inStr = false;
    for (int i = 0; i < e.length() - op.length() + 1; i++) {
      char c = e.charAt(i);
      if (c == '"') inStr = !inStr;
      if (inStr) continue;
      if (c == '(') depth++;
      if (c == ')') depth--;
      if (depth == 0 && e.startsWith(op, i)) {
        // Don't match = in <=, >=, '=
        if (op.equals("=") && i > 0) {
          char prev = e.charAt(i - 1);
          if (prev == '<' || prev == '>' || prev == '\'') continue;
        }
        if (op.equals("<") && i + 1 < e.length() && e.charAt(i + 1) == '=') continue;
        if (op.equals(">") && i + 1 < e.length() && e.charAt(i + 1) == '=') continue;
        return i;
      }
    }
    return -1;
  }

  static String esc(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
