package mjvm.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import jvmumps.MError;
import mjvm.ir.Ir;

/**
 * mJVM M2 — emit a .class for IR via generated Java + javac.
 *
 * Control flow uses a pc-switch loop (Java has no goto — reserved, unused).
 * jvmumps#64: full IR coverage so make smoke / make itest can run in CI.
 */
public final class ClassEmitter {

    public static String emit(List<Ir> prog, String className, Path workDir)
            throws IOException {
        String src = toJava(prog, className);
        Path javaFile = workDir.resolve(className + ".java");
        Files.createDirectories(workDir);
        Files.writeString(javaFile, src);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new MError("no system Java compiler (need JDK, not JRE)");
        }
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        java.io.PrintStream eps = new java.io.PrintStream(err, true);
        int rc = javac.run(null, eps, eps,
                "-classpath", System.getProperty("java.class.path"),
                "-d", workDir.toString(),
                javaFile.toString());
        if (rc != 0) {
            String msg = err.toString().trim();
            // JVM 64KB method limit: "code too large" on fat unrolls
            if (msg.contains("code too large") || msg.contains("too large")) {
                throw new MError("javac failed for " + className
                        + " (method too large — reduce unroll / split run()): " + msg);
            }
            throw new MError("javac failed for " + className + ": " + msg);
        }
        return className;
    }

    static String toJava(List<Ir> prog, String className) {
        StringBuilder b = new StringBuilder();
        b.append("public final class ").append(className).append(" implements Runnable {\n");
        b.append("  private final mjvm.runtime.MRuntime rt;\n");
        b.append("  public ").append(className).append("(mjvm.runtime.MRuntime rt) { this.rt = rt; }\n");
        b.append("  private static jvmumps.MValue add(jvmumps.MValue a, jvmumps.MValue b) { return jvmumps.MValue.ofNumber(a.asNum()+b.asNum()); }\n");
        b.append("  private static jvmumps.MValue sub(jvmumps.MValue a, jvmumps.MValue b) { return jvmumps.MValue.ofNumber(a.asNum()-b.asNum()); }\n");
        b.append("  private static jvmumps.MValue mul(jvmumps.MValue a, jvmumps.MValue b) { return jvmumps.MValue.ofNumber(a.asNum()*b.asNum()); }\n");
        b.append("  private static jvmumps.MValue div(jvmumps.MValue a, jvmumps.MValue b) { return jvmumps.MValue.ofNumber(b.asNum()==0?0:a.asNum()/b.asNum()); }\n");
        b.append("  private static jvmumps.MValue mod(jvmumps.MValue a, jvmumps.MValue b) { double x=a.asNum(),y=b.asNum(); return jvmumps.MValue.ofNumber(y==0?x:x-y*Math.floor(x/y)); }\n");
        b.append("  private static String text(jvmumps.MValue v) { return v==null?\"\":(v.isnum?jvmumps.ExprEval.mstr(v.asNum()):v.asStr()); }\n");
        b.append("  private static void failBad(int op) { throw new jvmumps.MError(\"bad IR op \" + op); }\n");
        b.append("  public void run() {\n");
        b.append("    jvmumps.MValue[] s = new jvmumps.MValue[64];\n");
        b.append("    int p = 0;\n");
        b.append("    java.util.ArrayDeque<Integer> rets = new java.util.ArrayDeque<>();\n");
        b.append("    int pc = 0;\n");
        b.append("    for (;;) {\n");
        b.append("      switch (pc) {\n");
        for (int pc = 0; pc < prog.size(); pc++) {
            Ir op = prog.get(pc);
            int next = pc + 1;
            b.append("        case ").append(pc).append(": {\n");
            switch (op.op) {
                case Ir.PUSH -> {
                    if (op.iarg == 1) {
                        b.append("          s[p++] = jvmumps.MValue.ofNumber(").append(op.dval).append(");\n");
                    } else {
                        b.append("          s[p++] = jvmumps.MValue.ofString(")
                                .append(jstr(op.ival == null ? "" : op.ival)).append(");\n");
                    }
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.LOAD -> {
                    b.append("          s[p++] = rt.getVar(").append(jstr(op.name)).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.STORE -> {
                    if (op.name != null && op.name.startsWith("@")) {
                        b.append("          { jvmumps.MValue v = s[--p]; String t = rt.evalExpr(")
                                .append(jstr(op.name.substring(1))).append(").asStr(); rt.setVar(t, v); }\n");
                    } else if (op.name != null && op.name.startsWith("$")) {
                        b.append("          rt.setSpecial(").append(jstr(op.name)).append(", s[--p]);\n");
                    } else {
                        b.append("          rt.setVar(").append(jstr(op.name)).append(", s[--p]);\n");
                    }
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.ADD, Ir.SUB, Ir.MUL, Ir.DIV, Ir.MOD, Ir.CONCAT,
                     Ir.EQ, Ir.LT, Ir.GT, Ir.LE, Ir.GE, Ir.NE -> {
                    String expr = switch (op.op) {
                        case Ir.ADD -> "add(s[p-2], s[p-1])";
                        case Ir.SUB -> "sub(s[p-2], s[p-1])";
                        case Ir.MUL -> "mul(s[p-2], s[p-1])";
                        case Ir.DIV -> "div(s[p-2], s[p-1])";
                        case Ir.MOD -> "mod(s[p-2], s[p-1])";
                        case Ir.CONCAT -> "jvmumps.MValue.ofString(s[p-2].asStr() + s[p-1].asStr())";
                        case Ir.EQ -> "jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.eq(s[p-2], s[p-1]) ? 1 : 0)";
                        case Ir.LT -> "jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-2], s[p-1]) ? 1 : 0)";
                        case Ir.GT -> "jvmumps.MValue.ofNumber(mjvm.runtime.MRuntime.lt(s[p-1], s[p-2]) ? 1 : 0)";
                        case Ir.LE -> "jvmumps.MValue.ofNumber(!mjvm.runtime.MRuntime.lt(s[p-1], s[p-2]) ? 1 : 0)";
                        case Ir.GE -> "jvmumps.MValue.ofNumber(!mjvm.runtime.MRuntime.lt(s[p-2], s[p-1]) ? 1 : 0)";
                        default -> "jvmumps.MValue.ofNumber(!mjvm.runtime.MRuntime.eq(s[p-2], s[p-1]) ? 1 : 0)";
                    };
                    b.append("          s[p-2] = ").append(expr).append("; p--;\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.WRITE -> {
                    b.append("          rt.write(text(s[--p]));\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.WRITE_NL -> {
                    b.append("          rt.writeNl();\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.END_WRITE -> {
                    b.append("          rt.endWrite();\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.WRITE_CHAR -> {
                    b.append("          rt.writeChar((int)s[--p].asNum());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.WRITE_COL -> {
                    b.append("          rt.writeCol((int)s[--p].asNum());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.EVAL_EXPR -> {
                    b.append("          s[p++] = rt.evalExpr(").append(jstr(op.ival == null ? "" : op.ival)).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.XECUTE -> {
                    b.append("          { jvmumps.MValue v = s[--p]; mjvm.runtime.IrMachine.run(new mjvm.compiler.MFrontend().compileLine(v.asStr()), rt); }\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.JMP -> {
                    b.append("          pc = ").append(op.iarg).append("; break; }\n");
                }
                case Ir.JMP0 -> {
                    b.append("          if (!mjvm.runtime.MRuntime.truthy(s[--p])) pc = ").append(op.iarg)
                            .append("; else pc = ").append(next).append("; break; }\n");
                }
                case Ir.CALL -> {
                    if (op.name != null && op.name.startsWith("$")) {
                        b.append("          { int argc = ").append(op.iarg)
                                .append("; jvmumps.MValue[] argv = new jvmumps.MValue[argc]; for (int k = argc-1; k >= 0; k--) argv[k] = s[--p]; s[p++] = rt.callExtrinsic(")
                                .append(jstr(op.name.substring(1))).append(", argv); }\n");
                    } else {
                        b.append("          mjvm_call(").append(jstr(op.name)).append(", ").append(op.iarg).append(");\n");
                    }
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.CALL_PC -> {
                    b.append("          rets.push(").append(next).append(");\n");
                    b.append("          pc = ").append(op.iarg).append("; break; }\n");
                }
                case Ir.RET_TO -> {
                    b.append("          if (rets.isEmpty()) return;\n");
                    b.append("          pc = rets.pop(); break; }\n");
                }
                case Ir.OPEN -> {
                    b.append("          { jvmumps.MValue path = s[--p]; jvmumps.MValue num = s[--p]; rt.openDevice((int)num.asNum(), path.asStr()); }\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.CLOSE -> {
                    b.append("          rt.closeDevice((int)s[--p].asNum());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.USE -> {
                    b.append("          rt.useDevice((int)s[--p].asNum());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.READ -> {
                    b.append("          rt.setVar(").append(jstr(op.name == null ? "R" : op.name))
                            .append(", jvmumps.MValue.ofString(rt.readDevice()));\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.JOB -> {
                    b.append("          rt.job(s[--p].asStr());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.LOCK -> {
                    if (op.iarg > 0) b.append("          rt.lockAcquire(s[--p].asStr(), 50);\n");
                    else b.append("          rt.lockRelease(s[--p].asStr());\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.TSTART -> {
                    b.append("          rt.tstart();\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.TCOMMIT -> {
                    b.append("          rt.tcommit();\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.TROLLBACK -> {
                    b.append("          rt.trollback();\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.NEW -> {
                    b.append("          rt.newVar(").append(jstr(op.name)).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.KILL -> {
                    if ("@".equals(op.name)) b.append("          rt.killVar(s[--p].asStr());\n");
                    else b.append("          rt.killVar(").append(jstr(op.name)).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.MERGE -> {
                    b.append("          rt.mergeVar(").append(jstr(op.name))
                            .append(", ").append(jstr(op.ival == null ? "" : op.ival)).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.SET_EXTRACT -> {
                    String[] ft = (op.ival == null ? "0,1" : op.ival).split(",");
                    b.append("          { jvmumps.MValue v = s[--p]; rt.setExtract(").append(jstr(op.name))
                            .append(", ").append(ft[0]).append(", ").append(ft[1]).append(", v.asStr()); }\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.SET_PIECE -> {
                    String raw = op.ival == null ? ",0,1" : op.ival;
                    String[] parts = raw.split("\u0001", -1);
                    String del = parts.length > 0 ? parts[0] : "";
                    String pi = parts.length > 1 ? parts[1] : "0";
                    String pn = parts.length > 2 ? parts[2] : "1";
                    b.append("          { jvmumps.MValue v = s[--p]; rt.setPiece(").append(jstr(op.name))
                            .append(", ").append(jstr(del)).append(", ").append(pi).append(", ").append(pn)
                            .append(", v.asStr()); }\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
                case Ir.RET -> {
                    b.append("          if (!rets.isEmpty()) { pc = rets.pop(); break; }\n");
                    b.append("          return; }\n");
                }
                default -> {
                    b.append("          failBad(").append(op.op).append(");\n");
                    b.append("          pc = ").append(next).append("; break; }\n");
                }
            }
        }
        b.append("        default: return;\n");
        b.append("      }\n");
        b.append("    }\n");
        b.append("  }\n");
        b.append("  private void mjvm_call(String name, int argc) {\n");
        b.append("    mjvm.runtime.IndyLink indy = (r, n, a) -> r.call(n, a);\n");
        b.append("    indy.invoke(rt, name, argc);\n");
        b.append("  }\n");
        b.append("}\n");
        return b.toString();
    }

    private static String jstr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
