package jvmumps;
import java.io.File;

/**
 * jvmumps#2 — JOB / TX / LOCK semantics probes (ISO / RSM rules).
 * Wants are ISO-derived, not engine-derived.
 *
 * Rules:
 *  TX1  TSTART opens a transaction ($TLEVEL++). SETs after TSTART are provisional.
 *  TX2  TCOMMIT makes provisional SETs permanent ($TLEVEL--).
 *  TX3  TROLLBACK undoes SETs since TSTART ($TLEVEL--).
 *  TX4  Nested TSTART allowed; TROLLBACK undoes the current level's SETs.
 *  TX5  JOB is asynchronous — parent continues; parent $IO is not the child's.
 *  LK1  LOCK +X is re-entrant in the same process (second +X succeeds).
 *  LK2  LOCK -X,Y releases X and Y (same as L -X L -Y).
 *  LK3  LOCK +X:n timeout is an argument of +X (acquire wait), not a separate command.
 *  LK4  Bare LOCK -X releases; subsequent +X succeeds.
 */
public final class JTxLockSemSmoke {
    static int n = 0;
    static void chk(LineInterp m, String code, String want, String tag, String rule) {
        String got = m.run(code);
        if (!want.equals(got))
            throw new IllegalStateException(tag+" ["+rule+"] "+code+" got="+q(got)+" want="+q(want));
        n++;
        System.out.println("  OK "+tag+"  "+rule);
    }
    static String q(String s) { return "\""+s.replace("\n","\\n")+"\""; }

    public static void main(String[] a) throws Exception {
        File db = File.createTempFile("jtx2", ".db"); db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        LineInterp m = new LineInterp(st);

        // TX3: rollback undoes SET
        chk(m, "S A=0 TSTART S A=1 TROLLBACK W A,!", "0\n", "TX3a", "TX3");
        // TX2: commit keeps SET
        chk(m, "S B=0 TSTART S B=1 TCOMMIT W B,!", "1\n", "TX2a", "TX2");
        // TX1: $TLEVEL via SET-undo only (no $TLEVEL intrinsic yet) — nested
        chk(m, "S C=0 TSTART S C=1 TSTART S C=2 TROLLBACK W C,!", "1\n", "TX4a", "TX4");
        chk(m, "S D=0 TSTART S D=1 TSTART S D=2 TROLLBACK TROLLBACK W D,!", "0\n", "TX4b", "TX4");
        // TX3 after JOB on same line (J27 shape)
        chk(m, "S X=0 L +A TSTART S X=1 J \"S Y=1\" TROLLBACK L -A W X,!", "0\n", "TX5a", "TX3+TX5");
        // TX5: parent W only — child job must not add to parent output
        chk(m, "J \"S ^CH=1\" W \"parent\",!", "parent\n", "TX5b", "TX5");
        // TX2 + JOB after commit (J29 shape)
        chk(m, "S Z=0 TSTART S Z=1 TCOMMIT J \"S Z=2\" W Z,!", "1\n", "TX5c", "TX2+TX5");

        // LK1 reenter
        chk(m, "L +X:0 W \"r1\",! L +X:0 W \"r2\",! L -X", "r1\nr2\n", "LK1a", "LK1");
        // LK2 comma release == separate release (parent W is the only side channel)
        chk(m, "L +X,Y L -X,Y W \"off1\",!", "off1\n", "LK2a", "LK2");
        chk(m, "L +X,Y L -X L -Y W \"off2\",!", "off2\n", "LK2b", "LK2");
        // LK3 timeout arg on +X (must not break the next command)
        chk(m, "L +Q:25 W \"t\",! L -Q", "t\n", "LK3a", "LK3");
        // LK4 release then reacquire
        chk(m, "L +Z L -Z L +Z W \"re\",! L -Z", "re\n", "LK4a", "LK4");
        // parent lock held through TX then released (J26 shape) — W still fires
        chk(m, "L +J1 J \"S ^J=1\" TSTART S H=1 TCOMMIT L -J1 W \"jhold\",!", "jhold\n", "LK+TX", "TX2+LK+TX5");

        System.out.println("JTxLockSemSmoke OK cases="+n);
        st.close();
    }
}
