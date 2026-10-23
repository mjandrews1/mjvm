package jvmumps;

/** J10 child process for JOB — runs one M line then exits. */
public final class JobChild {
    public static void main(String[] args) {
        if (args.length < 1) {
            return;
        }
        try {
            new LineInterp().run(args[0]);
        } catch (Exception ignored) {
        }
    }
}
