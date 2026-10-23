package jvmumps;

import java.util.List;

/** Smoke test for MapDBStore. */
public final class MapDBStoreSmoke {
    public static void main(String[] args) throws Exception {
        String path = System.getProperty("java.io.tmpdir") + "/mapdbsmoke-" + System.nanoTime() + ".db";
        var s = new MapDBStore(path);
        try {
            s.set(List.of("G", "A"), true, MValue.ofString("hello"));
            assert s.get(List.of("G", "A"), true).asStr().equals("hello") : "get string";
            s.set(List.of("G", "B"), true, MValue.ofNumber(42));
            assert s.get(List.of("G", "B"), true).asNum() == 42 : "get number";

            assert s.data(List.of("G", "A"), true) == 1 : "data value";
            assert s.data(List.of("G"), true) == 10 : "data descendants";

            String o1 = s.order(List.of("G", ""), true, 1);
            assert o1.equals("A") : "order fwd first, got " + o1;
            String o2 = s.order(List.of("G", "A"), true, 1);
            assert o2.equals("B") : "order fwd second, got " + o2;

            String r1 = s.order(List.of("G", ""), true, -1);
            assert r1.equals("B") : "order rev first, got " + r1;

            s.set(List.of("G", "A", "X"), true, MValue.ofString("sub"));
            String sub = s.order(List.of("G", "A", ""), true, 1);
            assert sub.equals("X") : "order subtree, got " + sub;

            s.kill(List.of("G", "A"), true);
            assert s.get(List.of("G", "A"), true) == MValue.EMPTY : "kill node";
            assert s.get(List.of("G", "A", "X"), true) == MValue.EMPTY : "kill descendant";
            assert s.get(List.of("G", "B"), true).asNum() == 42 : "kill sibling preserved";

            s.set(List.of("G", "N"), true, MValue.ofNumber(10));
            MValue inc = s.increment(List.of("G", "N"), true, 5);
            assert inc.asNum() == 15 : "increment, got " + inc.asNum();

            System.out.println("MAPDB SMOKE OK");
        } finally {
            s.close();
            new java.io.File(path).delete();
        }
    }
}
