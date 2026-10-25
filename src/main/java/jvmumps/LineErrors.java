package jvmumps;

/**
 * Direct-mode error/diagnostic reporting (deep split, #58). RSM: a failed line's result is the
 * error text + newline (C02).
 */
final class LineErrors {
  private LineErrors() {}

  static String report(MError e) {
    return e.getMessage() + "\n";
  }

  static void rethrow(MError e) {
    throw e;
  }
}
