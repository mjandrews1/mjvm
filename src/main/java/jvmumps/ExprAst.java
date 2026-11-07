package jvmumps;

import java.util.ArrayList;
import java.util.List;

/**
 * AST cache for {@link ExprEval#eval}: compiles a source expression once into an immutable {@link
 * Node} tree, then evaluates the tree per call instead of re-running the precedence-climbing token
 * walk every time (mjvm perf item 5: the climb was ~45% of the m-ils fixture ingest).
 *
 * <p>Semantics mirror {@link ExprEval.Parser} exactly: all binary operators are one precedence
 * level, strictly left-associative (C30-C32), so the tree is a left fold. Any construct the builder
 * does not handle throws {@link UnsupportedExpr}; the caller then falls back to the legacy
 * evaluating parser and records the source in a ledger set (AGENTS.md: never swallow — fall back
 * and record).
 */
public final class ExprAst {

  private ExprAst() {}

  /** Control-flow signal: this source is not AST-compilable; caller must use the legacy parser. */
  public static final class UnsupportedExpr extends RuntimeException {
    UnsupportedExpr(String why) {
      super(why);
    }
  }

  // ---- opcodes (assigned once at parse time; eval switches on ints, no string compares) ----
  static final int C_CONCAT = 1;
  static final int C_ADD = 2, C_SUB = 3, C_MUL = 4, C_DIV = 5, C_IDIV = 6, C_MOD = 7, C_POW = 8;
  static final int C_EQ = 10, C_NEQ = 11, C_LT = 12, C_GT = 13, C_LE = 14, C_GE = 15;
  static final int C_NLT = 16, C_NGT = 17; // '< and '> (negated sorts-before)
  static final int C_CONTAINS = 18, C_NCONTAINS = 19; // [ and '[
  static final int C_SORTAFTER = 20, C_NSORTAFTER = 21; // ] and ']
  static final int C_COLL = 22, C_NCOLL = 23; // ]] and ']] (GT.M numeric collation)
  static final int C_AND = 30, C_NAND = 31, C_OR = 32, C_NOR = 33;
  static final int U_NEG = 41, U_POS = 42, U_NOT = 43;

  static int binOpCode(String op) {
    switch (op) {
      case "_":
        return C_CONCAT;
      case "+":
        return C_ADD;
      case "-":
        return C_SUB;
      case "*":
        return C_MUL;
      case "/":
        return C_DIV;
      case "\\":
        return C_IDIV;
      case "#":
        return C_MOD;
      case "**":
        return C_POW;
      case "=":
        return C_EQ;
      case "'=":
        return C_NEQ;
      case "<":
        return C_LT;
      case ">":
        return C_GT;
      case "<=":
        return C_LE;
      case ">=":
        return C_GE;
      case "'<":
        return C_NLT;
      case "'>":
        return C_NGT;
      case "[":
        return C_CONTAINS;
      case "'[":
        return C_NCONTAINS;
      case "]":
        return C_SORTAFTER;
      case "']":
        return C_NSORTAFTER;
      case "]]":
        return C_COLL;
      case "']]":
        return C_NCOLL;
      case "&":
        return C_AND;
      case "'&":
        return C_NAND;
      case "!":
        return C_OR;
      case "'!":
        return C_NOR;
      default:
        return 0;
    }
  }

  static boolean isPatternOp(String op) {
    return op.equals("?") || op.equals("'?");
  }

  // ---- node tree ---------------------------------------------------------------

  public abstract static class Node {
    public abstract MValue eval(ExprEval e);
  }

  static final class NumNode extends Node {
    final double v;

    NumNode(double v) {
      this.v = v;
    }

    @Override
    public MValue eval(ExprEval e) {
      return MValue.ofNumber(v);
    }
  }

  static final class StrNode extends Node {
    final String s;

    StrNode(String s) {
      this.s = s;
    }

    @Override
    public MValue eval(ExprEval e) {
      return MValue.ofString(s);
    }
  }

  static final class EmptyNode extends Node {
    static final EmptyNode INSTANCE = new EmptyNode();

    @Override
    public MValue eval(ExprEval e) {
      return MValue.EMPTY;
    }
  }

  static final class XNode extends Node {
    @Override
    public MValue eval(ExprEval e) {
      return MValue.ofNumber(e.vx);
    }
  }

  static final class YNode extends Node {
    @Override
    public MValue eval(ExprEval e) {
      return MValue.ofNumber(e.vy);
    }
  }

  /** Local/global reference, optionally subscripted. Key formatting matches Parser.primary. */
  static final class VarNode extends Node {
    final String name;
    final Node[] subs; // null = bare name

    VarNode(String name, Node[] subs) {
      this.name = name;
      this.subs = subs;
    }

    @Override
    public MValue eval(ExprEval e) {
      String key;
      if (subs == null) {
        key = name;
      } else {
        StringBuilder inner = new StringBuilder();
        for (int k = 0; k < subs.length; k++) {
          if (k > 0) inner.append(',');
          MValue a = subs[k].eval(e);
          if (a.isnum) inner.append(ExprEval.mstr(a.asNum()));
          else inner.append('"').append(a.asStr()).append('"');
        }
        key = name + "(" + inner + ")";
      }
      e.lastRef = key;
      return e.env.get(key);
    }
  }

  static final class UnaryNode extends Node {
    final int op;
    final Node x;

    UnaryNode(int op, Node x) {
      this.op = op;
      this.x = x;
    }

    @Override
    public MValue eval(ExprEval e) {
      MValue v = x.eval(e);
      switch (op) {
        case U_NEG:
          return MValue.ofNumber(-v.asNum());
        case U_POS:
          return MValue.ofNumber(v.asNum());
        case U_NOT:
          return ExprEval.truthy(v) ? MValue.ofNumber(0) : MValue.ofNumber(1);
        default:
          throw new MError("bad unary op " + op);
      }
    }
  }

  static final class BinNode extends Node {
    final int op;
    final Node l, r;

    BinNode(int op, Node l, Node r) {
      this.op = op;
      this.l = l;
      this.r = r;
    }

    @Override
    public MValue eval(ExprEval e) {
      MValue a = l.eval(e);
      MValue b = r.eval(e);
      switch (op) {
        case C_CONCAT:
          return MValue.ofString(a.asStr() + b.asStr());
        case C_ADD:
        case C_SUB:
        case C_MUL:
        case C_DIV:
        case C_IDIV:
        case C_MOD:
        case C_POW:
          if (a.isnum && b.isnum) {
            double x = a.num, y = b.num;
            switch (op) {
              case C_ADD:
                return MValue.ofNumber(x + y);
              case C_SUB:
                return MValue.ofNumber(x - y);
              case C_MUL:
                return MValue.ofNumber(x * y);
              case C_DIV:
                return MValue.ofNumber(y == 0 ? 0 : x / y);
              case C_IDIV:
                return MValue.ofNumber(y == 0 ? 0 : Math.floor(x / y));
              case C_MOD:
                return MValue.ofNumber(y == 0 ? 0 : x - y * Math.floor(x / y));
              case C_POW:
                return MValue.ofNumber(Math.pow(x, y));
              default:
                break;
            }
          }
          return arith(op, a, b);
        case C_EQ:
        case C_NEQ:
        case C_LT:
        case C_GT:
        case C_LE:
        case C_GE:
        case C_NLT:
        case C_NGT:
          if (a.isnum && b.isnum) {
            double x = a.num, y = b.num;
            boolean r;
            switch (op) {
              case C_EQ:
                r = x == y;
                break;
              case C_NEQ:
                r = x != y;
                break;
              case C_LT:
                r = x < y;
                break;
              case C_GT:
                r = x > y;
                break;
              case C_LE:
                r = x <= y;
                break;
              case C_GE:
                r = x >= y;
                break;
              case C_NLT:
                r = x >= y;
                break; // '< : not sorts-before
              case C_NGT:
                r = x <= y;
                break; // '> : not sorts-after
              default:
                r = false;
            }
            return r ? MValue.ofNumber(1) : MValue.ofNumber(0);
          }
          return ExprEval.compare(opName(op), a, b);
        case C_CONTAINS:
        case C_NCONTAINS:
        case C_SORTAFTER:
        case C_NSORTAFTER:
        case C_COLL:
        case C_NCOLL:
          return ExprEval.compare(opName(op), a, b);
        case C_AND:
        case C_NAND:
        case C_OR:
        case C_NOR:
          return logic(op, a, b);
        default:
          throw new MError("bad bin op " + op);
      }
    }
  }

  static String opName(int op) {
    switch (op) {
      case C_CONCAT:
        return "_";
      case C_ADD:
        return "+";
      case C_SUB:
        return "-";
      case C_MUL:
        return "*";
      case C_DIV:
        return "/";
      case C_IDIV:
        return "\\";
      case C_MOD:
        return "#";
      case C_POW:
        return "**";
      case C_EQ:
        return "=";
      case C_NEQ:
        return "'=";
      case C_LT:
        return "<";
      case C_GT:
        return ">";
      case C_LE:
        return "<=";
      case C_GE:
        return ">=";
      case C_NLT:
        return "'<";
      case C_NGT:
        return "'>";
      case C_CONTAINS:
        return "[";
      case C_NCONTAINS:
        return "'[";
      case C_SORTAFTER:
        return "]";
      case C_NSORTAFTER:
        return "']";
      case C_COLL:
        return "]]";
      case C_NCOLL:
        return "']]";
      case C_AND:
        return "&";
      case C_NAND:
        return "'&";
      case C_OR:
        return "!";
      case C_NOR:
        return "'!";
      default:
        return "?";
    }
  }

  static MValue arith(int op, MValue a, MValue b) {
    double x = a.asNum();
    double y = b.asNum();
    double r;
    switch (op) {
      case C_ADD:
        r = x + y;
        break;
      case C_SUB:
        r = x - y;
        break;
      case C_MUL:
        r = x * y;
        break;
      case C_DIV:
        r = y == 0 ? 0 : x / y;
        break;
      case C_IDIV:
        r = y == 0 ? 0 : Math.floor(x / y);
        break;
      case C_MOD:
        r = y == 0 ? 0 : x - y * Math.floor(x / y);
        break; // C22: -7#3 = 2
      case C_POW:
        r = Math.pow(x, y);
        break;
      default:
        throw new MError("bad arith op " + op);
    }
    return MValue.ofNumber(r);
  }

  static MValue logic(int op, MValue a, MValue b) {
    boolean t;
    switch (op) {
      case C_AND:
        t = ExprEval.truthy(a) && ExprEval.truthy(b);
        break;
      case C_NAND:
        t = !(ExprEval.truthy(a) && ExprEval.truthy(b));
        break;
      case C_OR:
        t = ExprEval.truthy(a) || ExprEval.truthy(b);
        break;
      case C_NOR:
        t = !(ExprEval.truthy(a) || ExprEval.truthy(b));
        break;
      default:
        throw new MError("bad logic op " + op);
    }
    return t ? MValue.ofNumber(1) : MValue.ofNumber(0);
  }

  /**
   * Pattern match: {@code a ? pat} / {@code a '? pat}. Pattern source is captured at parse time.
   */
  static final class PatternNode extends Node {
    final boolean neg;
    final Node l;
    final String pat;

    PatternNode(boolean neg, Node l, String pat) {
      this.neg = neg;
      this.l = l;
      this.pat = pat;
    }

    @Override
    public MValue eval(ExprEval e) {
      boolean m = PatternMatch.match(l.eval(e).asStr(), pat);
      if (neg) m = !m;
      return m ? MValue.ofNumber(1) : MValue.ofNumber(0);
    }
  }

  /**
   * @expr indirection: evaluates the inner expression, then re-evaluates the resulting string.
   */
  static final class IndirNode extends Node {
    final Node x;

    IndirNode(Node x) {
      this.x = x;
    }

    @Override
    public MValue eval(ExprEval e) {
      MValue v = x.eval(e);
      return e.eval(v.asStr());
    }
  }

  /** Generic intrinsic $FN(args) — args evaluated per call, then ExprIntrinsics.intrinsic. */
  static final class IntrinsicNode extends Node {
    final String name;
    final Node[] args;

    IntrinsicNode(String name, Node[] args) {
      this.name = name;
      this.args = args;
    }

    @Override
    public MValue eval(ExprEval e) {
      List<MValue> vals = new ArrayList<>(args.length);
      for (Node a : args) vals.add(a.eval(e));
      return ExprIntrinsics.intrinsic(e, name, vals);
    }
  }

  /** $$tag(args) extrinsic, with optional pass-by-reference .var args. */
  static final class ExtrinsicNode extends Node {
    final String fn;
    final Node[] args;
    final String[] byrefs; // parallel to args; null entry = by-value

    ExtrinsicNode(String fn, Node[] args, String[] byrefs) {
      this.fn = fn;
      this.args = args;
      this.byrefs = byrefs;
    }

    @Override
    public MValue eval(ExprEval e) {
      List<MValue> vals = new ArrayList<>(args.length);
      List<String> br = new ArrayList<>(args.length);
      for (int k = 0; k < args.length; k++) {
        if (byrefs[k] != null) {
          vals.add(MValue.EMPTY);
          br.add(byrefs[k]);
        } else {
          vals.add(args[k].eval(e));
          br.add(null);
        }
      }
      return e.env.extrinsic(fn, vals, br);
    }
  }

  /** Reference key for the $G/$D/$O/... family: name + subscripts, or @indirection. */
  abstract static class RefKey {
    abstract String eval(ExprEval e);
  }

  static final class IndirRefKey extends RefKey {
    final Node x;

    IndirRefKey(Node x) {
      this.x = x;
    }

    @Override
    String eval(ExprEval e) {
      return x.eval(e).asStr();
    }
  }

  static final class NameRefKey extends RefKey {
    final String name;
    final List<Object> items; // Node or COMMA marker, in token order; null = no subscripts
    static final Object COMMA = new Object();

    NameRefKey(String name, List<Object> items) {
      this.name = name;
      this.items = items;
    }

    @Override
    String eval(ExprEval e) {
      StringBuilder key = new StringBuilder(name);
      if (items != null) {
        StringBuilder inner = new StringBuilder();
        for (Object it : items) {
          if (it == COMMA) {
            inner.append(',');
          } else {
            MValue a = ((Node) it).eval(e);
            if (a.isnum) inner.append(ExprEval.mstr(a.asNum()));
            else inner.append('"').append(a.asStr()).append('"');
          }
        }
        key.append('(').append(inner).append(')');
      }
      return key.toString();
    }
  }

  /** $D/^ref(...) family: builds the ref key, sets lastRef, calls the intrinsic. */
  static final class RefIntrinsicNode extends Node {
    final String name;
    final RefKey key;
    final Node[] extras;

    RefIntrinsicNode(String name, RefKey key, Node[] extras) {
      this.name = name;
      this.key = key;
      this.extras = extras;
    }

    @Override
    public MValue eval(ExprEval e) {
      String k = key.eval(e);
      List<MValue> args = new ArrayList<>(extras.length + 1);
      args.add(MValue.ofString(k));
      for (Node x : extras) args.add(x.eval(e));
      e.lastRef = k;
      return ExprIntrinsics.intrinsic(e, name, args);
    }
  }

  // ---- parser (token consumption mirrors ExprEval.Parser exactly) ------------------

  public static Node parse(String src, List<ExprTokens.Tok> toks) {
    AstParser p = new AstParser(toks);
    Node n = p.parseExpr();
    if (!p.atEnd()) {
      // legacy parser raises the MError; do not cache
      throw new UnsupportedExpr("trailing input");
    }
    return n;
  }

  static final class AstParser {
    final List<ExprTokens.Tok> toks;
    int i = 0;

    AstParser(List<ExprTokens.Tok> toks) {
      this.toks = toks;
    }

    ExprTokens.Tok peek() {
      return toks.get(i);
    }

    boolean atEnd() {
      return peek().kind == ExprTokens.Kind.EOF;
    }

    Node parseExpr() {
      return parseBinary();
    }

    Node parseBinary() {
      Node a = parseUnary();
      while (true) {
        ExprTokens.Tok t = peek();
        if (t.kind != ExprTokens.Kind.OP) break;
        String op = t.text;
        if (isPatternOp(op)) {
          i++;
          String pat = readPatternSrc();
          a = new PatternNode(op.equals("'?"), a, pat);
        } else {
          int code = binOpCode(op);
          if (code == 0) break;
          i++;
          Node b = parseUnary();
          a = new BinNode(code, a, b);
        }
      }
      return a;
    }

    Node parseUnary() {
      ExprTokens.Tok t = peek();
      if (t.kind == ExprTokens.Kind.OP) {
        if (t.text.equals("-")) {
          i++;
          return new UnaryNode(U_NEG, parseUnary());
        }
        if (t.text.equals("+")) {
          i++;
          return new UnaryNode(U_POS, parseUnary());
        }
        if (t.text.equals("'")) {
          i++;
          return new UnaryNode(U_NOT, parseUnary());
        }
      }
      return parsePrimary();
    }

    Node parsePrimary() {
      ExprTokens.Tok t = peek();
      if (t.kind == ExprTokens.Kind.NUM) {
        i++;
        return new NumNode(ExprEval.toNum(t.text));
      }
      if (t.kind == ExprTokens.Kind.STR) {
        i++;
        return new StrNode(t.text);
      }
      if (t.kind == ExprTokens.Kind.OP && t.text.equals("@")) {
        i++;
        return new IndirNode(parseAtChain());
      }
      if (t.kind == ExprTokens.Kind.OP && t.text.equals("(")) {
        i++;
        Node n = parseExpr();
        if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
        i++;
        return n;
      }
      if (t.kind == ExprTokens.Kind.NAME || t.kind == ExprTokens.Kind.GLOBAL) {
        i++;
        if (t.text.startsWith("$$")) {
          return parseExtrinsic(t.text);
        }
        if (t.text.startsWith("$")) {
          return parseIntrinsic(t.text);
        }
        // variable / global reference, optional subscripts
        Node[] subs = null;
        if (peek().kind == ExprTokens.Kind.LP) {
          i++;
          List<Node> args = parseArgList(false, null);
          if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
          i++;
          subs = args.toArray(new Node[0]);
        }
        return new VarNode(t.text, subs);
      }
      throw new UnsupportedExpr("unexpected " + t);
    }

    Node parseExtrinsic(String tag) {
      // optional ^ROUTINE qualifier
      if (peek().kind == ExprTokens.Kind.GLOBAL && peek().text.startsWith("^")) {
        i++;
      }
      List<Node> args = new ArrayList<>();
      List<String> byrefs = new ArrayList<>();
      if (peek().kind == ExprTokens.Kind.LP) {
        i++;
        args = parseArgList(true, byrefs);
        if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
        i++;
      }
      String fn = tag.substring(2);
      int caret = fn.indexOf('^');
      if (caret > 0) fn = fn.substring(0, caret);
      return new ExtrinsicNode(fn, args.toArray(new Node[0]), byrefs.toArray(new String[0]));
    }

    Node parseIntrinsic(String name) {
      String up = ExprEval.upper(name);
      if (up.equals("$TEXT")) {
        // C71: argument is a label/ref, not evaluated — consume to RP, value is ""
        if (peek().kind == ExprTokens.Kind.LP) {
          i++;
          while (!atEnd() && peek().kind != ExprTokens.Kind.RP) i++;
          if (peek().kind == ExprTokens.Kind.RP) i++;
        }
        return new StrNode("");
      }
      if (up.equals("$X")) return new XNode();
      if (up.equals("$Y")) return new YNode();
      if (up.equals("$S") || up.equals("$SELECT")) {
        throw new UnsupportedExpr("$SELECT falls back to legacy parser");
      }
      if (isRefIntrinsic(up)) {
        // bare $Q/$QUIT is the $QUIT special (C81), not $QUERY
        if ((up.equals("$Q") || up.equals("$QUERY") || up.equals("$QUIT"))
            && peek().kind != ExprTokens.Kind.LP) {
          if (up.equals("$Q") || up.equals("$QUIT")) return new NumNode(0);
        }
        if (peek().kind == ExprTokens.Kind.LP) {
          i++;
          RefKey key = parseRefKey();
          List<Node> extras = new ArrayList<>();
          while (peek().kind == ExprTokens.Kind.COMMA) {
            i++;
            extras.add(parseExpr());
          }
          if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
          i++;
          return new RefIntrinsicNode(name, key, extras.toArray(new Node[0]));
        }
        return new IntrinsicNode(name, new Node[0]);
      }
      // generic intrinsic: optional (argList)
      Node[] args = new Node[0];
      if (peek().kind == ExprTokens.Kind.LP) {
        i++;
        List<Node> a = parseArgList(false, null);
        if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
        i++;
        args = a.toArray(new Node[0]);
      }
      return new IntrinsicNode(name, args);
    }

    static boolean isRefIntrinsic(String up) {
      switch (up) {
        case "$G":
        case "$GET":
        case "$D":
        case "$DATA":
        case "$NA":
        case "$NAME":
        case "$I":
        case "$INCREMENT":
        case "$O":
        case "$ORDER":
        case "$NEXT":
        case "$N":
        case "$Q":
        case "$QUERY":
        case "$QUIT":
          return true;
        default:
          return false;
      }
    }

    /**
     * Arg list; when {@code byrefs} is non-null, {@code .name} pass-by-reference args record the
     * caller's variable name (and contribute EMPTY). Otherwise byref contributes EMPTY only
     * (mirrors Parser.argList with null byrefs).
     */
    List<Node> parseArgList(boolean recordByrefs, List<String> byrefs) {
      List<Node> args = new ArrayList<>();
      if (peek().kind == ExprTokens.Kind.RP) return args;
      while (true) {
        if (peek().kind == ExprTokens.Kind.OP
            && ".".equals(peek().text)
            && i + 1 < toks.size()
            && toks.get(i + 1).kind == ExprTokens.Kind.NAME
            && !toks.get(i + 1).text.startsWith("$")) {
          i++; // consume '.'
          String nm = toks.get(i).text;
          i++;
          args.add(EmptyNode.INSTANCE);
          if (recordByrefs) byrefs.add(nm);
        } else {
          args.add(parseExpr());
          if (recordByrefs) byrefs.add(null);
        }
        if (peek().kind == ExprTokens.Kind.COMMA) {
          i++;
          continue;
        }
        break;
      }
      return args;
    }

    /**
     * After '@': parse the operand plus any @(subs) chains into one concatenated name node.
     * GT.M: @%A@(1) with %A="q" reads q(1); a trailing '@' without '(' is an error there, so only
     * '@(' is consumed.
     */
    Node parseAtChain() {
      Node x = parseExpr();
      while (i + 1 < toks.size()
          && toks.get(i).kind == ExprTokens.Kind.OP
          && toks.get(i).text.equals("@")
          && toks.get(i + 1).kind == ExprTokens.Kind.LP) {
        i += 2; // '@' '('
        Node acc = new StrNode("(");
        if (peek().kind != ExprTokens.Kind.RP) {
          boolean first = true;
          while (true) {
            Node a = parseExpr();
            if (!first) acc = new BinNode(C_CONCAT, acc, new StrNode(","));
            acc = new BinNode(C_CONCAT, acc, a);
            first = false;
            if (peek().kind == ExprTokens.Kind.COMMA) {
              i++;
              continue;
            }
            break;
          }
        }
        if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing ) in @(...)");
        i++;
        acc = new BinNode(C_CONCAT, acc, new StrNode(")"));
        x = new BinNode(C_CONCAT, x, acc);
      }
      return x;
    }

    /** Mirror of Parser.readRefKey: name[+subs] or @indirection; subs keep their comma tokens. */
    RefKey parseRefKey() {
      ExprTokens.Tok t = peek();
      if (t.kind == ExprTokens.Kind.OP && t.text.equals("@")) {
        i++;
        return new IndirRefKey(parseAtChain());
      }
      if (t.kind != ExprTokens.Kind.NAME && t.kind != ExprTokens.Kind.GLOBAL) {
        return new NameRefKey("", null);
      }
      i++;
      String name = t.text;
      if (peek().kind == ExprTokens.Kind.LP) {
        i++;
        List<Object> items = new ArrayList<>();
        boolean first = true;
        while (peek().kind != ExprTokens.Kind.RP && !atEnd()) {
          if (!first && peek().kind == ExprTokens.Kind.COMMA) items.add(NameRefKey.COMMA);
          if (peek().kind == ExprTokens.Kind.COMMA) {
            i++;
            continue;
          }
          items.add(parseExpr());
          first = false;
        }
        if (peek().kind != ExprTokens.Kind.RP) throw new UnsupportedExpr("missing )");
        i++;
        return new NameRefKey(name, items);
      }
      return new NameRefKey(name, null);
    }

    /** Mirror of Parser.readPatternSrc — token-only consumption, pattern kept as source text. */
    String readPatternSrc() {
      StringBuilder sb = new StringBuilder();
      while (!atEnd()) {
        ExprTokens.Tok t = peek();
        if (t.kind == ExprTokens.Kind.RP
            || t.kind == ExprTokens.Kind.COMMA
            || t.kind == ExprTokens.Kind.EOF) {
          break;
        }
        if (t.kind == ExprTokens.Kind.OP && !t.text.equals(".")) break;
        if (t.kind == ExprTokens.Kind.OP && t.text.equals(".")) {
          sb.append('.');
          i++;
          continue;
        }
        if (t.kind == ExprTokens.Kind.NUM) {
          sb.append(t.text);
          i++;
          continue;
        }
        if (t.kind == ExprTokens.Kind.STR) {
          sb.append('"').append(t.text).append('"');
          i++;
          continue;
        }
        if (t.kind == ExprTokens.Kind.NAME) {
          sb.append(t.text);
          i++;
          continue;
        }
        if (t.kind == ExprTokens.Kind.GLOBAL) {
          sb.append(t.text);
          i++;
          continue;
        }
        break;
      }
      return sb.toString();
    }
  }
}
