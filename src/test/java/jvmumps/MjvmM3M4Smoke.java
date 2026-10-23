package jvmumps;

import java.nio.file.Path;
import java.util.List;
import mjvm.compiler.ClassEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/**
 * mJVM M3 (invokedynamic CALL/GOTO) + M4 (store oracle) smoke.
 *
 * M3: DO/GOTO go through IndyLink lambda (LambdaMetafactory = INVOKEDYNAMIC).
 * M4: MRuntime+IrMachine store get/set/kill/data/order match JvMumps/ISO.
 */
public final class MjvmM3M4Smoke {
    static int n = 0;

    static void chk(boolean ok, String tag) {
        if (!ok) throw new IllegalStateException(tag);
        n++;
    }

    static void chkEq(String got, String want, String tag) {
        if (!want.equals(got)) {
            throw new IllegalStateException(tag + " got=" + q(got) + " want=" + q(want));
        }
        n++;
    }

    static String q(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }

    static MRuntime compileRun(String code, boolean useClass, MRuntime.Resolver res)
            throws Exception {
        MRuntime rt = new MRuntime(new MStore());
        if (res != null) rt.setResolver(res);
        List<Ir> prog = new MFrontend().compileLine(code);
        if (useClass) {
            String cn = "Mj3_" + (n++);
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-m3");
            ClassEmitter.emit(prog, cn, dir);
            Class<?> cl = Class.forName(cn, true,
                    new java.net.URLClassLoader(new java.net.URL[] { dir.toUri().toURL() },
                            MjvmM3M4Smoke.class.getClassLoader()));
            Runnable r = (Runnable) cl.getConstructor(MRuntime.class).newInstance(rt);
            r.run();
        } else {
            IrMachine.run(prog, rt);
        }
        return rt;
    }

    public static void main(String[] a) throws Exception {
        // --- M3: DO via invokedynamic (class) ---
        for (boolean useClass : new boolean[] { false, true }) {
            MRuntime.Resolver res = (name, argc, rt) -> {
                rt.write("do:" + name);
            };
            MRuntime rt = compileRun("D FOO", useClass, res);
            chkEq(rt.output(), "do:FOO", "M3-do" + (useClass ? "-class" : "-ir"));

            rt = compileRun("G BAR", useClass, res);
            chkEq(rt.output(), "do:BAR", "M3-goto" + (useClass ? "-class" : "-ir"));

            rt = compileRun("D A", useClass, res);
            // still indy even when resolver writes nothing extra
            chkEq(rt.output(), "do:A", "M3-do2" + (useClass ? "-class" : "-ir"));
        }

        // --- M4: store oracle (MRuntime vs ISO/JvMumps contract) ---
        MStore st = new MStore();
        MRuntime rt = new MRuntime(st);
        List<Ir> p = new MFrontend().compileLine("S X=1 S Y=2");
        IrMachine.run(p, rt);
        chkEq(rt.getVar("X").asStr(), "1", "M4-set-x");
        chkEq(rt.getVar("Y").asStr(), "2", "M4-set-y");
        chk(st.data(java.util.List.of("X"), false) == 1, "M4-data-x");
        // kill via store SPI
        st.kill(java.util.List.of("X"), false);
        chk(st.data(java.util.List.of("X"), false) == 0, "M4-kill-x");
        // order
        st.set(java.util.List.of("A", "1"), false, MValue.ofString("a1"));
        st.set(java.util.List.of("A", "2"), false, MValue.ofString("a2"));
        String o = st.order(java.util.List.of("A", ""), false, 1);
        chkEq(o, "1", "M4-order-1");
        o = st.order(java.util.List.of("A", "1"), false, 1);
        chkEq(o, "2", "M4-order-2");
        // globals
        rt.setVar("^G", MValue.ofString("g"));
        chkEq(st.get(java.util.List.of("^G"), true).asStr(), "g", "M4-global");
        // arith matches LTR M
        rt = new MRuntime(new MStore());
        IrMachine.run(new MFrontend().compileLine("W 1+2*3,!" ), rt);
        chkEq(rt.output(), "9\n", "M4-ltr");

        System.out.println("MjvmM3M4Smoke OK cases=" + n);
    }
}
