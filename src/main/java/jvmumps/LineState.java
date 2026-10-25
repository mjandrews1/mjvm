package jvmumps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routine / stack / tag state for {@link LineInterp} (deep split, #58). Holds J13 tagged-routine
 * frame state that DO/GOTO/QUIT manipulate.
 */
final class LineState {
  static final class RtLine {
    final String tag;
    final List<String> formals;
    final String body;

    RtLine(String tag, List<String> formals, String body) {
      this.tag = tag;
      this.formals = formals;
      this.body = body;
    }
  }

  static final class Frame {
    final int retLine;
    final String retRest;
    final boolean extrinsic;
    final List<String> savedFormals;

    /** formal name (upper) → caller actual name, for pass-by-reference `.var` args. */
    final Map<String, String> byref = new LinkedHashMap<>();

    Frame(int retLine, String retRest, boolean extrinsic, List<String> savedFormals) {
      this.retLine = retLine;
      this.retRest = retRest;
      this.extrinsic = extrinsic;
      this.savedFormals = savedFormals;
    }
  }

  List<RtLine> prog = null;
  int pc;
  String pendingRest;
  final Deque<Frame> doStack = new ArrayDeque<>();
  final Map<String, Integer> tags = new LinkedHashMap<>();
  MValue extrinsicValue = MValue.EMPTY;
  boolean routineMode = false;
  boolean trapFiring = false;

  void clearRun() {
    prog = null;
    pc = 0;
    pendingRest = null;
    doStack.clear();
    tags.clear();
    extrinsicValue = MValue.EMPTY;
    routineMode = false;
  }
}
