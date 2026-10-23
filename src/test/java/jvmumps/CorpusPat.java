package jvmumps;
import java.io.File;
public class CorpusPat {
  public static void main(String[] a) throws Exception {
    File db = File.createTempFile("cpat", ".db"); db.deleteOnExit();
    SqlStore st = new SqlStore(db.getAbsolutePath());
    LineInterp m = new LineInterp(st);
    String[][] cases = {
      {"T13.pattern", "I \"a\"?1A.AN W \"p1\",! E  W \"p1f\",!", "p1\n"},
      {"T13b.pattern-alt", "I \"ab\"?1A1A W \"p2\",! E  W \"p2f\",!", "p2\n"},
      {"C26.pattern-classes", "W (\"a\"?1A),\" \",(\"1\"?1N),\" \",(\"x\"?1AN),\" \",(\"1\"?1AN),\" \",(\"!\"?1AN),\" \",(\"!\"?1AP),\" \",(\"5\"?1AP),!", "1 1 1 1 0 1 0\n"},
      {"C27.pattern-counts", "W (\"\"?1A),\" \",(\"\"?.A),\" \",(\"abc\"?.A),\" \",(\"abc\"?3A),\" \",(\"abc\"?2.3A),\" \",(\"hello\"?1E),\" \",(\"hello\"?5E),\" \",(\"hello\"?1.5E),!", "0 1 1 1 1 0 1 1\n"},
      {"C28.pattern-literal", "W (\"hello\"?1\"hello\"),\" \",(\"hello\"?1\"hell\"),\" \",(\"hihi\"?2\"hi\"),!", "1 0 1\n"},
      {"C29.pattern-fullstring", "W (\"a\"?1A.AN),\" \",(\"ab\"?1A.AN),\" \",(\"a1\"?1A.AN),\" \",(\"a!\"?1A.AN),!", "1 1 1 0\n"},
    };
    int ok=0;
    for (String[] c : cases) {
      String got = m.run(c[1]);
      if (c[2].equals(got)) { System.out.println(c[0]+" MATCH"); ok++; }
      else System.out.println(c[0]+" MISS got="+got.replace("\n","\\n")+" want="+c[2].replace("\n","\\n"));
    }
    System.out.println("corpus pattern gate "+ok+"/"+cases.length);
    st.close();
  }
}
