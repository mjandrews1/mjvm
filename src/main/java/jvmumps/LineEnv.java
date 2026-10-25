package jvmumps;

import java.util.List;

/**
 * {@link ExprEval.Env} bridge for {@link LineInterp} (deep split, #58). Maps names to store paths
 * via {@link LineRefs}; extrinsics → LineRoutine.
 */
@SuppressWarnings("deprecation")
final class LineEnv implements ExprEval.Env {
  private final LineInterp fe;
  private final StoreApi store;

  // uses fe.refs

  LineEnv(LineInterp fe, StoreApi store, LineRefs refs) {
    this.fe = fe;
    this.store = store;
  }

  @Override
  public MValue get(String name) {
    List<String> path = fe.refs.parseRef(name);
    boolean glob = path.get(0).startsWith("^");
    if (!glob && store.data(path, false) % 2 == 0) {
      throw new MError("Undefined local variable");
    }
    return store.get(path, glob);
  }

  @Override
  public void set(String name, MValue v) {
    List<String> path = fe.refs.parseRef(name);
    store.set(path, path.get(0).startsWith("^"), v);
  }

  @Override
  public int data(String name) {
    List<String> path = fe.refs.parseRef(name);
    return store.data(path, path.get(0).startsWith("^"));
  }

  @Override
  public String query(String key, int dir) {
    List<String> path = fe.refs.parseRef(key);
    return store.queryNext(path, path.get(0).startsWith("^"), dir);
  }

  @Override
  public String order(String key, double dir) {
    List<String> path = fe.refs.parseRef(key);
    return store.order(path, path.get(0).startsWith("^"), dir);
  }

  @Override
  public MValue extrinsic(String name, List<MValue> args) {
    return LineRoutine.callExtrinsic(fe, name, args, null);
  }

  @Override
  public MValue extrinsic(String name, List<MValue> args, List<String> byrefs) {
    return LineRoutine.callExtrinsic(fe, name, args, byrefs);
  }
}
