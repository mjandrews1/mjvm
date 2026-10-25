package jvmumps;

import java.util.HashMap;
import java.util.Map;

/** J1: evaluate M expressions via ExprEval (string + numeric). */
/** Expression-only engine: evaluate M expr strings via {@link ExprEval}. */
public final class ExprEngine implements Engine {
  private final Map<String, MValue> vars = new HashMap<>();
  private final ExprEval eval =
      new ExprEval(
          new ExprEval.Env() {
            @Override
            public MValue get(String name) {
              MValue v = vars.get(name);
              return v == null ? MValue.EMPTY : v;
            }

            @Override
            public void set(String name, MValue v) {
              vars.put(name, v);
            }
          });

  @Override
  public void reset() {
    vars.clear();
  }

  @Override
  public String evalLine(String line) {
    if (line == null) {
      return "";
    }
    String s = line.trim();
    if (s.isEmpty()) {
      return "";
    }
    // W expr
    if (s.startsWith("W ") || s.startsWith("WRITE ")) {
      return doWrite(s.substring(s.indexOf(' ')).trim());
    }
    // S name=expr
    if (s.startsWith("S ") || s.startsWith("SET ")) {
      String rest = s.substring(s.indexOf(' ') + 1).trim();
      int eq = rest.indexOf('=');
      if (eq > 0) {
        String name = rest.substring(0, eq).trim();
        String rhs = rest.substring(eq + 1).trim();
        vars.put(name, eval.eval(rhs));
      }
      return "";
    }
    // bare expression (dev)
    return eval.eval(s).asStr();
  }

  private String doWrite(String args) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < args.length()) {
      char c = args.charAt(i);
      if (c == ',') {
        i++;
        continue;
      }
      if (c == '!') {
        out.append('\n');
        i++;
        continue;
      }
      if (c == '?') {
        // ?n column — skip for J1
        i++;
        while (i < args.length() && Character.isDigit(args.charAt(i))) {
          i++;
        }
        continue;
      }
      // expression up to top-level comma
      int start = i;
      int depth = 0;
      boolean inStr = false;
      while (i < args.length()) {
        char d = args.charAt(i);
        if (inStr) {
          if (d == '"') {
            inStr = false;
          }
          i++;
          continue;
        }
        if (d == '"') {
          inStr = true;
          i++;
          continue;
        }
        if (d == '(') {
          depth++;
        } else if (d == ')') {
          depth--;
        } else if (d == ',' && depth == 0) {
          break;
        }
        i++;
      }
      String ex = args.substring(start, i).trim();
      if (ex.equals("!") || ex.isEmpty()) {
        // handled above / empty
      } else if (!ex.equals("!")) {
        MValue v = eval.eval(ex);
        out.append(v.isnum ? ExprEval.mstr(v.num) : v.str);
      }
    }
    return out.toString();
  }
}
