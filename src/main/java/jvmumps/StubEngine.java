package jvmumps;

import java.util.HashMap;
import java.util.Map;

/**
 * J0 placeholder engine: holds a symbol table and echoes trivial SET/WRITE. Enough for the corpus
 * *runner* gate; J1–J2 replace this with real eval.
 */
public final class StubEngine implements Engine {
  private final Map<String, MValue> vars = new HashMap<>();

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
    // Minimal SET X=literal (J0 smoke only)
    if (s.startsWith("S ") || s.startsWith("SET ")) {
      int eq = s.indexOf('=');
      if (eq > 0) {
        String name = s.substring(s.indexOf(' ') + 1, eq).trim();
        String rhs = s.substring(eq + 1).trim();
        if (rhs.startsWith("\"") && rhs.endsWith("\"") && rhs.length() >= 2) {
          vars.put(name, MValue.ofString(rhs.substring(1, rhs.length() - 1)));
        } else {
          try {
            vars.put(name, MValue.ofNumber(Double.parseDouble(rhs)));
          } catch (NumberFormatException e) {
            vars.put(name, MValue.ofString(rhs));
          }
        }
      }
      return "";
    }
    if (s.startsWith("W ") || s.startsWith("WRITE ")) {
      String arg = s.substring(s.indexOf(' ')).trim();
      if (arg.startsWith("\"") && arg.contains("\",!") || arg.endsWith("\"")) {
        // WRITE "text" or WRITE "text",!
        int end = arg.indexOf('"', 1);
        String text = end > 0 ? arg.substring(1, end) : "";
        return text + "\n";
      }
      if (vars.containsKey(arg)) {
        return vars.get(arg).asStr() + "\n";
      }
      return "";
    }
    return "";
  }
}
