package jvmumps;

import java.io.File;
import java.util.*;

public class SqlCorpusSmoke {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("sqc", ".db");
    db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    List<CorpusCase> cs = CorpusCase.load(CorpusCase.corpusPath(a));
    int m = 0, x = 0;
    for (CorpusCase c : cs) {
      if (!c.name.matches("C0[1-9]|C1[0-5]|C0[1-9]\\..*|C1[0-5]\\..*")) continue;
      LineInterp it = new LineInterp(st);
      String got = it.run(c.code);
      String g = got;
      while (g.endsWith("\n")) g = g.substring(0, g.length() - 1);
      String w = c.expected;
      while (w.endsWith("\n")) w = w.substring(0, w.length() - 1);
      if (g.equals(w)) m++;
      else {
        x++;
        System.out.println(
            "MISS " + c.name + " got=" + g.replace("\n", "|") + " want=" + w.replace("\n", "|"));
      }
    }
    st.close();
    System.out.println("SQL CORPUS C01-C15 MATCH=" + m + " MISS=" + x);
  }
}
