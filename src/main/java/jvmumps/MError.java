package jvmumps;

/** M runtime error (ISO / RSM error channel). */
public final class MError extends RuntimeException {
    public MError(String message) {
        super(message);
    }
}
