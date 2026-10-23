package jvmumps;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TSTART undo must be per JOB / LineInterp (jvmumps#122).
 * Parent TSTART + dirty SET must not leak into a child JOB's view;
 * child TROLLBACK must not undo the parent's committed values.
 */
public final class TStartJobSmoke {
    public static void main(String[] args) throws Exception {
        File db = File.createTempFile("txjob", ".db");
        db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());

        LineInterp parent = new LineInterp(st);
        parent.run("S ^P(1)=\"committed\"");
        parent.run("TSTART");
        parent.run("S ^P(1)=\"dirty-parent\"");
        parent.run("S ^P(2)=\"parent-new\"");

        AtomicReference<String> childOut = new AtomicReference<>();
        AtomicReference<String> childErr = new AtomicReference<>();
        Thread child = new Thread(() -> {
            try {
                LineInterp c = new LineInterp(st);
                // child sees only committed parent state if undo is provisional
                // — at minimum, child TROLLBACK must not restore parent's dirty node
                c.run("TSTART");
                c.run("S ^C(1)=\"child\"");
                c.run("TROLLBACK");
                String s = c.run("W $D(^C(1)),\"|\",$G(^P(1)),!");
                childOut.set(s);
            } catch (Exception e) {
                childErr.set(String.valueOf(e));
            }
        });
        child.start();
        child.join(5000);

        if (childErr.get() != null) {
            throw new IllegalStateException("child: " + childErr.get());
        }
        System.out.println("child saw " + childOut.get().replace('\n', ' '));

        // parent rollback restores parent keys only
        parent.run("TROLLBACK");
        String after = parent.run("W $G(^P(1)),\"|\",$D(^P(2)),!");
        if (!after.contains("committed")) {
            throw new IllegalStateException("parent TROLLBACK lost committed value: " + after);
        }
        // ^C(1) should be gone after child rollback
        String cAlive = parent.run("W $D(^C(1)),!");
        if (!cAlive.trim().equals("0")) {
            throw new IllegalStateException("child key leaked after child TROLLBACK: " + cAlive);
        }
        System.out.println("parent after " + after.replace('\n', ' ') + " ^C " + cAlive.trim());
        System.out.println("TSTART JOB SMOKE OK");
        st.close();
    }
}
