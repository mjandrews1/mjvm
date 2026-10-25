package jvmumps;

/**
 * Direct-mode engine over {@link LineInterp} (J2 core commands).
 *
 * <p>Stateless facade for corpus runners and smoke tests: each {@link #evalLine} runs one physical
 * line against a fresh-or-persistent interpreter; {@link #reset} starts a clean session.
 */
@SuppressWarnings("deprecation")
public final class LineEngine implements Engine {
  private LineInterp interp = new LineInterp();

  /** Evaluate one direct-mode line; returns concatenated WRITE text. */
  @Override
  public String evalLine(String line) {
    return interp.run(line);
  }

  /** Drop interpreter state (locals, $TEST, devices). */
  @Override
  public void reset() {
    interp = new LineInterp();
  }
}
