package mjvm.ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Superinstruction fusion: compacts adjacent {@code EVAL_EXPR(expr) + STORE(name)} pairs into a
 * single {@link Ir#EVAL_STORE} op, removing one dispatch per SET in {@code IrMachine.runFrame}
 * (dispatch is ~40% of the hot loop per docs/PERF-REVIEW.md).
 *
 * <p>Safety rules:
 *
 * <ul>
 *   <li>A pair is fused only when its second half (the STORE) is not a jump target. Jumping to the
 *       first half is unaffected: the fused op executes both halves, exactly what fall-through from
 *       the first half did.
 *   <li>Every iarg-targeted op (JMP, JMP0, JMP_IF_TRUE, CALL_PC) is remapped through the compaction
 *       map. A target landing on a removed op cannot happen (guarded above) — if it ever does, we
 *       throw rather than skip.
 *   <li>Extra protected indices (routine line starts, used as runExtrinsic entry points) are never
 *       removed.
 * </ul>
 *
 * <p>Callers must invoke this only on a fully patched program — all jump iargs absolute in the same
 * index space as the list.
 */
public final class IrFusion {
  private IrFusion() {}

  /** Test/emergency kill switch. Default: fusion on. */
  public static volatile boolean FUSE_ENABLED = true;

  public static final class Result {
    public final List<Ir> prog;

    /** old index → new index in {@link #prog}; -1 for a removed (fused-away) op. */
    public final int[] oldToNew;

    Result(List<Ir> prog, int[] oldToNew) {
      this.prog = prog;
      this.oldToNew = oldToNew;
    }
  }

  public static Result fuse(List<Ir> prog) {
    return fuse(prog, null);
  }

  /**
   * @param protectedExtra indices that must never be removed (e.g. routine line starts used as
   *     extrinsic entry points), in addition to all jump targets
   */
  public static Result fuse(List<Ir> prog, int[] protectedExtra) {
    int n = prog.size();
    Set<Integer> prot = new HashSet<>();
    for (Ir op : prog) {
      if (isTargeted(op) && op.iarg >= 0 && op.iarg < n) {
        prot.add(op.iarg);
      }
    }
    if (protectedExtra != null) {
      for (int t : protectedExtra) {
        if (t >= 0 && t < n) prot.add(t);
      }
    }

    int[] oldToNew = new int[n];
    List<Ir> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      Ir a = prog.get(i);
      Ir b = i + 1 < n ? prog.get(i + 1) : null;
      if (b != null && a.op == Ir.EVAL_EXPR && b.op == Ir.STORE && !prot.contains(i + 1)) {
        Ir fused = new Ir(Ir.EVAL_STORE, b.name, a.ival, 0, 0);
        fused.slot = b.slot; // carry over any resolved locals slot
        fused.node = a.node; // carry over the cached AST node, if any
        fused.nodeFailed = a.nodeFailed;
        out.add(fused);
        oldToNew[i] = out.size() - 1;
        oldToNew[i + 1] = -1;
        i++; // consume the STORE half
      } else {
        out.add(a);
        oldToNew[i] = out.size() - 1;
      }
    }

    for (int i = 0; i < out.size(); i++) {
      Ir op = out.get(i);
      if (isTargeted(op) && op.iarg >= 0 && op.iarg < n) {
        int mapped = oldToNew[op.iarg];
        if (mapped < 0) {
          throw new IllegalStateException(
              "IrFusion: jump target " + op.iarg + " landed on a fused-away op " + op);
        }
        if (mapped != op.iarg) {
          out.set(i, new Ir(op.op, op.name, op.ival, op.dval, mapped));
        }
      }
    }
    return new Result(out, oldToNew);
  }

  private static boolean isTargeted(Ir op) {
    return op.op == Ir.JMP || op.op == Ir.JMP0 || op.op == Ir.JMP_IF_TRUE || op.op == Ir.CALL_PC;
  }
}
