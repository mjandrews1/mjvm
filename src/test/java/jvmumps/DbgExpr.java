package jvmumps;

import java.util.*;

public class DbgExpr {
  public static void main(String[] a) {
    ExprEval e =
        new ExprEval(
            new ExprEval.Env() {
              public MValue get(String n) {
                return MValue.EMPTY;
              }

              public void set(String n, MValue v) {}
            });
    System.out.println("1:" + e.eval("\"[\"_\"\"_\"\"]\""));
    System.out.println("2:" + e.eval("\"[]\""));
    System.out.println("3:" + e.eval("1+1"));
    LineInterp m = new LineInterp();
    System.out.println("W:" + m.run("W \"[\"_\"\"_\"\"]\",!"));
  }
}
