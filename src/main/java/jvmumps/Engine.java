package jvmumps;

/** Direct-mode engine: evaluate one M line, return stdout text. */
public interface Engine {
  /** Run one direct-mode line; return output written by WRITE. */
  String evalLine(String line);

  /** Reset locals/globals between corpus cases. */
  void reset();
}
