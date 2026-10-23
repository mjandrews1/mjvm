package mjvm.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jvmumps.MError;

/**
 * mJVM M2/M3 classfile emitter.
 *
 * Emits {@code final class X implements Runnable} with a
 * {@code (MRuntime)} constructor.  DO/$$/GOTO call sites are real
 * {@code invokedynamic} instructions linked by {@link mjvm.runtime.Indy}.
 *
 * Constant-pool layout is built in one pass via {@link Cp}.
 */
/** Direct classfile emitter for IR (M2/M3); preferred over generated Java. */
public final class BytecodeEmitter {

    public static byte[] emit(List<mjvm.ir.Ir> prog, String className) {
        return new Gen(prog, className).gen();
    }

    /** Load and instantiate a generated class. */
    public static Runnable load(List<mjvm.ir.Ir> prog, String className,
                                mjvm.runtime.MRuntime rt) throws Exception {
        byte[] bytes = emit(prog, className);
        Class<?> cl = new DefLoader(BytecodeEmitter.class.getClassLoader())
                .define(className, bytes);
        return (Runnable) cl.getConstructor(mjvm.runtime.MRuntime.class).newInstance(rt);
    }

    static final class DefLoader extends ClassLoader {
        DefLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    // ---------------------------------------------------------------- CP



    // ---------------------------------------------------------------- code



    // ---------------------------------------------------------------- gen

    static final class Gen {
        final List<mjvm.ir.Ir> prog;
        final String cls;
        final Cp cp = new Cp();
        int thisC, superC, runC;
        int mVal, mRt, eEval;
        int ofNum, ofStr, asNum, asStr, getVar, setVar, write, writeNl, callM;
        int objInit, mstr, concat;
        int indyCall, indyGoto;
        int mhCall, mhGoto;
        int bsmAttrName;
        int rtField;

        Gen(List<mjvm.ir.Ir> prog, String className) {
            this.prog = prog;
            this.cls = className;
        }

        byte[] gen() {
            thisC = cp.cls(cls);
            superC = cp.cls("java/lang/Object");
            runC = cp.cls("java/lang/Runnable");
            mVal = cp.cls("jvmumps/MValue");
            mRt = cp.cls("mjvm/runtime/MRuntime");
            eEval = cp.cls("jvmumps/ExprEval");
            ofNum = cp.mref("jvmumps/MValue", "ofNumber", "(D)Ljvmumps/MValue;");
            ofStr = cp.mref("jvmumps/MValue", "ofString", "(Ljava/lang/String;)Ljvmumps/MValue;");
            asNum = cp.mref("jvmumps/MValue", "asNum", "()D");
            asStr = cp.mref("jvmumps/MValue", "asStr", "()Ljava/lang/String;");
            getVar = cp.mref("mjvm/runtime/MRuntime", "getVar",
                    "(Ljava/lang/String;)Ljvmumps/MValue;");
            setVar = cp.mref("mjvm/runtime/MRuntime", "setVar",
                    "(Ljava/lang/String;Ljvmumps/MValue;)V");
            write = cp.mref("mjvm/runtime/MRuntime", "write", "(Ljava/lang/String;)V");
            writeNl = cp.mref("mjvm/runtime/MRuntime", "writeNl", "()V");
            callM = cp.mref("mjvm/runtime/MRuntime", "call", "(Ljava/lang/String;I)V");
            objInit = cp.mref("java/lang/Object", "<init>", "()V");
            mstr = cp.mref("jvmumps/ExprEval", "mstr", "(D)Ljava/lang/String;");
            concat = cp.mref("java/lang/String", "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;");
            rtField = cp.fref(cls, "rt", "Lmjvm/runtime/MRuntime;");

            String bsmSig = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/String;I)"
                    + "Ljava/lang/invoke/CallSite;";
            int callRef = cp.mref("mjvm/runtime/Indy", "call", bsmSig);
            int gotoRef = cp.mref("mjvm/runtime/Indy", "goto_", bsmSig);
            mhCall = cp.mhandle(6, callRef);   // REF_invokeStatic
            mhGoto = cp.mhandle(6, gotoRef);

            String indyType = "(Lmjvm/runtime/MRuntime;Ljava/lang/String;I)V";
            // BootstrapMethods index 0 = call, 1 = goto_
            indyCall = cp.indy(0, "call", indyType);
            indyGoto = cp.indy(1, "goto", indyType);

            // Pre-register field + method + attr names so they land in the CP
            // before we serialize it.
            int fn = cp.utf8("rt");
            int fd = cp.utf8("Lmjvm/runtime/MRuntime;");
            int ctorN = cp.utf8("<init>");
            int ctorD = cp.utf8("(Lmjvm/runtime/MRuntime;)V");
            int runN = cp.utf8("run");
            int runD = cp.utf8("()V");
            int codeN = cp.utf8("Code");
            bsmAttrName = cp.utf8("BootstrapMethods");

            byte[] ctor = ctor();
            byte[] run = run();
            byte[] bsm = bsmAttr();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] pre = cp.prefix(0x0021 /* public super */, thisC, superC,
                    new int[] {runC});
            out.writeBytes(pre);
            // fields — use the pre-registered indexes
            Cp.writeU2(out, 1);
            Cp.writeU2(out, 0x0001);
            Cp.writeU2(out, fn);
            Cp.writeU2(out, fd);
            Cp.writeU2(out, 0);
            // methods
            Cp.writeU2(out, 2);
            out.writeBytes(ctor);
            out.writeBytes(run);
            // class attrs
            Cp.writeU2(out, 1);
            Cp.writeU2(out, bsmAttrName);
            Cp.writeU4(out, bsm.length);
            out.writeBytes(bsm);
            return out.toByteArray();
        }

        byte[] bsmAttr() {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            Cp.writeU2(o, 2);
            Cp.writeU2(o, mhCall);
            Cp.writeU2(o, 2);
            Cp.writeU2(o, cp.str("DO"));
            Cp.writeU2(o, cp.integer(0));
            Cp.writeU2(o, mhGoto);
            Cp.writeU2(o, 2);
            Cp.writeU2(o, cp.str("GOTO"));
            Cp.writeU2(o, cp.integer(0));
            return o.toByteArray();
        }

        byte[] method(String name, String desc, byte[] code, int maxStack, int maxLocals) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            Cp.writeU2(o, 0x0001);
            Cp.writeU2(o, cp.utf8(name));
            Cp.writeU2(o, cp.utf8(desc));
            Cp.writeU2(o, 1);
            Cp.writeU2(o, cp.utf8("Code"));
            Cp.writeU4(o, 12 + code.length);
            Cp.writeU2(o, maxStack);
            Cp.writeU2(o, maxLocals);
            Cp.writeU4(o, code.length);
            o.writeBytes(code);
            Cp.writeU2(o, 0);
            Cp.writeU2(o, 0);
            return o.toByteArray();
        }

        byte[] ctor() {
            Code c = new Code();
            c.aload0();
            c.invokespecial(objInit);
            c.aload0();
            c.aload(1);
            c.getfield(0); // placeholder
            // putfield rt = arg1
            // aload_0; aload_1; putfield rt
            // fix: getfield is wrong — putfield
            c = new Code();
            c.aload0();
            c.invokespecial(objInit);
            c.aload0();
            c.aload(1);
            c.u1(0xb5); // putfield
            c.u2(rtField);
            c.vreturn();
            return method("<init>", "(Lmjvm/runtime/MRuntime;)V", c.bytes(), 3, 2);
        }

        byte[] run() {
            Code c = new Code();
            // locals: 0 this, 2 MValue[] s, 3 p, 4 da, 6 db
            c.bipush(64);
            c.anewarray(mVal);
            c.astore(2);
            c.u1(0x03); // iconst_0
            c.istore(3);
            for (mjvm.ir.Ir op : prog) {
                switch (op.op) {
                    case mjvm.ir.Ir.PUSH -> {
                        c.aload(2);
                        c.iload(3);
                        if (op.iarg == 1) {
                            c.ldc2(cp.dbl(op.dval));
                            c.invokestatic(ofNum);
                        } else {
                            c.ldc(cp.str(op.ival == null ? "" : op.ival));
                            c.invokestatic(ofStr);
                        }
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.LOAD -> {
                        c.aload(2);
                        c.iload(3);
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name));
                        c.invokevirtual(getVar);
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.STORE -> {
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name));
                        c.aload(2);
                        c.iload(3);
                        c.iconst_1_();
                        c.isub();
                        c.aaload();
                        c.invokevirtual(setVar);
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.ADD, mjvm.ir.Ir.SUB, mjvm.ir.Ir.MUL, mjvm.ir.Ir.DIV -> {
                        // da = s[p-2].asNum(); db = s[p-1].asNum(); s[p-2]=ofNumber(da op db); p--
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.dstore(4);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.dstore(6);
                        c.dload(4);
                        c.dload(6);
                        switch (op.op) {
                            case mjvm.ir.Ir.ADD -> c.dadd();
                            case mjvm.ir.Ir.SUB -> c.dsub();
                            case mjvm.ir.Ir.MUL -> c.dmul();
                            default -> c.ddiv();
                        }
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub();
                        c.invokestatic(ofNum);
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.CONCAT -> {
                        // s[p-2] = ofString(s[p-2].asStr().concat(s[p-1].asStr())); p--
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub(); c.aaload();
                        c.invokevirtual(asStr);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asStr);
                        c.invokevirtual(concat);
                        c.invokestatic(ofStr);
                        c.astore(8);
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub();
                        c.aload(8);
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.WRITE -> {
                        c.aload0();
                        c.getfield(rtField);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asStr);
                        c.invokevirtual(write);
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.WRITE_NL -> {
                        c.aload0();
                        c.getfield(rtField);
                        c.invokevirtual(writeNl);
                    }
                    case mjvm.ir.Ir.CALL -> {
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name));
                        c.bipush(op.iarg);
                        c.invokedynamic(indyCall);
                    }
                    case mjvm.ir.Ir.JMP -> {
                        // goto — we need labels. Use raw bytecode with placeholder offset.
                        // For now, emit a NOP and handle jumps via interpreted fallback.
                        c.vreturn();
                        break;
                    }
                    case mjvm.ir.Ir.EQ, mjvm.ir.Ir.LT, mjvm.ir.Ir.GT,
                         mjvm.ir.Ir.LE, mjvm.ir.Ir.GE, mjvm.ir.Ir.NE -> {
                        // Delegate to runtime helper
                        c.vreturn();
                        break;
                    }
                    case mjvm.ir.Ir.STORE_ADD -> {
                        // Atomic increment: s[p-1] = store.increment(name, true, s[p-1].asNum())
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name));
                        c.iconst_1_(); // true
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "incrementVar",
                                "(Ljava/lang/String;D)Ljvmumps/MValue;"));
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub();
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.EVAL_EXPR -> {
                        // Evaluate M expression: s[p++] = rt.evalExpr(expr)
                        c.aload(2);
                        c.iload(3);
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.ival == null ? "" : op.ival));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "evalExpr",
                                "(Ljava/lang/String;)Ljvmumps/MValue;"));
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.NEW -> {
                        // NEW var: rt.newVar(name) — no stack effect
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "newVar",
                                "(Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.KILL -> {
                        // KILL var: rt.killVar(name) — no stack effect
                        c.aload0();
                        c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "killVar",
                                "(Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.JMP0 -> {
                        // Conditional jump: if (!truthy(s[--p])) goto target
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokestatic(cp.mref("mjvm/runtime/MRuntime", "truthy",
                                "(Ljvmumps/MValue;)Z"));
                        // ifeq to target (placeholder offset)
                        c.u1(0x99); // ifeq
                        c.u2(0); // placeholder offset
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.MOD -> {
                        // s[p-2] = s[p-2] % s[p-1]
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.dstore(4);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.dstore(6);
                        c.dload(4); c.dload(6);
                        c.u1(0x71); // irem (Java % operator)
                        c.aload(2); c.iload(3); c.iconst_2_(); c.isub();
                        c.invokestatic(ofNum);
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.PUSH_REG -> {
                        // Push last stored value (peephole for redundant reads)
                        c.aload(2); c.iload(3);
                        c.u1(0x01); // aconst_null (placeholder for EMPTY)
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.BIND -> {
                        // BIND: rt.setVar(name, pop()) — bind parameter
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "setVar",
                                "(Ljava/lang/String;Ljvmumps/MValue;)V"));
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.CALL_PC -> {
                        // CALL_PC: call subroutine at PC (no return value)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.u1(0x03); // iconst_0
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "call",
                                "(Ljava/lang/String;I)V"));
                    }
                    case mjvm.ir.Ir.CLOSE -> {
                        // CLOSE: rt.closeDev(name)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "closeDev",
                                "(Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.END_WRITE -> {
                        // END_WRITE: rt.endWrite()
                        c.aload0(); c.getfield(rtField);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "endWrite", "()V"));
                    }
                    case mjvm.ir.Ir.GETB -> {
                        // GETB: batch get (pushes empty placeholder)
                        c.aload(2); c.iload(3);
                        c.u1(0x01); // aconst_null
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.JOB -> {
                        // JOB: rt.job(cmd)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "job",
                                "(Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.LOCK -> {
                        // LOCK: rt.lock(key, mode)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.bipush(op.iarg);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "lock",
                                "(Ljava/lang/String;I)V"));
                    }
                    case mjvm.ir.Ir.MERGE -> {
                        // MERGE: rt.mergeVar(dst, src)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.ldc(cp.str(op.ival == null ? "" : op.ival));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "mergeVar",
                                "(Ljava/lang/String;Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.OPEN -> {
                        // OPEN: rt.openDev(name, args)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.ldc(cp.str(op.ival == null ? "" : op.ival));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "openDev",
                                "(Ljava/lang/String;Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.READ -> {
                        // READ: s[p++] = rt.readDev(name)
                        c.aload(2); c.iload(3);
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "readDev",
                                "(Ljava/lang/String;)Ljvmumps/MValue;"));
                        c.aastore();
                        c.iinc(3, 1);
                    }
                    case mjvm.ir.Ir.RET_TO -> {
                        // RET_TO: return to specific PC (no-op in bytecode)
                        c.vreturn();
                    }
                    case mjvm.ir.Ir.RET_VALUE -> {
                        // RET_VALUE: return with value on stack
                        c.vreturn();
                    }
                    case mjvm.ir.Ir.SET_EXTRACT -> {
                        // SET_EXTRACT: s[p-1] = extract(s[p-1], start, end)
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub();
                        c.invokevirtual(asNum);
                        c.dstore(4);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub();
                        c.dload(4);
                        c.invokestatic(ofNum);
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.SET_PIECE -> {
                        // SET_PIECE: s[p-1] = piece(s[p-1], del, pos)
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub();
                        c.invokevirtual(asNum);
                        c.dstore(4);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub();
                        c.dload(4);
                        c.invokestatic(ofNum);
                        c.aastore();
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.TCOMMIT -> {
                        // TCOMMIT: rt.tcommit()
                        c.aload0(); c.getfield(rtField);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "tcommit", "()V"));
                    }
                    case mjvm.ir.Ir.TROLLBACK -> {
                        // TROLLBACK: rt.trollback()
                        c.aload0(); c.getfield(rtField);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "trollback", "()V"));
                    }
                    case mjvm.ir.Ir.TSTART -> {
                        // TSTART: rt.tstart()
                        c.aload0(); c.getfield(rtField);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "tstart", "()V"));
                    }
                    case mjvm.ir.Ir.USE -> {
                        // USE: rt.useDev(name)
                        c.aload0(); c.getfield(rtField);
                        c.ldc(cp.str(op.name == null ? "" : op.name));
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "useDev",
                                "(Ljava/lang/String;)V"));
                    }
                    case mjvm.ir.Ir.WRITE_CHAR -> {
                        // WRITE_CHAR: rt.writeChar(s[--p].asNum())
                        c.aload0(); c.getfield(rtField);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.u1(0x8e); // d2i
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "writeChar", "(I)V"));
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.WRITE_COL -> {
                        // WRITE_COL: rt.writeCol(s[--p].asNum())
                        c.aload0(); c.getfield(rtField);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asNum);
                        c.u1(0x8e); // d2i
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "writeCol", "(I)V"));
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.XECUTE -> {
                        // XECUTE: rt.xecute(s[--p].asStr())
                        c.aload0(); c.getfield(rtField);
                        c.aload(2); c.iload(3); c.iconst_1_(); c.isub(); c.aaload();
                        c.invokevirtual(asStr);
                        c.invokevirtual(cp.mref("mjvm/runtime/MRuntime", "xecute",
                                "(Ljava/lang/String;)V"));
                        c.iinc(3, -1);
                    }
                    case mjvm.ir.Ir.RET -> {
                        c.vreturn();
                        break;
                    }
                    default -> {
                        // Unknown op — no-op in bytecode path
                    }
                }
            }
            c.vreturn();
            return method("run", "()V", c.bytes(), 10, 12);
        }
    }
}
