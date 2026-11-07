package mjvm.compiler;

import java.util.ArrayList;
import java.util.List;

/** Static helpers for {@link MFrontend} (split from god file, #58). */
final class MFrontendUtil {
  private MFrontendUtil() {}

  static java.util.List<String> splitArgsCommas(String spec) {
    List<String> segs = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    int depth = 0;
    boolean inS = false;
    for (int k = 0; k < spec.length(); k++) {
      char c = spec.charAt(k);
      if (inS) {
        sb.append(c);
        if (c == '"') {
          if (k + 1 < spec.length() && spec.charAt(k + 1) == '"') {
            sb.append('"');
            k++;
          } else inS = false;
        }
        continue;
      }
      if (c == '"') {
        inS = true;
        sb.append(c);
        continue;
      }
      if (c == '(') {
        depth++;
        sb.append(c);
        continue;
      }
      if (c == ')') {
        depth--;
        sb.append(c);
        continue;
      }
      if (c == ',' && depth == 0) {
        segs.add(sb.toString().trim());
        sb.setLength(0);
        continue;
      }
      sb.append(c);
    }
    segs.add(sb.toString().trim());
    return segs;
  }

  static java.util.List<String> splitOnColons(String spec) {
    List<String> segs = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    int depth = 0;
    boolean inS = false;
    for (int k = 0; k < spec.length(); k++) {
      char c = spec.charAt(k);
      if (inS) {
        sb.append(c);
        if (c == '"') inS = false;
        continue;
      }
      if (c == '"') {
        inS = true;
        sb.append(c);
        continue;
      }
      if (c == '(') {
        depth++;
        sb.append(c);
        continue;
      }
      if (c == ')') {
        depth--;
        sb.append(c);
        continue;
      }
      if (c == ':' && depth == 0) {
        segs.add(sb.toString());
        sb.setLength(0);
        continue;
      }
      sb.append(c);
    }
    segs.add(sb.toString());
    return segs;
  }

  /**
   * mjvm#17: IF/QUIT-style comma condition lists are ANDed. Split a condition span on top-level
   * commas (not inside strings or parens) and join the parts with & — returns null when there is no
   * top-level comma.
   */
  static String andJoinCommas(String span) {
    java.util.List<String> parts = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    int depth = 0;
    boolean inS = false;
    boolean any = false;
    for (int k = 0; k < span.length(); k++) {
      char c = span.charAt(k);
      if (inS) {
        sb.append(c);
        if (c == '"') {
          if (k + 1 < span.length() && span.charAt(k + 1) == '"') {
            sb.append('"');
            k++;
          } else {
            inS = false;
          }
        }
        continue;
      }
      if (c == '"') {
        inS = true;
        sb.append(c);
        continue;
      }
      if (c == '(') depth++;
      if (c == ')') depth--;
      if (c == ',' && depth == 0) {
        parts.add(sb.toString());
        sb.setLength(0);
        any = true;
        continue;
      }
      sb.append(c);
    }
    if (!any) return null;
    parts.add(sb.toString());
    StringBuilder r = new StringBuilder();
    for (int k = 0; k < parts.size(); k++) {
      if (k > 0) r.append('&');
      r.append('(').append(parts.get(k).trim()).append(')');
    }
    return r.toString();
  }

  /**
   * mjvm#25: advance past an argument list to the next command word. String-, paren- and
   * operator-aware (a naive command-word scan stops at the B in A+B, since B is a command letter
   * after an operator).
   */
  static int skipToNextCmd(String line, int i) {
    while (i < line.length()) {
      i = skipWs(line, i);
      if (i >= line.length() || isCmdStart(line, i)) break;
      int j = scanExprEnd(line, i, " ");
      i = j > i ? j : i + 1;
    }
    return i;
  }

  static String[] readRefName(String line, int[] pos) {
    int i = pos[0];
    i = skipWs(line, i);
    int st = i;
    while (i < line.length()
        && (Character.isLetterOrDigit(line.charAt(i))
            || line.charAt(i) == '%'
            || line.charAt(i) == '^'
            || line.charAt(i) == '$'))
      i++; // ^$W / $ETRAP-style names (VistA swallowed-syntax class)
    if (i < line.length() && line.charAt(i) == '(') {
      int depth = 0;
      while (i < line.length()) {
        char c = line.charAt(i++);
        if (c == '(') depth++;
        else if (c == ')') {
          depth--;
          if (depth == 0) break;
        }
      }
    }
    pos[0] = i;
    return new String[] {line.substring(st, i), String.valueOf(i)};
  }

  static int scanExprEnd(String line, int i, String term) {
    int depth = 0;
    boolean inS = false;
    while (i < line.length()) {
      char c = line.charAt(i);
      if (inS) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            i += 2;
            continue;
          }
          inS = false;
        }
        i++;
        continue;
      }
      if (c == '"') {
        inS = true;
        i++;
        continue;
      }
      // ';' at depth 0 starts a comment — terminate the expression scan here
      // (outside strings), otherwise Q:1 ; c, x swallows the comment into the
      // quit expression and then chokes on the comma (VistA swallowed class)
      if (depth == 0 && c == ';') break;
      if (c == '(') depth++;
      if (c == ')') {
        if (depth == 0 && term.indexOf(')') >= 0) break;
        depth--;
        i++;
        continue;
      }
      // Space at depth 0: if previous non-space char is an operator, spaces are
      // insignificant (e.g. A_"|"_  B). Otherwise space terminates (command separator).
      if (depth == 0 && c == ' ') {
        // Find previous non-space character
        int p = i - 1;
        while (p >= 0 && line.charAt(p) == ' ') p--;
        char prev = p >= 0 ? line.charAt(p) : ' ';
        boolean afterOp =
            prev == '_'
                || prev == '+'
                || prev == '-'
                || prev == '*'
                || prev == '/'
                || prev == '\\'
                || prev == '#'
                || prev == '='
                || prev == '('
                || prev == ',';
        if (!afterOp) {
          // Check for multi-char operators: '= , '< , '> , <= , >=
          if (p >= 1) {
            char prev2 = line.charAt(p - 1);
            if (prev == '=' && (prev2 == '\'' || prev2 == '<' || prev2 == '>')) afterOp = true;
            if (prev == '<' && prev2 == '\'') afterOp = true;
            if (prev == '>' && prev2 == '\'') afterOp = true;
            if (prev == '>' && prev2 == '<') afterOp = true;
            if (prev == '=' && prev2 == '<') afterOp = true;
            if (prev == '=' && prev2 == '>') afterOp = true;
          }
        }
        if (!afterOp) break;
        i++;
        continue;
      }
      if (depth == 0 && term.indexOf(c) >= 0) break;
      i++;
    }
    return i;
  }

  /**
   * True if the command compiler handles its own postconditional (`CMD:COND`). IF is included
   * because its `:cond` is indistinguishable from a subscript separator in its condition argument
   * and the construct is vanishingly rare.
   */
  static boolean handlesOwnPostcond(String cmd) {
    String u = cmd.toUpperCase(java.util.Locale.ROOT);
    return u.equals("S")
        || u.equals("SET")
        || u.equals("W")
        || u.equals("WRITE")
        || u.equals("Q")
        || u.equals("QUIT")
        || u.equals("D")
        || u.equals("DO")
        || u.equals("G")
        || u.equals("GOTO")
        || u.equals("L")
        || u.equals("LOCK")
        || u.equals("I")
        || u.equals("IF");
  }

  static boolean isKnownCmd(String w) {
    String u = w.toUpperCase(java.util.Locale.ROOT);
    return u.equals("S")
        || u.equals("SET")
        || u.equals("W")
        || u.equals("WRITE")
        || u.equals("Q")
        || u.equals("QUIT")
        || u.equals("H")
        || u.equals("HALT")
        || u.equals("HANG")
        || u.equals("J")
        || u.equals("JOB")
        || u.equals("D")
        || u.equals("DO")
        || u.equals("L")
        || u.equals("LOCK")
        || u.equals("O")
        || u.equals("OPEN")
        || u.equals("C")
        || u.equals("CLOSE")
        || u.equals("U")
        || u.equals("USE")
        || u.equals("R")
        || u.equals("READ")
        || u.equals("G")
        || u.equals("GOTO")
        || u.equals("TSTART")
        || u.equals("TCOMMIT")
        || u.equals("TROLLBACK")
        || u.equals("X")
        || u.equals("XECUTE")
        || u.equals("I")
        || u.equals("IF")
        || u.equals("E")
        || u.equals("ELSE")
        || u.equals("F")
        || u.equals("FOR")
        || u.equals("N")
        || u.equals("NEW")
        || u.equals("K")
        || u.equals("KILL")
        || u.equals("M")
        || u.equals("MERGE")
        || u.equals("VIEW");
  }

  static int skipWs(String s, int i) {
    while (i < s.length() && s.charAt(i) == ' ') i++;
    return i;
  }

  static boolean isCmdStart(String s, int i) {
    if (i >= s.length() || !Character.isLetter(s.charAt(i))) return false;
    // must be at a word boundary (start of string or after non-word char)
    // so %AL doesn't match L (LOCK) at the tail of a variable name.
    // mjvm#22/#24: a comma also never precedes a command — commands are
    // space-separated, so after a comma the word is an argument, not a command
    // (W "a=",C / S A=1,B=2 must not read C/B as CLOSE/DO).
    if (i > 0) {
      char prev = s.charAt(i - 1);
      if (Character.isLetterOrDigit(prev) || prev == '%' || prev == ',') return false;
    }
    int j = i;
    while (j < s.length() && Character.isLetter(s.charAt(j))) j++;
    String w = s.substring(i, j).toUpperCase(java.util.Locale.ROOT);
    // must be a command word followed by space/end (not a variable named X, L, …)
    if (j < s.length() && s.charAt(j) != ' ' && s.charAt(j) != ':') {
      return false;
    }
    return w.equals("S")
        || w.equals("SET")
        || w.equals("W")
        || w.equals("WRITE")
        || w.equals("Q")
        || w.equals("QUIT")
        || w.equals("H")
        || w.equals("HALT")
        || w.equals("J")
        || w.equals("JOB")
        || w.equals("D")
        || w.equals("DO")
        || w.equals("L")
        || w.equals("LOCK")
        || w.equals("O")
        || w.equals("OPEN")
        || w.equals("C")
        || w.equals("CLOSE")
        || w.equals("U")
        || w.equals("USE")
        || w.equals("R")
        || w.equals("READ")
        || w.equals("G")
        || w.equals("GOTO")
        || w.equals("TSTART")
        || w.equals("TCOMMIT")
        || w.equals("TROLLBACK")
        || w.equals("X")
        || w.equals("XECUTE")
        || w.equals("I")
        || w.equals("IF")
        || w.equals("E")
        || w.equals("ELSE")
        || w.equals("F")
        || w.equals("FOR")
        || w.equals("N")
        || w.equals("NEW")
        || w.equals("K")
        || w.equals("KILL")
        || w.equals("M")
        || w.equals("MERGE")
        || w.equals("HANG")
        || w.equals("VIEW");
  }
}
