package jvmumps;

/** Direct-mode exec loop + $ETRAP/$ZTRAP wrapper for {@link LineInterp} (deep split, #58). */
@SuppressWarnings("deprecation")
final class LineExec {
  private LineExec() {}

  static void exec(LineInterp fe, String line) {
    // Physical newlines separate direct-mode commands
    for (String part : line.split("\n", -1)) {
      Cursor c = new Cursor(part);
      while (!c.atEnd() && !fe.quit) {
        int before = c.i;
        if (!execOneTrapped(fe, c) && !c.atEnd() && c.i == before) {
          c.next();
        }
      }
    }
  }

  static boolean execOneTrapped(LineInterp fe, Cursor c) {
    try {
      int before = c.i;
      LineDispatch.execOne(fe, c);
      return true;
    } catch (MError e) {
      String trap = !fe.eval.etrap.isEmpty() ? fe.eval.etrap : fe.eval.ztrap;
      if (trap.isEmpty() || fe.state.trapFiring) {
        throw e;
      }
      // abandon the rest of the failed command
      try {
        c.skipCmd();
      } catch (RuntimeException ignored) {
        c.skipToEnd();
      }
      fe.state.trapFiring = true;
      try {
        Cursor tc = new Cursor(trap);
        while (!tc.atEnd() && !fe.quit) {
          int b = tc.i;
          try {
            LineDispatch.execOne(fe, tc);
          } catch (MError e2) {
            if (tc.i == b) {
              tc.next();
            }
          }
          if (tc.i == b && !tc.atEnd()) {
            tc.next();
          }
        }
      } finally {
        fe.state.trapFiring = false;
      }
      return false;
    }
  }
}
