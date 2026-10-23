package jvmumps;

import java.io.File;

/** Live SQLite backend smoke (get/set/order/kill/data via SQL). */
public final class J7LiveSmoke {
    public static void main(String[] args) throws Exception {
        File db = File.createTempFile("j7l", ".db");
        db.deleteOnExit();
        SqlStore st = new SqlStore(db.getAbsolutePath());
        java.util.List<String> a = new java.util.ArrayList<>();
        a.add("^G");
        a.add("1");
        st.set(a, true, MValue.ofString("one"));
        java.util.List<String> b = new java.util.ArrayList<>();
        b.add("^G");
        b.add("2");
        b.add("x");
        st.set(b, true, MValue.ofString("deep"));
        if (!st.get(a, true).asStr().equals("one")) {
            throw new IllegalStateException("get");
        }
        if (st.data(b, true) != 1) {
            throw new IllegalStateException("data leaf");
        }
        if (st.data(java.util.List.of("^G", "2"), true) != 10) {
            throw new IllegalStateException("data child-only");
        }
        java.util.List<String> cur = new java.util.ArrayList<>(java.util.List.of("^G", ""));
        String n1 = st.order(cur, true, 1);
        if (!"1".equals(n1)) {
            throw new IllegalStateException("order first=" + n1);
        }
        java.util.List<String> cur2 = new java.util.ArrayList<>(java.util.List.of("^G", n1));
        String n2 = st.order(cur2, true, 1);
        if (!"2".equals(n2)) {
            throw new IllegalStateException("order second=" + n2);
        }
        st.kill(a, true);
        if (st.data(a, true) != 0) {
            throw new IllegalStateException("kill");
        }
        st.close();
        System.out.println("J7 LIVE SQLITE OK");
    }
}
