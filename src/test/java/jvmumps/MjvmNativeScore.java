package jvmumps;

import java.util.List;

/** Native mJVM path (MFrontend+IrMachine) without LineInterp fallback. */
public final class MjvmNativeScore {
    public static void main(String[] args) throws Exception {
        String path = CorpusCase.corpusPath(args);
        String prefix = args.length > 1 ? args[1] : "*";
        List<CorpusCase> cases = CorpusCase.load(path);
        int match = 0, miss = 0, err = 0;
        for (CorpusCase c : cases) {
            boolean use = "*".equals(prefix) || prefix.isEmpty()
                || c.name.equals(prefix) || c.name.startsWith(prefix);
            if (!use) continue;
            try {
                mjvm.runtime.MRuntime rt = new mjvm.runtime.MRuntime(new MStore());
                rt.setExtrinsicHandler((fn, argv) -> {
                    for (String raw : c.code.split("\n", -1)) {
                        String s = raw.trim();
                        int p = s.indexOf('(');
                        if (p <= 0) continue;
                        String nm = s.substring(0, p).trim();
                        if (!nm.equalsIgnoreCase(fn)) continue;
                        int q = s.indexOf(')', p);
                        if (q < 0) continue;
                        String[] fs = s.substring(p + 1, q).split(",");
                        for (int k = 0; k < fs.length && k < argv.length; k++) {
                            rt.setVar(fs[k].trim(), argv[k]);
                        }
                        String body = s.substring(q + 1).trim();
                        if (body.startsWith("Q") || body.startsWith("QUIT")) {
                            body = body.replaceFirst("^(QUIT|Q)\\s*", "");
                        }
                        return rt.evalExpr(body);
                    }
                    throw new jvmumps.MError("Extrinsic $$" + fn + " not available");
                });
                var prog = new mjvm.compiler.MFrontend().compileProgram(c.code);
                mjvm.runtime.IrMachine.run(prog, rt);
                String got = rt.output();
                String want = c.expected == null ? "" : c.expected;
                while (got.endsWith("\n")) got = got.substring(0, got.length() - 1);
                while (want.endsWith("\n")) want = want.substring(0, want.length() - 1);
                if (got.equals(want)) match++;
                else {
                    miss++;
                }
            } catch (jvmumps.MError e) {
                // Direct-mode convention: error text is output
                String got = e.getMessage() + "\n";
                String want = c.expected == null ? "" : c.expected;
                while (got.endsWith("\n")) got = got.substring(0, got.length() - 1);
                while (want.endsWith("\n")) want = want.substring(0, want.length() - 1);
                if (got.equals(want)) match++;
                else miss++;
            } catch (RuntimeException e) {
                err++;
            }
        }
        System.out.printf("MJVM-NATIVE MATCH=%d MISS=%d ERR=%d%n", match, miss, err);
    }
    private MjvmNativeScore() {}
}
