package jvmumps;

public class FmJv {
  public static void main(String[] a) {
    Engine e = new LineEngine();
    String[][] cases = {
      {"W $P(\"A,B,C\",\",\",2),!", "B\n"},
      {"S S=\"HELLO\" W $E(S,2,4),!", "ELL\n"},
      {"W $L(\"XXX\"),!", "3\n"},
      {"S A(1)=1,A(2)=2 W $O(A(\"\")),\" \",$O(A(1)),!", "1 2\n"},
      {"S X=$G(Y,\"dflt\") W X,!", "dflt\n"},
    };
    int ok = 0, bad = 0;
    for (String[] c : cases) {
      e.reset();
      String got = e.evalLine(c[0]);
      if (got.equals(c[1])) ok++;
      else {
        bad++;
        System.out.println("FAIL " + c[0] + " got=" + got.replace("\n", "\\n"));
      }
    }
    System.out.println("JVMUMPS_FILEMAN ok=" + ok + " fail=" + bad);
  }
}
