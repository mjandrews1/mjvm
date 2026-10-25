package jvmumps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mjvm.compiler.ClassEmitter;
import mjvm.compiler.MFrontend;
import mjvm.ir.Ir;
import mjvm.runtime.IrMachine;
import mjvm.runtime.MRuntime;

/** mJVM M5 devices + M6 JOB/locks/TX smoke. */
public final class MjvmM5M6Smoke {
  static int n = 0;

  static void chkEq(String got, String want, String tag) {
    if (!want.equals(got)) {
      throw new IllegalStateException(tag + " got=" + q(got) + " want=" + q(want));
    }
    n++;
  }

  static void chk(boolean ok, String tag) {
    if (!ok) throw new IllegalStateException(tag);
    n++;
  }

  static String q(String s) {
    return "\"" + s.replace("\n", "\\n") + "\"";
  }

  static MRuntime run(String code, boolean useClass) throws Exception {
    MRuntime rt = new MRuntime(new MStore());
    List<Ir> prog = new MFrontend().compileLine(code);
    if (useClass) {
      String cn = "Mj56_" + (n++);
      Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-m56");
      ClassEmitter.emit(prog, cn, dir);
      Class<?> cl =
          Class.forName(
              cn,
              true,
              new java.net.URLClassLoader(
                  new java.net.URL[] {dir.toUri().toURL()}, MjvmM5M6Smoke.class.getClassLoader()));
      ((Runnable) cl.getConstructor(MRuntime.class).newInstance(rt)).run();
    } else {
      IrMachine.run(prog, rt);
    }
    return rt;
  }

  public static void main(String[] a) throws Exception {
    // M5: OPEN / WRITE to device / READ back / CLOSE
    Path f = Files.createTempFile("mj5", ".txt");
    Files.writeString(f, "alpha\nbeta\n");
    for (boolean useClass : new boolean[] {false, true}) {
      String tag = useClass ? "-class" : "-ir";
      MRuntime rt = run("O 2:\"" + f + "\" U 2 R X U 0 W X,!", useClass);
      chkEq(rt.output(), "alpha\n", "M5-read" + tag);
      rt.closeDevice(2);
      rt = run("O 2:\"" + f + "\" U 2 W \"gamma\",!", useClass);
      chkEq(rt.output(), "", "M5-write-file" + tag);
      rt.closeDevice(2);
      String s = Files.readString(f);
      chk(s.contains("gamma"), "M5-file-has-gamma" + tag);

      // M6: LOCK reenter + release
      rt = run("L +X W \"a\",! L +X W \"b\",! L -X", useClass);
      chkEq(rt.output(), "a\nb\n", "M6-lock" + tag);

      // M6: TSTART / SET / TROLLBACK undoes
      MRuntime rt2 = new MRuntime(new MStore());
      List<Ir> p = new MFrontend().compileLine("S A=0 TSTART S A=1 TROLLBACK W A,!");
      if (useClass) {
        String cn = "Mj56T_" + (n++);
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-m56");
        ClassEmitter.emit(p, cn, dir);
        Class<?> cl =
            Class.forName(
                cn,
                true,
                new java.net.URLClassLoader(
                    new java.net.URL[] {dir.toUri().toURL()},
                    MjvmM5M6Smoke.class.getClassLoader()));
        ((Runnable) cl.getConstructor(MRuntime.class).newInstance(rt2)).run();
      } else {
        IrMachine.run(p, rt2);
      }
      chkEq(rt2.output(), "0\n", "M6-trollback" + tag);

      // M6: TCOMMIT keeps
      rt2 = new MRuntime(new MStore());
      p = new MFrontend().compileLine("S B=0 TSTART S B=1 TCOMMIT W B,!");
      if (useClass) {
        String cn = "Mj56C_" + (n++);
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "mjvm-m56");
        ClassEmitter.emit(p, cn, dir);
        Class<?> cl =
            Class.forName(
                cn,
                true,
                new java.net.URLClassLoader(
                    new java.net.URL[] {dir.toUri().toURL()},
                    MjvmM5M6Smoke.class.getClassLoader()));
        ((Runnable) cl.getConstructor(MRuntime.class).newInstance(rt2)).run();
      } else {
        IrMachine.run(p, rt2);
      }
      chkEq(rt2.output(), "1\n", "M6-tcommit" + tag);

      // M6: JOB async parent W only
      rt = run("J \"W 1\" W \"parent\",!", useClass);
      chkEq(rt.output(), "parent\n", "M6-job" + tag);
    }
    System.out.println("MjvmM5M6Smoke OK cases=" + n);
  }
}
