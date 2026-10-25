package mjvm.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat stack-ish IR for compiled M (mJVM M1).
 *
 * <p>Ops are intentionally few: straight-line SET/WRITE/arith is M2. DO/$$/GOTO via invokedynamic
 * is M3 (CallDyn / GotoDyn stubs).
 */
public final class Ir {
  public static final int PUSH = 1; // PUSH slot|const
  public static final int LOAD = 2; // LOAD name → stack
  public static final int STORE = 3; // STORE name ← stack
  public static final int STORE_ADD = 4; // STORE_ADD name ← stack (atomic increment)
  public static final int BIND = 33; // formal bind: name ← stack, or UNDEF if empty
  public static final int ADD = 10;
  public static final int SUB = 11;
  public static final int MUL = 12;
  public static final int DIV = 13;
  public static final int CONCAT = 14;
  public static final int WRITE = 20; // WRITE top + optional NL
  public static final int WRITE_NL = 21;
  public static final int WRITE_CHAR = 24;
  public static final int WRITE_COL = 25;
  public static final int JMP = 30;
  public static final int JMP0 = 31; // pop, jump if falsey
  public static final int CALL = 40; // CALL name argc (M3 invokedynamic)
  public static final int RET = 50;
  public static final int RET_VALUE = 51; // Q expr: pop → extrinsicValue, then RET
  // M5 devices
  public static final int OPEN = 60;
  public static final int CLOSE = 61;
  public static final int USE = 62;
  public static final int READ = 63;
  // M6 JOB / locks / TX
  public static final int JOB = 70;
  public static final int LOCK = 71;
  public static final int TSTART = 72;
  public static final int TCOMMIT = 73;
  public static final int TROLLBACK = 74;
  // M10 control + data
  public static final int MOD = 15;
  public static final int EQ = 16;
  public static final int LT = 17;
  public static final int GT = 18;
  public static final int LE = 19;
  public static final int GE = 22;
  public static final int NE = 23;
  public static final int NEW = 80;
  public static final int KILL = 81;
  public static final int MERGE = 82;
  public static final int EVAL_EXPR = 83;
  public static final int SET_EXTRACT = 84;
  public static final int SET_PIECE = 85;
  public static final int CALL_PC = 86;
  public static final int RET_TO = 87;
  public static final int END_WRITE = 26;
  public static final int XECUTE = 88;
  public static final int PUSH_REG = 89; // push last stored value (peephole)
  public static final int GETB = 90; // GETB nkeys → push nkeys values (batched read)

  public final int op;
  public final String name; // var / label / write-tag
  public final String ival; // constant string
  public final double dval;
  public final int iarg; // argc / target pc

  public Ir(int op, String name, String ival, double dval, int iarg) {
    this.op = op;
    this.name = name;
    this.ival = ival;
    this.dval = dval;
    this.iarg = iarg;
  }

  public static Ir pushStr(String s) {
    return new Ir(PUSH, null, s, 0, 0);
  }

  public static Ir pushNum(double d) {
    return new Ir(PUSH, null, null, d, 1);
  }

  public static Ir load(String n) {
    return new Ir(LOAD, n, null, 0, 0);
  }

  public static Ir store(String n) {
    return new Ir(STORE, n, null, 0, 0);
  }

  /** Atomic increment: read current value at name, add stack value, store back. */
  public static Ir storeAdd(String n) {
    return new Ir(STORE_ADD, n, null, 0, 0);
  }

  /** Push last stored value onto stack (peephole for redundant reads). */
  public static Ir pushReg() {
    return new Ir(PUSH_REG, null, null, 0, 0);
  }

  /**
   * Formal binding at a call target. On a top-down run the value stack is empty and the formal is
   * simply undefined — which is what M does.
   */
  public static Ir bindFormal(String n) {
    return new Ir(BIND, n, null, 0, 0);
  }

  public static Ir bin(int op) {
    return new Ir(op, null, null, 0, 0);
  }

  public static Ir write() {
    return new Ir(WRITE, null, null, 0, 0);
  }

  /** End of a WRITE command: newline without resetting $X/$Y (C90). */
  /** WRITE @ind — expand argument-indirection string and write pieces. */
  public static Ir writeArgs() {
    return new Ir(WRITE, "@ARGS", null, 0, 1);
  }

  public static Ir endWrite() {
    return new Ir(END_WRITE, null, null, 0, 0);
  }

  public static Ir xecute() {
    return new Ir(XECUTE, null, null, 0, 0);
  }

  public static Ir writeNl() {
    return new Ir(WRITE_NL, null, null, 0, 0);
  }

  public static Ir writeChar() {
    return new Ir(WRITE_CHAR, null, null, 0, 0);
  }

  public static Ir writeCol() {
    return new Ir(WRITE_COL, null, null, 0, 0);
  }

  public static Ir jmp(int target) {
    return new Ir(JMP, null, null, 0, target);
  }

  public static Ir jmp0(int target) {
    return new Ir(JMP0, null, null, 0, target);
  }

  public static Ir call(String fn, int argc) {
    return new Ir(CALL, fn, null, 0, argc);
  }

  public static Ir open() {
    return new Ir(OPEN, null, null, 0, 0);
  }

  public static Ir close() {
    return new Ir(CLOSE, null, null, 0, 0);
  }

  public static Ir use() {
    return new Ir(USE, null, null, 0, 0);
  }

  public static Ir read() {
    return new Ir(READ, null, null, 0, 0);
  }

  public static Ir job() {
    return new Ir(JOB, null, null, 0, 0);
  }

  public static Ir lock(int acquire) {
    return new Ir(LOCK, null, null, 0, acquire);
  }

  public static Ir tstart() {
    return new Ir(TSTART, null, null, 0, 0);
  }

  public static Ir tcommit() {
    return new Ir(TCOMMIT, null, null, 0, 0);
  }

  public static Ir trollback() {
    return new Ir(TROLLBACK, null, null, 0, 0);
  }

  public static Ir ret() {
    return new Ir(RET, null, null, 0, 0);
  }

  /** {@code Q expr} — capture the expression as the extrinsic return value. */
  public static Ir retVal() {
    return new Ir(RET_VALUE, null, null, 0, 0);
  }

  public static Ir newName(String n) {
    return new Ir(NEW, n, null, 0, 0);
  }

  public static Ir killName(String n) {
    return new Ir(KILL, n, null, 0, 0);
  }

  /** MERGE dst ← src (both name strings). */
  public static Ir mergeName(String dst, String src) {
    return new Ir(MERGE, dst, src, 0, 0);
  }

  /** Evaluate an M expression source via JvMumps ExprEval (M11 bridge). */
  public static Ir evalExpr(String src) {
    return new Ir(EVAL_EXPR, null, src, 0, 0);
  }

  /** SET $EXTRACT(ref,from[,to])=v — ival = "from,to" */
  public static Ir setExtract(String ref, int from, int to) {
    return new Ir(SET_EXTRACT, ref, from + "," + to, 0, 0);
  }

  /** SET $PIECE(ref,d,f[,t])=v — ival = "delim,from,to" */
  /** M12: call target pc (native routine DO). */
  public static Ir callPc(int target) {
    return new Ir(CALL_PC, null, null, 0, target);
  }

  public static Ir setPiece(String ref, String d, int from, int to) {
    return new Ir(SET_PIECE, ref, d + "\u0001" + from + "\u0001" + to, 0, 0);
  }

  public static List<Ir> program(Ir... ops) {
    List<Ir> l = new ArrayList<>();
    for (Ir op : ops) l.add(op);
    return l;
  }

  @Override
  public String toString() {
    return op + " " + name + " " + ival + " " + dval + " " + iarg;
  }
}
