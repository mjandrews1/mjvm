package jvmumps;

import java.util.*;

public class DbgCase {
  public static void main(String[] a) throws Exception {
    List<CorpusCase> cs = CorpusCase.load(CorpusCase.corpusPath(new String[0]));
    for (CorpusCase c : cs) {
      if (c.name.startsWith("C21")) {
        System.out.println("code=[" + c.code + "]");
        System.out.println("want=[" + c.expected + "]");
        LineEngine e = new LineEngine();
        String g = e.evalLine(c.code);
        System.out.println("got =[" + g + "]");
      }
    }
  }
}
