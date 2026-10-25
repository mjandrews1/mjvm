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

    // Pass 1: collect label positions (JMP/JMP0 targets)
    java.util.Set<Integer> labels = new java.util.HashSet<>();
    for (mjvm.ir.Ir op : prog) {
      if ((op.op == mjvm.ir.Ir.JMP || op.op == mjvm.ir.Ir.JMP0) && op.iarg > 0) {
        labels.add(op.iarg);
      }
    }

    for (int pc = 0; pc < prog.size(); pc++) {
      mjvm.ir.Ir op = prog.get(pc);
      // Emit label if this PC is a jump target
      if (labels.contains(pc)) {
        sb.append("  L").append(pc).append(": ;\n");
      }
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
        case mjvm.ir.Ir.STORE_ADD -> {
          sb.append("    s[p++] = rt.increment(\"")
              .append(esc(op.name))
              .append("\", true, s[--p].asNum());\n");
        }
        case mjvm.ir.Ir.JMP -> {
          // Use labeled break/continue in a wrapper loop
          if (op.iarg >= 0 && op.iarg < prog.size()) {
            if (op.iarg < pc) {
              sb.append("    continue L_root; // back to L").append(op.iarg).append("\n");
            } else {
              sb.append("    break L").append(op.iarg).append("; // forward\n");
            }
          }
        }
        case mjvm.ir.Ir.JMP0 -> {
          if (op.iarg >= 0 && op.iarg < prog.size()) {
            if (op.iarg < pc) {
              sb.append(
                  "    if (!mjvm.runtime.MRuntime.truthy(s[--p])) continue L_root; // back\n");
            } else {
              sb.append("    if (!mjvm.runtime.MRuntime.truthy(s[--p])) break L")
                  .append(op.iarg)
                  .append("; // forward\n");
            }
          }
        }
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
    // Only emit break if last op wasn't RET/RET_VALUE
    boolean lastIsRet =
        !prog.isEmpty()
            && (prog.get(prog.size() - 1).op == mjvm.ir.Ir.RET
                || prog.get(prog.size() - 1).op == mjvm.ir.Ir.RET_VALUE);
    if (!lastIsRet) sb.append("    break L_root;\n");
    sb.append("  }\n  }\n}\n");
    String result = sb.toString();
    if (System.getenv("JAVASRC_DEBUG") != null) {
      System.err.println("--- generated ---");
      System.err.println(result);
    }
    return result;
  }

  static String esc(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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
