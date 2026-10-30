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
  // mjvm#32 sockets: "SOCKET:LISTEN=port:TCP" opens a listener; each accepted
  // connection gets its own device number (1000+cid) so plain USE/READ/WRITE/CLOSE
  // work on it. $$SOCKWAIT drives the event loop, $$SOCKSELECT selects a connection.
  private final Map<Integer, java.nio.channels.ServerSocketChannel> listeners = new HashMap<>();
  private final Map<Integer, java.nio.channels.SocketChannel> conns = new HashMap<>();
  private final Map<String, Integer> cidDev = new HashMap<>();
  private final Map<Integer, StringBuilder> connBuf = new HashMap<>();
  private java.nio.channels.Selector selector;
  private int nextCid = 1;
  private volatile String lastKey = "";
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

              @Override
              public String sockWait(int dev, double secs) {
                return MRuntime.this.sockWait(dev, secs);
              }

              @Override
              public int sockSelect(int dev, String cid) {
                return MRuntime.this.sockSelect(dev, cid);
              }

              @Override
              public String lastKey() {
                return MRuntime.this.lastKey();
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
    final double[] nums; // pre-parsed numeric value per token (kind 0); 0 elsewhere

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
      this.nums = new double[knd.size()];
      for (int j = 0; j < knd.size(); j++) {
        this.kinds[j] = knd.get(j);
        if (knd.get(j) == 0) this.nums[j] = Double.parseDouble(tok.get(j));
      }
    }
  }

  /** M11: run a raw M expression through JvMumps ExprEval. */
  public jvmumps.MValue evalExpr(String src) {
    // Fast path: try cached compiled expression for simple patterns. Any error falls through
    // to the trap-aware evaluator — the fast path must be semantically transparent (C115).
    if (src != null && !src.isEmpty() && src.length() < 32) {
      try {
        jvmumps.MValue fast = fastEvalCached(src);
        if (fast != null) return fast;
      } catch (jvmumps.MError fallThroughToTrapPath) {
        // undefined variable, etc. — re-evaluate below so $ETRAP fires exactly as before
      }
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
    SubRef sr = subRefOf(name);
    if (sr != null) {
      StringBuilder sb = new StringBuilder(name.length() + 4);
      sb.append(sr.base);
      for (int i = 0; i < sr.subs.length; i++) {
        sb.append((char) 1);
        if (sr.kinds[i] == SubRef.NAME && rt != null) {
          // evalSub semantics: numeric values canonicalize via mstr, strings pass through;
          // a null rt (static parse) leaves the raw text, matching evalSub.
          MValue v = rt.evalExpr(sr.subs[i]);
          sb.append(v.isnum ? jvmumps.ExprEval.mstr(v.num) : v.str);
        } else {
          sb.append(sr.subs[i]);
        }
      }
      return sb.toString();
    }
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
    SubRef sr = subRefOf(name);
    if (sr != null) {
      List<String> path = new ArrayList<>(sr.subs.length + 1);
      path.add(sr.base);
      for (int i = 0; i < sr.subs.length; i++) {
        if (sr.kinds[i] == SubRef.NAME && rt != null) {
          // subOf semantics: variable reference → evalExpr().asStr(); null rt keeps raw text
          path.add(rt.evalExpr(sr.subs[i]).asStr());
        } else {
          path.add(sr.subs[i]);
        }
      }
      return path;
    }
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

  /**
   * Parsed-name cache (mjvm#7): subscripted references like a(i) re-parse their name string on
   * every setVar/getVar otherwise. The structure (base + sub kinds) is static per raw name, so it
   * is parsed once and cached; NAME subscripts are still evaluated per call. Unrecognized shapes
   * cache a COMPLEX marker and always take the full parse.
   */
  private static final java.util.concurrent.ConcurrentHashMap<String, SubRef> subRefCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  static final class SubRef {
    static final byte NUM = 0;
    static final byte NAME = 1;
    static final byte STR = 2;
    static final SubRef COMPLEX = new SubRef("", new String[0], new byte[0]);

    final String base;
    final String[] subs;
    final byte[] kinds;

    SubRef(String base, String[] subs, byte[] kinds) {
      this.base = base;
      this.subs = subs;
      this.kinds = kinds;
    }
  }

  /** Cached parse of a subscripted name; null-when-complex is signaled by SubRef.COMPLEX. */
  static SubRef subRefOf(String name) {
    if (name.indexOf('(') < 0) return null;
    SubRef sr = subRefCache.get(name);
    if (sr == null) {
      if (subRefCache.size() > 4096) return null; // unbounded loops with generated names: uncached
      sr = parseSubRef(name);
      subRefCache.put(name, sr);
    }
    return sr == SubRef.COMPLEX ? null : sr;
  }

  /**
   * Recognize only the hot shape: base NAME (optional ^) with top-level comma-separated subs, each
   * a quoted literal, numeric literal, or simple variable name — no quotes/parens/@ inside, no
   * empty subscripts (their arity rules differ in the full parser).
   */
  static SubRef parseSubRef(String name) {
    int p = name.indexOf('(');
    int last = name.length() - 1;
    if (p < 0 || name.charAt(last) != ')') return SubRef.COMPLEX;
    String base = name.substring(0, p);
    int bi = base.isEmpty() ? -1 : 0;
    if (bi == 0 && base.charAt(0) == '^') bi = 1;
    if (bi < 0 || bi >= base.length()) return SubRef.COMPLEX;
    char b0 = base.charAt(bi);
    if (!(Character.isLetter(b0) || b0 == '%')) return SubRef.COMPLEX;
    for (int i = bi + 1; i < base.length(); i++) {
      char c = base.charAt(i);
      if (!(Character.isLetterOrDigit(c) || c == '%')) return SubRef.COMPLEX;
    }
    String inner = name.substring(p + 1, last);
    if (inner.indexOf('"') >= 0
        || inner.indexOf('(') >= 0
        || inner.indexOf(')') >= 0
        || inner.indexOf('@') >= 0) {
      return SubRef.COMPLEX;
    }
    String[] parts = inner.split(",", -1);
    String[] subs = new String[parts.length];
    byte[] kinds = new byte[parts.length];
    for (int i = 0; i < parts.length; i++) {
      String s = parts[i].trim();
      if (s.isEmpty()) return SubRef.COMPLEX;
      if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
        subs[i] = s.substring(1, s.length() - 1);
        kinds[i] = SubRef.STR;
      } else if (isNumericLiteral(s)) {
        subs[i] = jvmumps.ExprEval.mstr(Double.parseDouble(s));
        kinds[i] = SubRef.NUM;
      } else if (Character.isLetter(s.charAt(0)) || s.charAt(0) == '%') {
        for (int j = 1; j < s.length(); j++) {
          char c = s.charAt(j);
          if (!(Character.isLetterOrDigit(c) || c == '%')) return SubRef.COMPLEX;
        }
        subs[i] = s;
        kinds[i] = SubRef.NAME;
      } else {
        return SubRef.COMPLEX;
      }
    }
    return new SubRef(base, subs, kinds);
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
    // locals never holds null values (STORE converts dvals-nulls; NEW/KILL remove), so one
    // lookup replaces the containsKey+get pair on the hottest read path.
    MValue cached = locals.get(name);
    if (cached != null) return cached;
    String key = keyOf(name, this);
    boolean glob = key.startsWith("^");
    if (!glob && store.data(splitKey(key), false) % 2 == 0) {
      throw new jvmumps.MError("Undefined local variable");
    }
    return store.get(key, glob);
  }

  /**
   * Peek without throwing: the pre-NEW value for IrMachine's NEW journal (mjvm#23). Returns null
   * when the variable is undefined.
   */
  MValue peekVar(String name) {
    MValue cached = locals.get(name);
    if (cached != null) return cached;
    try {
      return getVar(name);
    } catch (jvmumps.MError e) {
      return null;
    }
  }

  /** Get numeric value directly (skips string format/parse). */
  @SuppressWarnings("RedundantThrows")
  public double getNum(String name) {
    MValue cached = locals.get(name);
    if (cached != null) return cached.asNum();
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
      // Subscripted names must not enter the raw-key locals cache: the key would be the
      // unevaluated name (e.g. "A(I)"), stale whenever the subscript changes. The store is
      // the truth for subscripted values; getVar resolves via keyOf.
      if (name.indexOf('(') < 0) locals.put(name, MValue.ofNumber(val));
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
      // No raw-key locals cache for subscripted names (stale when the subscript changes —
      // S I=1 S A(I)="one" S I=2 W A(I) must read the store, not "A(I)")
      if (name.indexOf('(') < 0) locals.put(name, v);
      store.set(path, false, v);
    }
  }

  /** Fast evaluation using cached tokenization. Returns null if pattern not recognized. */
  private jvmumps.MValue fastEvalCached(String src) {
    if (src.length() < 1 || src.length() > 31) return null;
    char c0 = src.charAt(0);
    if (c0 == '$') {
      // $P(X,"d",N) / $PIECE(X,"d",N) — the dominant hot idiom
      int p1 = src.indexOf('(');
      if (p1 == 2 || p1 == 5) {
        String fn = src.substring(0, p1);
        if (fn.equals("$P") || fn.equals("$PIECE")) return fastPiece(src);
      }
      return null;
    }
    if (src.indexOf('(') >= 0 || src.indexOf('@') >= 0 || src.indexOf('"') >= 0) return null;
    CompiledExpr ce = exprCache.get(src);
    if (ce == null) {
      if (exprCache.size() > 500) return null;
      ce = new CompiledExpr(src);
      exprCache.put(src, ce);
    }
    int n = ce.tokens.length;
    if (n == 1) {
      if (ce.kinds[0] == 1) return getVar(ce.tokens[0]);
      if (ce.kinds[0] == 0) return jvmumps.MValue.ofNumber(ce.nums[0]);
      return null;
    }
    if ((n & 1) == 0) return null; // operands and operators must alternate
    // Simple patterns: VAR OP NUM, VAR OP VAR
    if (ce.tokens.length == 3 && ce.kinds[0] == 1) {
      String left = ce.tokens[0];
      String op = ce.tokens[1];
      if (op.length() <= 2 && !Character.isLetterOrDigit(op.charAt(0))) {
        jvmumps.MValue lv = getVar(left);
        jvmumps.MValue rv;
        if (ce.kinds[2] == 0) {
          rv = jvmumps.MValue.ofNumber(ce.nums[2]);
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
      return null;
    }
    return evalArithChain(ce);
  }

  /**
   * Pure arithmetic chain over cached tokens: NUM/NAME operands with single-char + - * / \ #,
   * evaluated strictly left-to-right (M has no operator precedence — C30: 1+2*3 = 9).
   * Numeric-operand names only; anything else declines to the full evaluator. Operator semantics
   * match ExprEval: / and \ yield 0 on zero divisor, # is M modulo (x - y*floor(x/y), C22).
   */
  private jvmumps.MValue evalArithChain(CompiledExpr ce) {
    int n = ce.tokens.length;
    if (n < 3 || (n & 1) == 0) return null;
    int cnt = (n + 1) / 2;
    double[] vs = new double[cnt];
    char[] ops = new char[cnt - 1];
    int vi = 0, oi = 0, i = 0;
    while (i < n) {
      int kind = ce.kinds[i];
      if (kind == 1) {
        jvmumps.MValue v = getVar(ce.tokens[i]);
        if (!v.isnum) return null;
        vs[vi++] = v.num;
      } else if (kind == 0) {
        vs[vi++] = ce.nums[i];
      } else {
        return null;
      }
      i++;
      if (i < n) {
        String t = ce.tokens[i];
        if (ce.kinds[i] != 3 || t.length() != 1) return null;
        char opc = t.charAt(0);
        if (opc != '+' && opc != '-' && opc != '*' && opc != '/' && opc != '\\' && opc != '#') {
          return null;
        }
        ops[oi++] = opc;
        i++;
      }
    }
    // M arithmetic is strictly left-to-right (C30: 1+2*3 = 9) — no operator precedence.
    double r = vs[0];
    for (int k = 0; k < ops.length; k++) r = chainApply(ops[k], r, vs[k + 1]);
    return jvmumps.MValue.ofNumber(r);
  }

  private static double chainApply(char opc, double x, double y) {
    switch (opc) {
      case '+':
        return x + y;
      case '-':
        return x - y;
      case '*':
        return x * y;
      case '/':
        return y == 0 ? 0 : x / y;
      case '\\':
        return y == 0 ? 0 : Math.floor(x / y);
      default: // '#'
        return y == 0 ? 0 : x - y * Math.floor(x / y);
    }
  }

  /** $P(NAME,"d",CHAIN) fast shape — parse once, cache; evaluate via ExprEval.pieceOf. */
  private jvmumps.MValue fastPiece(String src) {
    PiecePat pp = pieceCache.get(src);
    if (pp == null) {
      if (pieceCache.size() > 200) return null;
      pp = parsePiece(src);
      if (pp == null) return null;
      pieceCache.put(src, pp);
    }
    jvmumps.MValue s = getVar(pp.varName);
    jvmumps.MValue num = fastEvalCached(pp.pieceArg);
    if (num == null || !num.isnum) return null;
    return jvmumps.MValue.ofString(
        jvmumps.ExprEval.pieceOf(s.asStr(), pp.delim, (int) num.num, -1));
  }

  private static final java.util.concurrent.ConcurrentHashMap<String, PiecePat> pieceCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static final class PiecePat {
    final String varName;
    final String delim;
    final String pieceArg;

    PiecePat(String varName, String delim, String pieceArg) {
      this.varName = varName;
      this.delim = delim;
      this.pieceArg = pieceArg;
    }
  }

  /**
   * Recognize only $P(NAME,"STR",CHAIN) / $PIECE(NAME,"STR",CHAIN): simple NAME first arg, a quoted
   * delimiter, and a comma-free arithmetic chain (no parens, quotes, @, $). The chain is evaluated
   * through fastEvalCached, so unknown shapes decline there.
   */
  private static PiecePat parsePiece(String src) {
    int p1 = src.indexOf('(');
    int last = src.length() - 1;
    if (p1 < 0 || src.charAt(last) != ')') return null;
    String fn = src.substring(0, p1);
    if (!fn.equals("$P") && !fn.equals("$PIECE")) return null;
    int c1 = src.indexOf(',', p1);
    if (c1 < 0) return null;
    String varName = src.substring(p1 + 1, c1).trim();
    if (varName.isEmpty()) return null;
    for (int i = 0; i < varName.length(); i++) {
      char c = varName.charAt(i);
      boolean ok = Character.isLetterOrDigit(c) || c == '%' || (i == 0 && c == '^');
      if (!ok) return null;
    }
    if (src.charAt(c1 + 1) != '"') return null;
    int q2 = src.indexOf('"', c1 + 2);
    if (q2 < 0 || src.charAt(q2 + 1) != ',') return null;
    String delim = src.substring(c1 + 2, q2);
    String arg = src.substring(q2 + 2, last).trim();
    if (arg.isEmpty()) return null;
    for (int i = 0; i < arg.length(); i++) {
      char c = arg.charAt(i);
      if (c == '(' || c == '"' || c == '@' || c == '$' || c == ',' || c == '_') return null;
    }
    return new PiecePat(varName, delim, arg);
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
      if (path != null && path.startsWith("SOCKET:")) {
        openSocket(n, path);
        return;
      }
      java.nio.file.Path p = java.nio.file.Path.of(path);
      // Write-only device: a missing file is fine (CREATE below); the reader
      // is simply absent and READs return "" (mjvm#39 family — the demo ILS
      // writes logs/dumps to devices that do not exist before the first run).
      try {
        devices.put(n, java.nio.file.Files.newBufferedReader(p));
      } catch (java.nio.file.NoSuchFileException e) {
        devices.remove(n);
      }
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

  /** mjvm#32: parse "SOCKET:LISTEN=port[:TCP]" (server) or "SOCKET:CONNECT=host:port[:TCP]". */
  private void openSocket(int n, String spec) throws java.io.IOException {
    if (selector == null) selector = java.nio.channels.Selector.open();
    String[] parts = spec.split(":");
    String mode = parts.length > 1 ? parts[1] : "";
    if (mode.startsWith("LISTEN=")) {
      int port = Integer.parseInt(mode.substring("LISTEN=".length()).split("\\|")[0].trim());
      java.nio.channels.ServerSocketChannel ssc = java.nio.channels.ServerSocketChannel.open();
      ssc.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);
      ssc.bind(new java.net.InetSocketAddress(port));
      ssc.configureBlocking(false);
      ssc.register(selector, java.nio.channels.SelectionKey.OP_ACCEPT);
      listeners.put(n, ssc);
      io = n;
    } else if (mode.startsWith("CONNECT=")) {
      // spec form: SOCKET:CONNECT=host|port  (pipe keeps ':' free for the spec split)
      String[] hp = mode.substring("CONNECT=".length()).split("\\|");
      java.nio.channels.SocketChannel sc =
          java.nio.channels.SocketChannel.open(
              new java.net.InetSocketAddress(hp[0], Integer.parseInt(hp[1])));
      sc.configureBlocking(false);
      sc.register(selector, java.nio.channels.SelectionKey.OP_READ);
      String cid = "c" + (nextCid++);
      int cdev = 1000 + nextCid;
      conns.put(cdev, sc);
      cidDev.put(cid, cdev);
      connBuf.put(cdev, new StringBuilder());
      lastKey = "CONNECT|" + cid;
      io = cdev;
      devices.put(n, null); // mark caller's device number open
    } else {
      throw new jvmumps.MError("OPEN failed: bad socket spec " + spec);
    }
  }

  /** mjvm#32: one event on the selector; returns and sets $KEY. "" on timeout. */
  public String sockWait(int dev, double secs) {
    try {
      if (selector == null) return "";
      long deadline = System.currentTimeMillis() + (long) (secs * 1000);
      while (true) {
        long waitMs = deadline - System.currentTimeMillis();
        if (waitMs <= 0) return "";
        int n = selector.select(waitMs);
        if (n == 0) continue;
        java.util.Iterator<java.nio.channels.SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          java.nio.channels.SelectionKey k = it.next();
          it.remove();
          if (!k.isValid()) continue;
          if (k.isAcceptable()) {
            java.nio.channels.ServerSocketChannel ssc =
                (java.nio.channels.ServerSocketChannel) k.channel();
            java.nio.channels.SocketChannel sc = ssc.accept();
            if (sc == null) continue;
            sc.configureBlocking(false);
            sc.register(selector, java.nio.channels.SelectionKey.OP_READ);
            String cid = "s" + (nextCid++);
            int cdev = 1000 + nextCid;
            conns.put(cdev, sc);
            cidDev.put(cid, cdev);
            connBuf.put(cdev, new StringBuilder());
            lastKey = "CONNECT|" + cid;
            return lastKey;
          }
          if (k.isReadable()) {
            java.nio.channels.SocketChannel sc = (java.nio.channels.SocketChannel) k.channel();
            Integer cdev = null;
            for (Map.Entry<Integer, java.nio.channels.SocketChannel> e : conns.entrySet()) {
              if (e.getValue() == sc) {
                cdev = e.getKey();
                break;
              }
            }
            if (cdev == null) continue;
            String cid = null;
            for (Map.Entry<String, Integer> e : cidDev.entrySet()) {
              if (e.getValue().equals(cdev)) {
                cid = e.getKey();
                break;
              }
            }
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8192);
            int r = sc.read(bb);
            if (r < 0) {
              lastKey = "EOF|" + cid;
              return lastKey;
            }
            if (r > 0) {
              connBuf
                  .get(cdev)
                  .append(new String(bb.array(), 0, r, java.nio.charset.StandardCharsets.UTF_8));
            }
            lastKey = "READ|" + cid;
            return lastKey;
          }
        }
      }
    } catch (Exception e) {
      throw new jvmumps.MError("SOCKWAIT failed: " + e);
    }
  }

  /** mjvm#32: make connection cid of listener dev current; returns its device number. */
  public int sockSelect(int dev, String cid) {
    Integer cdev = cidDev.get(cid);
    if (cdev == null) return 0;
    io = cdev;
    return cdev;
  }

  public String lastKey() {
    return lastKey;
  }

  @SuppressWarnings("RedundantThrows")
  public void closeDevice(int n) {
    try {
      if (listeners.containsKey(n)) {
        listeners.remove(n).close();
      }
      if (conns.containsKey(n)) {
        java.nio.channels.SocketChannel sc = conns.remove(n);
        try {
          sc.close();
        } catch (Exception ignored) {
        }
        connBuf.remove(n);
        String cid = null;
        for (Map.Entry<String, Integer> e : cidDev.entrySet()) {
          if (e.getValue() == n) {
            cid = e.getKey();
            break;
          }
        }
        if (cid != null) cidDev.remove(cid);
      }
      if (devices.containsKey(n)) {
        java.io.BufferedReader r = devices.remove(n);
        if (r != null) r.close();
      }
      if (writers.containsKey(n)) writers.remove(n).close();
    } catch (Exception ignored) {
    }
    if (io == n) io = 0;
  }

  @SuppressWarnings("RedundantThrows")
  public void useDevice(int n) {
    if (n != 0
        && !devices.containsKey(n)
        && !writers.containsKey(n)
        && !listeners.containsKey(n)
        && !conns.containsKey(n)) {
      throw new jvmumps.MError("USE on unopened device");
    }
    io = n;
  }

  @SuppressWarnings("RedundantThrows")
  public String readDevice() {
    try {
      java.nio.channels.SocketChannel sc = conns.get(io);
      if (sc != null) {
        // drain whatever is readable into the line buffer, then take one line
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8192);
        int r;
        while ((r = sc.read(bb)) > 0) {
          connBuf
              .get(io)
              .append(new String(bb.array(), 0, r, java.nio.charset.StandardCharsets.UTF_8));
          bb.clear();
        }
        StringBuilder buf = connBuf.get(io);
        int nl = buf.indexOf("\n");
        if (nl < 0) return "";
        String line = buf.substring(0, nl);
        buf.delete(0, nl + 1);
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        return line;
      }
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
      java.nio.channels.SocketChannel sc = conns.get(n);
      if (sc != null) {
        // raw write, no auto-delimiter (HTTP-style protocols carry their own)
        sc.write(java.nio.ByteBuffer.wrap(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return;
      }
      if (listeners.containsKey(n)) {
        if (s.isEmpty()) return; // endWrite()/writeNl() on a listener is a no-op
        throw new jvmumps.MError("cannot WRITE to a listening socket");
      }
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
          if (!path.get(0).startsWith("^") && nm.indexOf('(') < 0) {
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
