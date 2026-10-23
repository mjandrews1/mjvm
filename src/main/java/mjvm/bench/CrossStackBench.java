package mjvm.bench;

import java.util.List;
import jvmumps.CorpusCase;
import jvmumps.LineEngine;
import jvmumps.Engine;

/**
 * Cross-stack corpus timing (jvmumps#99): JavaM LineEngine vs wall clock
 * over the shared conformance corpus. mJVM native/class scored separately
 * (MjvmNativeScore / MjvmCorpusScore); RSM via tools/rsm_corpus_score.py.
 *
 * Usage: java mjvm.bench.CrossStackBench [conformance.json]
 */
public final class CrossStackBench {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "tests/conformance.json";
        List<CorpusCase> cases = CorpusCase.load(path);
        Engine eng = new LineEngine();
        int match = 0, miss = 0, err = 0;
        long t0 = System.nanoTime();
        for (CorpusCase c : cases) {
            eng.reset();
            try {
                String got = eng.evalLine(c.code);
                if (norm(got).equals(norm(c.expected))) match++;
                else miss++;
            } catch (RuntimeException e) {
                err++;
            }
        }
        double wallMs = (System.nanoTime() - t0) / 1e6;
        System.out.printf(
                "CROSS LineEngine cases=%d MATCH=%d MISS=%d ERR=%d wall_ms=%.2f%n",
                cases.size(), match, miss, err, wallMs);
        System.out.printf("CROSS LineEngine cases_per_s=%.1f%n",
                cases.size() / (wallMs / 1000.0));
    }

    static String norm(String s) {
        String t = s.replace("\r\n", "\n");
        while (t.endsWith("\n")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
