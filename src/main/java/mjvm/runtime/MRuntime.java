package mjvm.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jvmumps.MValue;
import jvmumps.StoreApi;

/**
 * mJVM runtime (M2/M3) — executes {@link mjvm.ir.Ir} against a {@link StoreApi}.
 *
 * <p>Compiled classes call back into this runtime for I/O and variables so the JvMumps store SPI
 * stays the single persistence contract.
 */
public final class MRuntime {
  private final StoreApi store;

  /** Access the underlying store (for batch operations). */
  @SuppressWarnings("RedundantThrows")
  public StoreApi getStore() {
    return store;
  }

  private final StringBuilder out = new StringBuilder();
  private final Map<String, MValue> locals = new HashMap<>();
  private volatile boolean quit;
  // M5 devices
  private final Map<Integer, java.io.BufferedReader> devices = new HashMap<>();
  private final Map<Integer, java.io.Writer> writers = new HashMap<>();
  private volatile int io = 0;
  // M6 locks / TX (per-level undo, same contract as JvMumps LineInterp)
  private final java.util.Set<String> locks = new java.util.HashSet<>();
  private final java.util.List<java.util.List<Object[]>> txUndo = new ArrayList<>();
  private final java.util.concurrent.atomic.AtomicInteger tlevel =
      new java.util.concurrent.atomic.AtomicInteger(0);

  /** Shared JvMumps evaluator for $intrinsics / patterns / indirection (M11). */
  private final jvmumps.ExprEval exprEval;

  public MRuntime(StoreApi store) {
    this.store = store;
    this.exprEval =
        new jvmumps.ExprEval(
            new jvmumps.ExprEval.Env() {
              @Override
              public jvmumps.MValue get(String name) {
                return MRuntime.this.getVar(name);
              }

              @Override
              @SuppressWarnings("RedundantThrows")
              public void set(String name, jvmumps.MValue v) {
                MRuntime.this.setVar(name, v);
              }

              @Override
              @SuppressWarnings("RedundantThrows")
              public int data(String name) {
                return MRuntime.this.data(name);
              }

              @Override
              @SuppressWarnings("RedundantThrows")
              public String order(String key, double dir) {
                return MRuntime.this.order(key, dir);
              }

              @Override
              @SuppressWarnings("RedundantThrows")
              public String query(String key, int dir) {
                return MRuntime.this.query(key, dir);
              }

              @Override
              public jvmumps.MValue extrinsic(String name, java.util.List<jvmumps.MValue> args) {
                return MRuntime.this.callExtrinsic(name, args.toArray(new jvmumps.MValue[0]));
              }
            });
  }

  // Compiled expression cache: avoids re-tokenization for repeated expressions
  private static final java.util.concurrent.ConcurrentHashMap<String, CompiledExpr> exprCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Pre-compiled expression: cached tokenization + fast evaluation. */
  static final class CompiledExpr {
    final String src;
    final String[] tokens; // pre-tokenized
    final int[] kinds; // token kinds

    CompiledExpr(String src) {
      this.src = src;
      // Tokenize once
      java.util.List<String> tok = new java.util.ArrayList<>();
      java.util.List<Integer> knd = new java.util.ArrayList<>();
      int i = 0;
      while (i < src.length()) {
        char c = src.charAt(i);
        if (Character.isWhitespace(c)) {
          i++;
          continue;
        }
        if (Character.isDigit(c) || c == '.') {
          int j = i;
          while (j < src.length() && (Character.isDigit(src.charAt(j)) || src.charAt(j) == '.'))
            j++;
          tok.add(src.substring(i, j));
          knd.add(0); // NUM
          i = j;
        } else if (Character.isLetter(c) || c == '%' || c == '^') {
          int j = i + 1; // consume first char (handles ^ not followed by alnum)
          while (j < src.length()
              && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '%')) j++;
          tok.add(src.substring(i, j));
          knd.add(1); // NAME
          i = j;
        } else if (c == '"') {
          int j = i + 1;
          while (j < src.length() && src.charAt(j) != '"') j++;
          tok.add(src.substring(i + 1, j));
          knd.add(2); // STR
          i = j + 1;
        } else {
          tok.add(String.valueOf(c));
          knd.add(3); // OP
          i++;
        }
      }
      this.tokens = tok.toArray(new String[0]);
      this.kinds = new int[knd.size()];
      for (int j = 0; j < knd.size(); j++) this.kinds[j] = knd.get(j);
    }
  }

  /** M11: run a raw M expression through JvMumps ExprEval. */
  public jvmumps.MValue evalExpr(String src) {
    // Fast path: try cached compiled expression for simple patterns
    if (src != null && src.length() > 1 && src.length() < 30) {
      jvmumps.MValue fast = fastEvalCached(src);
      if (fast != null) return fast;
    }
    try {
      return exprEval.eval(src == null ? "" : src);
    } catch (jvmumps.MError e) {
      if (trapFiring) throw e;
      String trap = exprEval.etrap;
      if (trap.isEmpty()) trap = exprEval.ztrap;
      if (trap.isEmpty()) throw e;
      trapFiring = true;
      try {
        var prog = new mjvm.compiler.MFrontend().compileLine(trap);
        mjvm.runtime.IrMachine.run(prog, this);
      } catch (RuntimeException ignored) {
      } finally {
        trapFiring = false;
      }
      return jvmumps.MValue.EMPTY;
    }
  }

  private volatile boolean trapFiring;

  /**
   * M11: run $ETRAP/$ZTRAP once for a command error. Returns true if a trap was fired (error
   * handled), false if no trap set.
   */
  @SuppressWarnings("RedundantThrows")
  public boolean fireTrap() {
    if (trapFiring) return true;
    String trap = exprEval.etrap;
    if (trap.isEmpty()) trap = exprEval.ztrap;
    if (trap.isEmpty()) return false;
    trapFiring = true;
    try {
      var prog = new mjvm.compiler.MFrontend().compileLine(trap);
      mjvm.runtime.IrMachine.run(prog, this);
    } catch (RuntimeException ignored) {
    } finally {
      trapFiring = false;
    }
    return true;
  }

  /** SET $ETRAP / $ECODE / $ZTRAP / $X / $Y. */
  @SuppressWarnings("RedundantThrows")
  public void setSpecial(String name, jvmumps.MValue v) {
    String u = name.toUpperCase(java.util.Locale.ROOT);
    switch (u) {
      case "$ETRAP" -> exprEval.etrap = v.asStr();
      case "$ECODE" -> exprEval.ecode = v.asStr();
      case "$ZTRAP" -> exprEval.ztrap = v.asStr();
      case "$X" -> exprEval.vx = (int) v.asNum();
      case "$Y" -> exprEval.vy = (int) v.asNum();
      default -> {
        /* ignore other specials for now */
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  public String output() {
    return out.toString();
  }

  @SuppressWarnings("RedundantThrows")
  public void reset() {
    out.setLength(0);
    locals.clear();
    quit = false;
  }

  @SuppressWarnings("RedundantThrows")
  public void write(String s) {
    if (out.length() > 2_000_000) throw new jvmumps.MError("output limit");
    if (io != 0) {
      writeToDevice(io, s);
      return;
    }
    out.append(s);
    exprEval.vx += s.length();
  }

  /** M11 WRITE *n */
  @SuppressWarnings("RedundantThrows")
  public void writeChar(int code) {
    if (code == 10) {
      writeNl();
      return;
    }
    char ch = (code >= 0 && code <= 255) ? (char) code : '?';
    write(String.valueOf(ch));
  }

  /** M11 WRITE ?n — pad to column n (1-based). */
  @SuppressWarnings("RedundantThrows")
  public void writeCol(int n) {
    String cur = out.toString();
    int lastNl = cur.lastIndexOf('\n');
    int col = cur.length() - lastNl - 1;
    if (n > col) {
      write(" ".repeat(n - col));
    }
  }

  /** Trailing WRITE line-break: keep $X/$Y (C90). */
  @SuppressWarnings("RedundantThrows")
  public void endWrite() {
    if (io != 0) {
      writeToDevice(io, "");
      return;
    }
    out.append('\n');
  }

  @SuppressWarnings("RedundantThrows")
  public void writeNl() {
    if (io != 0) {
      writeToDevice(io, "");
      return;
    }
    out.append('\n');
    exprEval.vx = 0;
    exprEval.vy++;
  }

  @SuppressWarnings("RedundantThrows")
  static List<String> pathOf(String name) {
    return pathOf(name, null);
  }

  /** Build interned string key (name\001sub1\001sub2...) — avoids List allocation. */
  @SuppressWarnings("RedundantThrows")
  static String keyOf(String name, MRuntime rt) {
    int p = name.indexOf('(');
    if (p < 0) return name;
    StringBuilder sb = new StringBuilder(name.substring(0, p));
    String inner = name.substring(p + 1, name.lastIndexOf(')'));
    int depth = 0;
    boolean inS = false;
    StringBuilder sub = new StringBuilder();
    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);
      if (inS) {
        sub.append(c);
        if (c == '"') inS = false;
        continue;
      }
      if (c == '"') {
        inS = true;
        sub.append(c);
        continue;
      }
      if (c == '(') {
        depth++;
        sub.append(c);
        continue;
      }
      if (c == ')') {
        depth--;
        sub.append(c);
        continue;
      }
      if (c == ',' && depth == 0) {
        sb.append((char) 1).append(evalSub(sub.toString(), rt));
        sub.setLength(0);
        continue;
      }
      sub.append(c);
    }
    if (sub.length() > 0) sb.append((char) 1).append(evalSub(sub.toString(), rt));
    return sb.toString();
  }

  @SuppressWarnings("RedundantThrows")
  static String evalSub(String s, MRuntime rt) {
    if (s.isEmpty()) return "";
    if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
    if (rt != null) {
      try {
        MValue v = rt.evalExpr(s);
        return v.isnum ? jvmumps.ExprEval.mstr(v.num) : v.str;
      } catch (jvmumps.MError e) {
        throw e;
      } catch (Exception e) {
        throw new jvmumps.MError("eval failed in WRITE sub: " + s + ": " + e);
      }
    }
    return s;
  }

  @SuppressWarnings("RedundantThrows")
  static List<String> pathOf(String name, MRuntime rt) {
    List<String> path = new ArrayList<>();
    int p = name.indexOf('(');
    if (p < 0) {
      path.add(name);
      return path;
    }
    path.add(name.substring(0, p));
    String inner = name.substring(p + 1, name.lastIndexOf(')'));
    int depth = 0;
    boolean inS = false;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);
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
      if (c == ',' && depth == 0) {
        path.add(subOf(sb.toString().trim(), rt));
        sb.setLength(0);
        continue;
      }
      sb.append(c);
    }
    if (sb.length() > 0) path.add(subOf(sb.toString().trim(), rt));
    return path;
  }

  /**
   * Subscript text → node name. Quoted subscripts are string data and keep their literal form
   * ({@code ^DD("0.01")} → {@code 0.01}). Unquoted numeric subscripts are canonicalised the way M
   * does when a number becomes a node name ({@code ^DD(0.01)} → {@code .01}, {@code 1.10} → {@code
   * 1.1}) — see {@link jvmumps.ExprEval#mstr}. Without this, {@code $ORDER} returns the
   * non-canonical form and FileMan's numeric field/index subscripts never compare equal to a
   * literal (GAP-1).
   */
  @SuppressWarnings("RedundantThrows")
  static String subOf(String s, MRuntime rt) {
    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
      return s.substring(1, s.length() - 1);
    }
    if (isNumericLiteral(s)) {
      return jvmumps.ExprEval.mstr(Double.parseDouble(s));
    }
    // variable reference — evaluate it
    if (rt != null && !s.isEmpty() && (Character.isLetter(s.charAt(0)) || s.charAt(0) == '%')) {
      try {
        return rt.evalExpr(s).asStr();
      } catch (jvmumps.MError e) {
        throw e;
      } catch (Exception e) {
        throw new jvmumps.MError("eval failed in WRITE sub: " + s + ": " + e);
      }
    }
    return s;
  }

  /** Plain numeric literal: optional sign, digits, optional fraction/exponent. */
  private static boolean isNumericLiteral(String s) {
    if (s.isEmpty()) return false;
    int i = 0;
    if (s.charAt(0) == '-' || s.charAt(0) == '+') i++;
    boolean digits = false;
    while (i < s.length() && Character.isDigit(s.charAt(i))) {
      i++;
      digits = true;
    }
    if (i < s.length() && s.charAt(i) == '.') {
      i++;
      while (i < s.length() && Character.isDigit(s.charAt(i))) {
        i++;
        digits = true;
      }
    }
    if (!digits) return false;
    if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
      i++;
      if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
      boolean edig = false;
      while (i < s.length() && Character.isDigit(s.charAt(i))) {
        i++;
        edig = true;
      }
      if (!edig) return false;
    }
    return i == s.length();
  }

  @SuppressWarnings("RedundantThrows")
  public MValue getVar(String name) {
    if (locals.containsKey(name)) return locals.get(name);
    String key = keyOf(name, this);
    boolean glob = key.startsWith("^");
    if (!glob && store.data(splitKey(key), false) % 2 == 0) {
      throw new jvmumps.MError("Undefined local variable");
    }
    return store.get(key, glob);
  }

  /** Get numeric value directly (skips string format/parse). */
  @SuppressWarnings("RedundantThrows")
  public double getNum(String name) {
    if (locals.containsKey(name)) return locals.get(name).asNum();
    String key = keyOf(name, this);
    boolean glob = key.startsWith("^");
    return store.getNum(key, glob);
  }

  /** Set numeric value directly (skips string format/parse). */
  @SuppressWarnings("RedundantThrows")
  public void setNum(String name, double val) {
    String key = keyOf(name, this);
    boolean glob = key.startsWith("^");
    if (glob) {
      store.setNum(key, true, val);
    } else {
      locals.put(name, MValue.ofNumber(val));
      store.setNum(key, false, val);
    }
  }

  static java.util.List<String> splitKey(String key) {
    return jvmumps.StoreCore.splitKey(key);
  }

  @SuppressWarnings("RedundantThrows")
  public void setVar(String name, MValue v) {
    // Fast path: simple global with no indirection
    if (name.startsWith("^") && name.indexOf('@') < 0) {
      boolean glob = true;
      String key = keyOf(name, this);
      if (tlevel.get() > 0) {
        MValue old = store.get(key, glob);
        recordUndo(List.of(key), glob, old);
      }
      store.set(key, glob, v);
      return;
    }
    List<String> path = pathOf(name, this);
    boolean glob = path.get(0).startsWith("^");
    if (tlevel.get() > 0) {
      MValue old = store.get(path, glob);
      recordUndo(path, glob, old);
    }
    if (glob) {
      store.set(path, true, v);
    } else {
      locals.put(name, v);
      store.set(path, false, v);
    }
  }

  /** Fast evaluation using cached tokenization. Returns null if pattern not recognized. */
  private jvmumps.MValue fastEvalCached(String src) {
    // Only handle simple 3-token expressions: VAR OP NUM/VAR
    if (src.length() > 15 || src.length() < 3) return null;
    CompiledExpr ce = exprCache.get(src);
    if (ce == null) {
      if (exprCache.size() > 500) return null;
      // Quick check: must have exactly 2 operator positions (3 tokens)
      // Count: NAME OP NAME/NUM pattern
      int ops = 0;
      for (int i = 0; i < src.length(); i++) {
        char c = src.charAt(i);
        if (c == '=' || c == '<' || c == '>' || c == '+' || c == '-' || c == '*' || c == '/'
            || c == '\'') ops++;
      }
      if (ops != 1) return null;
      ce = new CompiledExpr(src);
      exprCache.put(src, ce);
    }
    // Simple patterns: VAR OP NUM, VAR OP VAR
    if (ce.tokens.length == 3 && ce.kinds[0] == 1) {
      String left = ce.tokens[0];
      String op = ce.tokens[1];
      if (op.length() <= 2 && !Character.isLetterOrDigit(op.charAt(0))) {
        jvmumps.MValue lv = getVar(left);
        jvmumps.MValue rv;
        if (ce.kinds[2] == 0) {
          rv = jvmumps.MValue.ofNumber(Double.parseDouble(ce.tokens[2]));
        } else if (ce.kinds[2] == 1) {
          rv = getVar(ce.tokens[2]);
        } else {
          return null;
        }
        if (lv.isnum && rv.isnum) {
          double x = lv.num, y = rv.num;
          switch (op) {
            case "+":
              return jvmumps.MValue.ofNumber(x + y);
            case "-":
              return jvmumps.MValue.ofNumber(x - y);
            case "*":
              return jvmumps.MValue.ofNumber(x * y);
            case "/":
              return jvmumps.MValue.ofNumber(y == 0 ? 0 : x / y);
            case "=":
              return jvmumps.MValue.ofNumber(x == y ? 1 : 0);
            case "'=":
              return jvmumps.MValue.ofNumber(x != y ? 1 : 0);
            case "<":
              return jvmumps.MValue.ofNumber(x < y ? 1 : 0);
            case ">":
              return jvmumps.MValue.ofNumber(x > y ? 1 : 0);
            case "<=":
              return jvmumps.MValue.ofNumber(x <= y ? 1 : 0);
            case ">=":
              return jvmumps.MValue.ofNumber(x >= y ? 1 : 0);
          }
        }
      }
    }
    return null;
  }

  /** Atomic increment: delegates to store.increment for single-op path. */
  public jvmumps.MValue incrementVar(String name, double amount) {
    String key = keyOf(name, this);
    boolean glob = key.startsWith("^");
    return store.increment(key, glob, amount);
  }

  @SuppressWarnings("RedundantThrows")
  public boolean isQuit() {
    return quit;
  }

  @SuppressWarnings("RedundantThrows")
  public void requestQuit() {
    quit = true;
  }

  /** M3: CALL site — invoke a compiled routine via the resolver. */
  public interface Resolver {
    /** Run named M snippet/routine with argc values on the runtime. */
    void call(String name, int argc, MRuntime rt);
  }

  private Resolver resolver;

  @SuppressWarnings("RedundantThrows")
  public void setResolver(Resolver r) {
    this.resolver = r;
  }

  /** Extrinsic $$fn(args) — run via LineInterp-free: use stored program labels. */
  /** Return value of a {@code Q expr} inside an extrinsic (GAP-2). */
  private volatile jvmumps.MValue extrinsicValue = jvmumps.MValue.EMPTY;

  /** Linked routine (ZLINK semantics): tags stay callable after a run. */
  private java.util.List<mjvm.ir.Ir> linkedProg;

  private java.util.Map<String, Integer> linkedTags;

  @SuppressWarnings("RedundantThrows")
  public void link(java.util.List<mjvm.ir.Ir> prog, java.util.Map<String, Integer> tags) {
    this.linkedProg = prog == null ? null : java.util.List.copyOf(prog);
    this.linkedTags = tags == null ? null : java.util.Map.copyOf(tags);
  }

  public java.util.List<mjvm.ir.Ir> linkedProg() {
    return linkedProg == null ? null : java.util.Collections.unmodifiableList(linkedProg);
  }

  public java.util.Map<String, Integer> linkedTags() {
    return linkedTags == null ? null : java.util.Collections.unmodifiableMap(linkedTags);
  }

  @SuppressWarnings("RedundantThrows")
  public void setExtrinsicValue(jvmumps.MValue v) {
    this.extrinsicValue = v == null ? jvmumps.MValue.EMPTY : v;
  }

  public jvmumps.MValue takeExtrinsicValue() {
    jvmumps.MValue v = extrinsicValue;
    extrinsicValue = jvmumps.MValue.EMPTY;
    return v;
  }

  public jvmumps.MValue callExtrinsic(String name, jvmumps.MValue[] argv) {
    if (name != null && name.equalsIgnoreCase("PEMDAS")) {
      java.util.List<jvmumps.MValue> args = new java.util.ArrayList<>();
      if (argv != null) {
        for (jvmumps.MValue v : argv) args.add(v);
      }
      return jvmumps.PemdasEval.call(args);
    }
    // Linked routine first (ZLINK semantics): $$tag resolves against the
    // routine loaded by the last compile/run. Fall back to the handler.
    if (linkedTags != null && linkedProg != null) {
      Integer pc = linkedTags.get(name.toUpperCase(java.util.Locale.ROOT));
      if (pc != null) {
        jvmumps.MValue[] pass = argv == null ? new jvmumps.MValue[0] : argv;
        return mjvm.runtime.IrMachine.runExtrinsic(linkedProg, this, pc, pass);
      }
    }
    if (extrinsicHandler != null) return extrinsicHandler.call(name, argv);
    throw new jvmumps.MError("No such label '" + name + "'");
  }

  public interface ExtrinsicHandler {
    jvmumps.MValue call(String name, jvmumps.MValue[] argv);
  }

  private ExtrinsicHandler extrinsicHandler;

  @SuppressWarnings("RedundantThrows")
  public void setExtrinsicHandler(ExtrinsicHandler h) {
    this.extrinsicHandler = h;
  }

  @SuppressWarnings("RedundantThrows")
  public void call(String name, int argc) {
    call(name, new jvmumps.MValue[0]);
  }

  @SuppressWarnings("RedundantThrows")
  public void call(String name, jvmumps.MValue[] argv) {
    // argumentless DO — no-op (dotted block continues inline)
    if (name == null || name.isEmpty()) return;
    // Linked routine (ZLINK semantics): DO tag resolves against linked tags.
    if (linkedTags != null && linkedProg != null) {
      Integer pc = linkedTags.get(name.toUpperCase(java.util.Locale.ROOT));
      if (pc != null) {
        mjvm.runtime.IrMachine.runExtrinsic(linkedProg, this, pc, argv);
        return;
      }
    }
    if (resolver != null) {
      resolver.call(name, argv.length, this);
      return;
    }
    throw new jvmumps.MError("No such label '" + name + "'");
  }

  /** Truthiness: ISO / ExprEval contract (shared with JvMumps). */
  public static boolean truthy(MValue v) {
    return v != null && jvmumps.ExprEval.truthy(v);
  }

  /** ISO = : both numeric → numeric; else string (J17 contract). */
  public static boolean eq(MValue a, MValue b) {
    boolean an = a.isnum || jvmumps.ExprEval.numericStr(a.asStr());
    boolean bn = b.isnum || jvmumps.ExprEval.numericStr(b.asStr());
    if (an && bn) return a.asNum() == b.asNum();
    return a.asStr().equals(b.asStr());
  }

  public static boolean lt(MValue a, MValue b) {
    boolean an = a.isnum || jvmumps.ExprEval.numericStr(a.asStr());
    boolean bn = b.isnum || jvmumps.ExprEval.numericStr(b.asStr());
    if (an || bn) return a.asNum() < b.asNum();
    return a.asStr().compareTo(b.asStr()) < 0;
  }

  /** M10 NEW name — hide local (restore later via scope stack in LineInterp; here clear). */
  @SuppressWarnings("RedundantThrows")
  public void newVar(String name) {
    if (name == null) return;
    if (name.startsWith("^")) return;
    locals.remove(name);
    // also clear from store tree if present
    store.kill(List.of(name), false);
  }

  @SuppressWarnings("RedundantThrows")
  public void killVar(String name) {
    if (name == null || name.isEmpty()) return;
    List<String> path = pathOf(name, this);
    boolean glob = path.get(0).startsWith("^");
    locals.remove(name);
    store.kill(path, glob);
  }

  /** M12: $ORDER / $DATA / $QUERY / $NAME via the store tree. */
  @SuppressWarnings("RedundantThrows")
  public String order(String key, double dir) {
    List<String> path = pathOf(key, this);
    return store.order(path, path.get(0).startsWith("^"), dir);
  }

  @SuppressWarnings("RedundantThrows")
  public int data(String key) {
    List<String> path = pathOf(key, this);
    return store.data(path, path.get(0).startsWith("^"));
  }

  /** SET $EXTRACT(ref,from,to)=v / SET $PIECE(ref,d,f,t)=v */
  @SuppressWarnings("RedundantThrows")
  public void setExtract(String ref, int from, int to, String v) {
    List<String> path = pathOf(ref, this);
    boolean glob = path.get(0).startsWith("^");
    String cur = store.get(path, glob).asStr();
    if (to < from) to = from;
    if (from < 1) from = 1;
    int n = cur.length();
    String nu;
    if (from > n) {
      StringBuilder sb = new StringBuilder(cur);
      while (sb.length() < from - 1) sb.append(' ');
      sb.append(v);
      nu = sb.toString();
    } else {
      int end = Math.min(to, n);
      nu = cur.substring(0, from - 1) + v + cur.substring(end);
    }
    setVar(ref, jvmumps.MValue.ofString(nu));
  }

  @SuppressWarnings("RedundantThrows")
  public void setPiece(String ref, String d, int from, int to, String v) {
    List<String> path = pathOf(ref, this);
    boolean glob = path.get(0).startsWith("^");
    String cur = store.get(path, glob).asStr();
    setVar(ref, jvmumps.MValue.ofString(setPieceOf(cur, d, from, to, v)));
  }

  /** MERGE dst ← src (tree). */
  @SuppressWarnings("RedundantThrows")
  public void mergeVar(String dst, String src) {
    if (dst == null || src == null) return;
    List<String> d = pathOf(dst, this);
    List<String> s = pathOf(src, this);
    boolean dg = d.get(0).startsWith("^");
    boolean sg = s.get(0).startsWith("^");
    store.merge(d, dg, s, sg);
  }

  /** $QUERY */
  @SuppressWarnings("RedundantThrows")
  public String query(String key, int dir) {
    List<String> path = key.isEmpty() ? List.of("") : pathOf(key, this);
    return store.queryNext(path, !path.isEmpty() && path.get(0).startsWith("^"), dir);
  }

  // ---- M5 devices --------------------------------------------------
  @SuppressWarnings("RedundantThrows")
  public void openDevice(int n, String path) {
    try {
      java.nio.file.Path p = java.nio.file.Path.of(path);
      devices.put(n, java.nio.file.Files.newBufferedReader(p));
      writers.put(
          n,
          java.nio.file.Files.newBufferedWriter(
              p,
              java.nio.charset.StandardCharsets.UTF_8,
              java.nio.file.StandardOpenOption.CREATE,
              java.nio.file.StandardOpenOption.APPEND));
    } catch (Exception e) {
      throw new jvmumps.MError("OPEN failed: " + e);
    }
  }

  @SuppressWarnings("RedundantThrows")
  public void closeDevice(int n) {
    try {
      if (devices.containsKey(n)) devices.remove(n).close();
      if (writers.containsKey(n)) writers.remove(n).close();
    } catch (Exception ignored) {
    }
    if (io == n) io = 0;
  }

  @SuppressWarnings("RedundantThrows")
  public void useDevice(int n) {
    if (n != 0 && !devices.containsKey(n) && !writers.containsKey(n)) {
      throw new jvmumps.MError("USE on unopened device");
    }
    io = n;
  }

  @SuppressWarnings("RedundantThrows")
  public String readDevice() {
    try {
      java.io.BufferedReader r = devices.get(io);
      if (r == null) return "";
      String line = r.readLine();
      return line == null ? "" : line;
    } catch (Exception e) {
      throw new jvmumps.MError("READ failed: " + e);
    }
  }

  @SuppressWarnings("RedundantThrows")
  public void writeToDevice(int n, String s) {
    try {
      java.io.Writer w = writers.get(n);
      if (w != null) {
        w.write(s);
        w.write('\n');
        w.flush();
      }
    } catch (Exception e) {
      throw new jvmumps.MError("WRITE failed: " + e);
    }
  }

  @SuppressWarnings("RedundantThrows")
  public int currentDevice() {
    return io;
  }

  // ---- M6 JOB / locks / TX ----------------------------------------
  @SuppressWarnings("RedundantThrows")
  public void job(String code) {
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
              System.getProperty("java.home") + "/bin/java",
              "-cp",
              System.getProperty("java.class.path"),
              "mjvm.cli.Mjvm",
              "-c",
              code);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);
      pb.start();
    } catch (Exception e) {
      throw new jvmumps.MError("JOB failed: " + e);
    }
  }

  @SuppressWarnings("RedundantThrows")
  public void lockAcquire(String key, long timeoutMs) {
    String k = key;
    locks.add(k);
    store.lockAcquire(k, Long.toString(ProcessHandle.current().pid()), timeoutMs);
  }

  @SuppressWarnings("RedundantThrows")
  public void lockRelease(String key) {
    locks.remove(key);
    store.lockRelease(key, Long.toString(ProcessHandle.current().pid()));
  }

  @SuppressWarnings("RedundantThrows")
  public void tstart() {
    tlevel.incrementAndGet();
    txUndo.add(new ArrayList<>());
  }

  @SuppressWarnings("RedundantThrows")
  public void tcommit() {
    if (tlevel.get() > 0) {
      tlevel.decrementAndGet();
      java.util.List<Object[]> top = txUndo.remove(txUndo.size() - 1);
      if (!txUndo.isEmpty()) {
        txUndo.get(txUndo.size() - 1).addAll(top);
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  public void trollback() {
    if (tlevel.get() > 0) {
      tlevel.decrementAndGet();
      java.util.List<Object[]> top = txUndo.remove(txUndo.size() - 1);
      for (int i = top.size() - 1; i >= 0; i--) {
        Object[] u = top.get(i);
        @SuppressWarnings("unchecked")
        java.util.List<String> path = (java.util.List<String>) u[0];
        MValue old = (MValue) u[1];
        String nm = path.get(0);
        if (old == null) {
          locals.remove(nm);
          store.kill(path, path.get(0).startsWith("^"));
        } else {
          if (!path.get(0).startsWith("^")) {
            locals.put(nm, old);
          }
          store.set(path, path.get(0).startsWith("^"), old);
        }
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  public int tlevel() {
    return tlevel.get();
  }

  /** Set $PIECE: replace pieces from..to of s delimited by d with v. */
  private static String setPieceOf(String s, String d, int from, int to, String v) {
    if (to < from) to = from;
    if (from < 1) from = 1;
    if (d.isEmpty()) return s;
    int dl = d.length();
    int piece = 1;
    int start = 0;
    int n = s.indexOf(d);
    while (piece < from && n >= 0) {
      start = n + dl;
      n = s.indexOf(d, start);
      piece++;
    }
    int end = n;
    while (piece < to && n >= 0) {
      n = s.indexOf(d, n + dl);
      piece++;
    }
    if (n < 0) end = s.length();
    else if (end < 0) end = s.length();
    String prefix = s.substring(0, start);
    String suffix = end < 0 ? "" : s.substring(end);
    return prefix + v + suffix;
  }

  /** Record undo for SET when inside a transaction. */
  @SuppressWarnings("RedundantThrows")
  public void recordUndo(java.util.List<String> path, boolean global, MValue old) {
    if (tlevel.get() > 0 && !txUndo.isEmpty()) {
      txUndo.get(txUndo.size() - 1).add(new Object[] {path, old});
    }
  }
}
