package jvmumps;

public class PSys {
  public static void main(String[] a) {
    LineInterp m = new LineInterp();
    System.out.println(
        "[" + m.run("W $PRINCIPAL,\" \",$DEVICE,\" \",$SYSTEM,!").replace("\n", "\\n") + "]");
    System.out.println("[" + m.run("J \"S ^JOB=1\" W \"parent\",!").replace("\n", "\\n") + "]");
    System.out.println("[" + m.run("L +A J \"W 1\" L -A W \"done\",!").replace("\n", "\\n") + "]");
    System.out.println(
        "[" + m.run("L +A TSTART S X=1 TROLLBACK L -A W \"ok\",!").replace("\n", "\\n") + "]");
    System.out.println("[" + m.run("L +A L +A L -A W \"re\",!").replace("\n", "\\n") + "]");
  }
}
