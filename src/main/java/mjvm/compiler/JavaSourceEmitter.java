package mjvm.compiler;

import java.util.List;
import javax.tools.*;

/**
 * IR → Java source → bytecode compiler. Uses javax.tools.JavaCompiler to compile generated Java to
 * .class bytes, then loads via classloader.
 *
 * <p>Simpler than raw bytecode emission and JIT-optimized equally well. Generated code uses local
 * variables (no MValue[] array) for the value stack.
 */
public final class JavaSourceEmitter {

  /** Class bytes cache — keyed by source hash. Avoids re-running javac. */
  private static final java.util.concurrent.ConcurrentHashMap<String, byte[]> CLASS_CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static final java.util.concurrent.ConcurrentHashMap<String, Class<?>> LOADED_CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Compile IR to a Runnable class via Java source generation (cached). */
  public static Runnable compile(List<mjvm.ir.Ir> prog, String className, mjvm.runtime.MRuntime rt)
      throws Exception {
    String src = generate(prog, className);
    String hash = Integer.toHexString(src.hashCode());
    // Check loaded class cache first (fastest)
    Class<?> cl = LOADED_CACHE.get(hash);
    if (cl == null) {
      // Check class bytes cache (skip javac)
      byte[] classBytes = CLASS_CACHE.get(hash);
      if (classBytes == null) {
        classBytes = compileSource(src, className);
        CLASS_CACHE.put(hash, classBytes);
      }
      cl = new DefLoader(JavaSourceEmitter.class.getClassLoader()).define(className, classBytes);
      LOADED_CACHE.put(hash, cl);
    }
    return (Runnable) cl.getConstructor(mjvm.runtime.MRuntime.class).newInstance(rt);
  }

  /** Generate Java source from IR with control flow (labels, JMP, CALL). */
  static String generate(List<mjvm.ir.Ir> prog, String className) {
    String result = generateStructured(prog, className);
    if (result == null) result = generateDispatch(prog, className);
    if (System.getenv("JAVASRC_DEBUG") != null) {
      System.err.println("--- generated ---");
      System.err.println(result);
    }
    return result;
  }

  /**
   * Structured generation: labeled loops for backward jump targets, labeled blocks for forward jump
   * targets. A block opens before any loop its target escapes, so a forward jump out of a loop
   * breaks to a label that wraps the whole loop. Returns null when jump spans cross (unexpressible
   * with labeled break/continue) — callers fall back to dispatch generation.
   */
  static String generateStructured(List<mjvm.ir.Ir> prog, String className) {
    int n = prog.size();

    // Jump sites: forward targets become blocks, backward targets loops.
    java.util.Map<Integer, java.util.List<Integer>> fwdSites = new java.util.HashMap<>();
    java.util.Map<Integer, Integer> lastBackSite = new java.util.HashMap<>();
    for (int pc = 0; pc < n; pc++) {
      mjvm.ir.Ir op = prog.get(pc);
      if (op.op != mjvm.ir.Ir.JMP && op.op != mjvm.ir.Ir.JMP0) continue;
      int t = op.iarg;
      if (t < 0 || t >= n) continue; // out-of-range targets translate to return
      if (t > pc) fwdSites.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(pc);
      else lastBackSite.merge(t, pc, Math::max);
    }

    java.util.List<Struct> spans = new java.util.ArrayList<>();
    for (java.util.Map.Entry<Integer, java.util.List<Integer>> e : fwdSites.entrySet()) {
      int target = e.getKey();
      int open = java.util.Collections.min(e.getValue());
      for (java.util.Map.Entry<Integer, Integer> lb : lastBackSite.entrySet()) {
        int loop = lb.getKey();
        int close = lb.getValue() + 1;
        boolean siteInside = e.getValue().stream().anyMatch(s -> s > loop && s < close);
        // A forward jump out of a loop needs the block to wrap the whole loop.
        if (target >= close && siteInside) open = Math.min(open, loop);
      }
      spans.add(new Struct(false, open, target, target));
    }
    for (java.util.Map.Entry<Integer, Integer> lb : lastBackSite.entrySet()) {
      spans.add(new Struct(true, lb.getKey(), lb.getValue() + 1, lb.getKey()));
    }

    // Crossing spans are not expressible with labeled break/continue.
    for (int i = 0; i < spans.size(); i++) {
      for (int j = i + 1; j < spans.size(); j++) {
        Struct a = spans.get(i);
        Struct b = spans.get(j);
        boolean disjoint = a.close <= b.open || b.close <= a.open;
        boolean aInB = b.open <= a.open && a.close <= b.close;
        boolean bInA = a.open <= b.open && b.close <= a.close;
        if (!(disjoint || aInB || bInA)) return null;
      }
    }

    java.util.Map<Integer, java.util.List<Struct>> opensAt = new java.util.HashMap<>();
    for (Struct st : spans) {
      opensAt.computeIfAbsent(st.open, k -> new java.util.ArrayList<>()).add(st);
    }
    // Same open point: outer span (larger close) first; on close ties a block wraps a loop.
    for (java.util.List<Struct> l : opensAt.values()) {
      l.sort(
          (x, y) ->
              x.close != y.close
                  ? Integer.compare(y.close, x.close)
                  : Boolean.compare(x.loop, y.loop));
    }

    // Dead code scan: after an unconditional transfer (RET, or a JMP — break/continue/return),
    // ops until the next jump target are unreachable in the IR too; skip them at emission so
    // javac does not reject the class. Fall back to dispatch when a dead stretch contains a jump
    // or resumes against an unconditional transfer with no intervening brace.
    java.util.Set<Integer> targets = new java.util.HashSet<>();
    targets.addAll(fwdSites.keySet());
    targets.addAll(lastBackSite.keySet());
    java.util.Set<Integer> deadOps = new java.util.HashSet<>();
    boolean dead = false;
    int transferPc = -1;
    for (int pc = 0; pc < n; pc++) {
      if (dead && targets.contains(pc)) {
        boolean brace = false;
        for (Struct st : spans) {
          if (st.close > transferPc && st.close <= pc) {
            brace = true;
            break;
          }
        }
        if (!brace) return null; // resumed statement would follow a bare break/continue/return
        dead = false;
      }
      mjvm.ir.Ir op = prog.get(pc);
      if (dead) {
        if (op.op == mjvm.ir.Ir.JMP || op.op == mjvm.ir.Ir.JMP0) return null;
        deadOps.add(pc);
        continue;
      }
      if (op.op == mjvm.ir.Ir.RET || op.op == mjvm.ir.Ir.RET_VALUE || op.op == mjvm.ir.Ir.JMP) {
        dead = true;
        transferPc = pc;
      }
    }
    boolean deadAtEnd = dead;

    StringBuilder sb = new StringBuilder();
    sb.append("public final class ").append(className).append(" implements Runnable {\n");
    sb.append("  private final mjvm.runtime.MRuntime rt;\n");
    sb.append("  public ")
        .append(className)
        .append("(mjvm.runtime.MRuntime rt) { this.rt = rt; }\n");
    sb.append("  public void run() {\n");
    sb.append("    jvmumps.MValue[] s = new jvmumps.MValue[64];\n");
    sb.append("    int p = 0;\n");
    sb.append("  L_root: for (;;) {\n");

    java.util.Deque<Struct> openSpans = new java.util.ArrayDeque<>();
    for (int pc = 0; pc < n; pc++) {
      while (!openSpans.isEmpty() && openSpans.peek().close == pc) {
        closeSpan(sb, openSpans.pop(), prog);
      }
      for (Struct st : opensAt.getOrDefault(pc, java.util.List.of())) {
        sb.append("    L").append(st.id).append(st.loop ? ": for (;;) {\n" : ": {\n");
        openSpans.push(st);
      }
      if (!deadOps.contains(pc)) {
        emitOp(sb, prog.get(pc), pc, false, n);
      }
    }
    while (!openSpans.isEmpty()) {
      closeSpan(sb, openSpans.pop(), prog);
    }

    // Only emit break if last op wasn't RET/RET_VALUE or dead code
    boolean lastIsRet =
        !prog.isEmpty()
            && (prog.get(prog.size() - 1).op == mjvm.ir.Ir.RET
                || prog.get(prog.size() - 1).op == mjvm.ir.Ir.RET_VALUE);
    if (!deadAtEnd && !deadOps.contains(n - 1) && !lastIsRet) {
      sb.append("    break L_root;\n");
    }
    sb.append("  }\n  }\n}\n");
    return sb.toString();
  }

  /** Fallback: per-op switch dispatch — correct for any IR, still compiled by javac. */
  static String generateDispatch(List<mjvm.ir.Ir> prog, String className) {
    int n = prog.size();
    StringBuilder sb = new StringBuilder();
    sb.append("public final class ").append(className).append(" implements Runnable {\n");
    sb.append("  private final mjvm.runtime.MRuntime rt;\n");
    sb.append("  public ")
        .append(className)
        .append("(mjvm.runtime.MRuntime rt) { this.rt = rt; }\n");
    sb.append("  public void run() {\n");
    sb.append("    jvmumps.MValue[] s = new jvmumps.MValue[64];\n");
    sb.append("    int p = 0;\n");
    sb.append("    int __pc = 0;\n");
    sb.append("    __dl: for (;;) {\n");
    sb.append("      switch (__pc) {\n");
    for (int pc = 0; pc < n; pc++) {
      sb.append("      case ").append(pc).append(": {\n");
      mjvm.ir.Ir op = prog.get(pc);
      emitOp(sb, op, pc, true, n);
      boolean jumps =
          op.op == mjvm.ir.Ir.JMP
              || op.op == mjvm.ir.Ir.JMP0
              || op.op == mjvm.ir.Ir.RET
              || op.op == mjvm.ir.Ir.RET_VALUE;
      if (!jumps) sb.append("        __pc = ").append(pc + 1).append("; continue __dl;\n");
      sb.append("      }\n");
    }
    sb.append("      default: break __dl;\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append("  }\n}\n");
    return sb.toString();
  }

  /**
   * Close a span. A loop whose last back-edge is a JMP0 falls through when its condition is true,
   * so it must break out before the closing brace.
   */
  static void closeSpan(StringBuilder sb, Struct st, java.util.List<mjvm.ir.Ir> prog) {
    if (st.loop && prog.get(st.close - 1).op == mjvm.ir.Ir.JMP0) {
      sb.append("    break L").append(st.id).append(";\n");
    }
    sb.append("    }\n");
  }

  /** Emit one op. Jump ops differ by mode: labeled break/continue (structured) or __pc updates. */
  static void emitOp(StringBuilder sb, mjvm.ir.Ir op, int pc, boolean dispatch, int n) {
    switch (op.op) {
      case mjvm.ir.Ir.PUSH -> {
        if (op.iarg == 1) {
          sb.append("    s[p++] = jvmumps.MValue.ofNumber(").append(op.dval).append(");\n");
        } else {
          String esc = esc(op.ival == null ? "" : op.ival);
          sb.append("    s[p++] = jvmumps.MValue.ofString(\"").append(esc).append("\");\n");
        }
      }
      case mjvm.ir.Ir.LOAD -> {
        sb.append("    s[p++] = rt.getVar(\"").append(esc(op.name)).append("\");\n");
      }
      case mjvm.ir.Ir.STORE -> {
        sb.append("    rt.setVar(\"").append(esc(op.name)).append("\", s[--p]);\n");
      }
      case mjvm.ir.Ir.ADD ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(s[p-2].asNum() + s[p-1].asNum()); p--;\n");
      case mjvm.ir.Ir.SUB ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(s[p-2].asNum() - s[p-1].asNum()); p--;\n");
      case mjvm.ir.Ir.MUL ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(s[p-2].asNum() * s[p-1].asNum()); p--;\n");
      case mjvm.ir.Ir.DIV ->
          sb.append(
              "    { double y=s[p-1].asNum(); s[p-2] ="
                  + " jvmumps.MValue.ofNumber(y==0?0:s[p-2].asNum()/y); p--; }\n");
      case mjvm.ir.Ir.MOD ->
          sb.append(
              "    { double y=s[p-1].asNum(); double x=s[p-2].asNum(); s[p-2] ="
                  + " jvmumps.MValue.ofNumber(y==0?x:x-y*Math.floor(x/y)); p--; }\n");
      case mjvm.ir.Ir.CONCAT ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofString(s[p-2].asStr() + s[p-1].asStr()); p--;\n");
      case mjvm.ir.Ir.EQ ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.eq(s[p-2],s[p-1])?1:0);"
                  + " p--;\n");
      case mjvm.ir.Ir.NE ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.eq(s[p-2],s[p-1])?0:1);"
                  + " p--;\n");
      case mjvm.ir.Ir.LT ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-2],s[p-1])?1:0);"
                  + " p--;\n");
      case mjvm.ir.Ir.GT ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-1],s[p-2])?1:0);"
                  + " p--;\n");
      case mjvm.ir.Ir.LE ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-1],s[p-2])?0:1);"
                  + " p--;\n");
      case mjvm.ir.Ir.GE ->
          sb.append(
              "    s[p-2] = jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-2],s[p-1])?0:1);"
                  + " p--;\n");
      case mjvm.ir.Ir.WRITE -> sb.append("    rt.write(s[--p].asStr());\n");
      case mjvm.ir.Ir.WRITE_NL -> sb.append("    rt.writeNl();\n");
      case mjvm.ir.Ir.END_WRITE -> sb.append("    rt.endWrite();\n");
      case mjvm.ir.Ir.WRITE_CHAR -> sb.append("    rt.writeChar((int) s[--p].asNum());\n");
      case mjvm.ir.Ir.WRITE_COL -> sb.append("    rt.writeCol((int) s[--p].asNum());\n");
      case mjvm.ir.Ir.STORE_ADD -> {
        sb.append("    s[p++] = rt.increment(\"")
            .append(esc(op.name))
            .append("\", true, s[--p].asNum());\n");
      }
      case mjvm.ir.Ir.JMP -> emitJump(sb, op.iarg, false, pc, dispatch, n);
      case mjvm.ir.Ir.JMP0 -> emitJump(sb, op.iarg, true, pc, dispatch, n);
      case mjvm.ir.Ir.CALL -> {
        String esc = esc(op.name == null ? "" : op.name);
        sb.append("    rt.call(\"").append(esc).append("\", ").append(op.iarg).append(");\n");
      }
      case mjvm.ir.Ir.EVAL_EXPR -> {
        String compiled = MExprJava.compile(op.ival, "s", "p");
        if (compiled != null) {
          sb.append(compiled);
        } else {
          // Try function call: $$fn(args) or fn^RTN(args)
          String fn = MExprJava.compileFuncCall(op.ival, "s", "p");
          if (fn != null) {
            sb.append(fn);
          } else {
            sb.append("    s[p++] = rt.evalExpr(\"").append(esc(op.ival)).append("\");\n");
          }
        }
      }
      case mjvm.ir.Ir.BIND -> {
        sb.append("    { jvmumps.MValue bv = p > 0 ? s[--p] : jvmumps.MValue.EMPTY; rt.setVar(\"")
            .append(esc(op.name))
            .append("\", bv); }\n");
      }
      case mjvm.ir.Ir.RET, mjvm.ir.Ir.RET_VALUE -> sb.append("    return;\n");
      default -> sb.append("    // op ").append(op.op).append("\n");
    }
  }

  /** Emit a JMP/JMP0. Out-of-range targets exit the program, matching the IR machine. */
  static void emitJump(
      StringBuilder sb, int target, boolean conditional, int pc, boolean dispatch, int n) {
    if (target < 0 || target >= n) {
      if (conditional) sb.append("    if (!mjvm.runtime.MRuntime.truthy(s[--p])) return;\n");
      else sb.append("    return;\n");
      return;
    }
    if (dispatch) {
      if (conditional) {
        sb.append("    if (!mjvm.runtime.MRuntime.truthy(s[--p])) { __pc = ")
            .append(target)
            .append("; continue __dl; }\n");
        sb.append("    __pc = ").append(pc + 1).append("; continue __dl;\n");
      } else {
        sb.append("    __pc = ").append(target).append("; continue __dl;\n");
      }
      return;
    }
    String cond = conditional ? "if (!mjvm.runtime.MRuntime.truthy(s[--p])) " : "";
    if (target <= pc) {
      sb.append("    ").append(cond).append("continue L").append(target).append(";\n");
    } else {
      sb.append("    ").append(cond).append("break L").append(target).append(";\n");
    }
  }

  /** Control-flow span: a labeled loop (backward target) or labeled block (forward target). */
  private static final class Struct {
    final boolean loop;
    final int open;
    final int close;
    final int id;

    Struct(boolean loop, int open, int close, int id) {
      this.loop = loop;
      this.open = open;
      this.close = close;
      this.id = id;
    }
  }

  static String esc(String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') out.append("\\\\");
      else if (c == '"') out.append("\\\"");
      else if (c == '\n') out.append("\\n");
      else if (c == '\r') out.append("\\r");
      else if (c == '\t') out.append("\\t");
      else if (c < 0x20 || c > 0x7e) out.append(String.format("\\u%04x", (int) c));
      else out.append(c);
    }
    return out.toString();
  }

  /** Compile Java source to class bytes. */
  static byte[] compileSource(String src, String className) throws Exception {
    JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
    if (comp == null) throw new jvmumps.MError("no Java compiler (need JDK, not JRE)");
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    try (StandardJavaFileManager fm = comp.getStandardFileManager(diags, null, null)) {
      String srcPath = System.getProperty("java.io.tmpdir") + "/" + className + ".java";
      java.nio.file.Path sp = java.nio.file.Path.of(srcPath);
      java.nio.file.Files.writeString(sp, src);
      Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(List.of(sp));
      String outDir = System.getProperty("java.io.tmpdir");
      boolean ok =
          comp.getTask(
                  null,
                  fm,
                  diags,
                  List.of("-d", outDir, "-cp", System.getProperty("java.class.path")),
                  null,
                  units)
              .call();
      if (!ok) {
        StringBuilder err = new StringBuilder("javac failed:\n");
        for (var d : diags.getDiagnostics()) err.append(d).append('\n');
        err.append("--- source ---\n").append(src);
        throw new jvmumps.MError(err.toString());
      }
      return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(outDir, className + ".class"));
    }
  }

  static final class DefLoader extends ClassLoader {
    DefLoader(ClassLoader parent) {
      super(parent);
    }

    Class<?> define(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }
}
