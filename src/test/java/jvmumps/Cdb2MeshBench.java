package jvmumps;

import java.util.List;

/**
 * Mesh-plane shape over {@link Cdb2Store}: bidirectional GRAPH edges
 * (ydb-mesh emit_edge) + related() / $ORDER adjacency walks.
 *
 * Usage: Cdb2MeshBench [pipePath] [db] [type] [nEdges] [nWalks]
 */
public class Cdb2MeshBench {
  public static void main(String[] a) throws Exception {
    String pipe = a.length > 0 ? a[0] : "tools/cdb2pipe";
    String db = a.length > 1 ? a[1] : "testdb";
    String type = a.length > 2 ? a[2] : "local";
    int n = a.length > 3 ? Integer.parseInt(a[3]) : 20000;
    int walks = a.length > 4 ? Integer.parseInt(a[4]) : 50;

    Cdb2Store st = new Cdb2Store(pipe, db, type);
    st.kill(List.of("^GRAPH"), true);
    st.kill(List.of("^GRAPHREV"), true);

    // emit_edge: ^GRAPH(mesh,ui,has_mesh,pubmed,pmid) + ^GRAPHREV reverse
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      String ui = "D" + (100000 + i);
      String pmid = String.valueOf(14000000 + i);
      st.set(List.of("^GRAPH", "mesh", ui, "has_mesh", "pubmed", pmid), true, MValue.ofString("1"));
      st.set(List.of("^GRAPHREV", "pubmed", pmid, "has_subject", "mesh", ui), true, MValue.ofString("1"));
    }
    st.flush();
    long t1 = System.currentTimeMillis();

    // related(): $ORDER rel / to_type / to_id under ^GRAPH(mesh, ui)
    long t2 = System.currentTimeMillis();
    int seen = 0;
    for (int w = 0; w < walks; w++) {
      String ui = "D" + (100000 + (w * 97) % n);
      String rel = "";
      while (true) {
        rel = st.order(List.of("^GRAPH", "mesh", ui, rel), true, 1);
        if (rel.isEmpty()) break;
        String tt = "";
        while (true) {
          tt = st.order(List.of("^GRAPH", "mesh", ui, rel, tt), true, 1);
          if (tt.isEmpty()) break;
          String tid = "";
          int cap = 0;
          while (cap++ < 100) {
            tid = st.order(List.of("^GRAPH", "mesh", ui, rel, tt, tid), true, 1);
            if (tid.isEmpty()) break;
            seen++;
          }
        }
      }
    }
    st.flush();
    long t3 = System.currentTimeMillis();

    // articles for one mesh term (forward has_mesh -> pubmed)
    String ui0 = "D" + 100000;
    String pmid = "";
    int rev = 0;
    long t4 = System.currentTimeMillis();
    while (rev < 5000) {
      pmid = st.order(List.of("^GRAPH", "mesh", ui0, "has_mesh", "pubmed", pmid), true, 1);
      if (pmid.isEmpty()) break;
      rev++;
    }
    st.flush();
    long t5 = System.currentTimeMillis();

    System.out.println("CDB2 MESH emit_edge " + n + " pairs (" + (n * 2) + " nodes) in " + (t1 - t0) + "ms");
    System.out.println("CDB2 MESH related walk " + walks + " hubs -> " + seen + " edges in " + (t3 - t2) + "ms");
    System.out.println("CDB2 MESH pubmed-adj walk " + rev + " pmids in " + (t5 - t4) + "ms");
    if (seen <= 0 || rev <= 0) {
      throw new IllegalStateException("mesh walk empty seen=" + seen + " rev=" + rev);
    }
    System.out.println("CDB2 MESH OK");
    st.close();
  }
}
