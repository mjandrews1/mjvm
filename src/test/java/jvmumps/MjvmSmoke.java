package jvmumps;

import java.nio.file.Path;
import java.util.List;
import mjvm.compiler.ClassEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/** mJVM M0–M2 smoke: frontend → IR → (IrMachine | generated .class). */
public final class MjvmSmoke {
  static int n = 0;

  static void chk(String code, String want, String tag, boolean useClass) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    List<Ir> prog = new MFrontend().compileLine(code);
    if (useClass) {
      String cn = "MjvS" + (n++);
      Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-smoke");
      ClassEmitter.emit(prog, cn, dir);
      Class<?> cl =
          Class.forName(
              cn,
              true,
              new java.net.URLClassLoader(
                  new java.net.URL[] {dir.toUri().toURL()}, MjvmSmoke.class.getClassLoader()));
      Runnable r = (Runnable) cl.getConstructor(MRuntime.class).newInstance(rt);
      r.run();
    } else {
      IrMachine.run(prog, rt);
    }
    String got = rt.output();
    if (!want.equals(got)) {
      throw new IllegalStateException(
          tag
              + " "
              + code
              + " got="
              + q(got)
              + " want="
              + q(want)
              + (useClass ? " [class]" : " [ir]"));
    }
    n++;
  }

  static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }

  public static void main(String[] a) throws Exception {
    for (boolean useClass : new boolean[] {false, true}) {
      chk("S X=1 W X,!", "1\n", "set-num", useClass);
      chk("S X=\"hi\" W X,!", "hi\n", "set-str", useClass);
      chk("S X=1 S Y=2 W X+Y,!", "3\n", "add", useClass);
      chk("S X=10 S Y=3 W X-Y,!", "7\n", "sub", useClass);
      chk("S X=2 S Y=3 W X*Y,!", "6\n", "mul", useClass);
      chk("S X=6 S Y=3 W X/Y,!", "2\n", "div", useClass);
      chk("W \"a\"_\"b\",!", "ab\n", "concat", useClass);
      chk("S X=1 W X,!,X+1,!", "1\n2\n", "write-nl", useClass);
      chk("W 1+2*3,!", "9\n", "ltr", useClass);
    }
    System.out.println("MjvmSmoke OK cases=" + n);
  }
}
