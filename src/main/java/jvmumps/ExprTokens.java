package jvmumps;

import java.util.ArrayList;
import java.util.List;

/** Tokens + lexer for {@link ExprEval} (split from god file, #58). */
final class ExprTokens {
  static boolean digit(char c) {
    return c >= '0' && c <= '9';
  }

  private ExprTokens() {}

  // ---- tokens -------------------------------------------------------

  enum Kind {
    NUM,
    STR,
    NAME,
    GLOBAL,
    OP,
    LP,
    RP,
    COMMA,
    EOF
  }

  static final class Tok {
    final Kind kind;
    final String text;

    Tok(Kind kind, String text) {
      this.kind = kind;
      this.text = text;
    }

    @Override
    public String toString() {
      return kind + ":" + text;
    }
  }

  static List<Tok> lex(String s) {
    List<Tok> out = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == ' ' || c == '\t') {
        i++;
        continue;
      }
      if (c == '"') {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length()) {
          if (s.charAt(i) == '"') {
            if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
              sb.append('"');
              i += 2;
              continue;
            }
            i++;
            break;
          }
          sb.append(s.charAt(i++));
        }
        out.add(new Tok(Kind.STR, sb.toString()));
        continue;
      }
      if (digit(c) || (c == '.' && i + 1 < s.length() && digit(s.charAt(i + 1)))) {
        int j = i;
        while (j < s.length() && (digit(s.charAt(j)) || s.charAt(j) == '.')) {
          j++;
        }
        if (j < s.length() && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
          int k = j + 1;
          if (k < s.length() && (s.charAt(k) == '+' || s.charAt(k) == '-')) {
            k++;
          }
          if (k < s.length() && digit(s.charAt(k))) {
            j = k;
            while (j < s.length() && digit(s.charAt(j))) {
              j++;
            }
          }
        }
        out.add(new Tok(Kind.NUM, s.substring(i, j)));
        i = j;
        continue;
      }
      if (c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '$') {
        // $$extrinsic
        int j = i + 2;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '%')) {
          j++;
        }
        out.add(new Tok(Kind.NAME, s.substring(i, j)));
        i = j;
        continue;
      }
      if (c == '$' || c == '%') {
        // $fn / $H etc. — keep as NAME token for later intrinsic layer
        int j = i + 1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '%')) {
          j++;
        }
        out.add(new Tok(Kind.NAME, s.substring(i, j)));
        i = j;
        continue;
      }
      if (Character.isLetter(c)) {
        int j = i;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '%')) {
          j++;
        }
        out.add(new Tok(Kind.NAME, s.substring(i, j)));
        i = j;
        continue;
      }
      if (c == '^') {
        int j = i + 1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '%')) {
          j++;
        }
        out.add(new Tok(Kind.GLOBAL, s.substring(i, j)));
        i = j;
        continue;
      }
      if (c == '(') {
        out.add(new Tok(Kind.LP, "("));
        i++;
        continue;
      }
      if (c == ')') {
        out.add(new Tok(Kind.RP, ")"));
        i++;
        continue;
      }
      if (c == ',') {
        out.add(new Tok(Kind.COMMA, ","));
        i++;
        continue;
      }
      // operators (longest first)
      if (s.startsWith("]]", i)) {
        out.add(new Tok(Kind.OP, "]]"));
        i += 2;
        continue;
      }
      if (s.startsWith("**", i)) {
        out.add(new Tok(Kind.OP, "**"));
        i += 2;
        continue;
      }
      if (s.startsWith("']]", i) || s.startsWith("'<=", i) || s.startsWith("'>=", i)) {
        out.add(new Tok(Kind.OP, s.substring(i, i + 3)));
        i += 3;
        continue;
      }
      if (s.startsWith("'<", i)
          || s.startsWith("'>", i)
          || s.startsWith("'=", i)
          || s.startsWith("'<", i)
          || s.startsWith("'[", i)
          || s.startsWith("']", i)
          || s.startsWith("'?", i)) {
        out.add(new Tok(Kind.OP, s.substring(i, i + 2)));
        i += 2;
        continue;
      }
      if (s.startsWith("'<=", i) || s.startsWith("'>=", i)) {
        out.add(new Tok(Kind.OP, s.substring(i, i + 3)));
        i += 3;
        continue;
      }
      if (s.startsWith("<=", i)
          || s.startsWith(">=", i)
          || s.startsWith("'&", i)
          || s.startsWith("'!", i)) {
        out.add(new Tok(Kind.OP, s.substring(i, i + 2)));
        i += 2;
        continue;
      }
      String op = String.valueOf(c);
      out.add(new Tok(Kind.OP, op));
      i++;
    }
    out.add(new Tok(Kind.EOF, ""));
    return out;
  }
}
