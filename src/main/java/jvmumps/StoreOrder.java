package jvmumps;

import java.util.List;

/**
 * Ordered traversal operations — $ORDER, $DATA, $QUERY.
 * Required by MStore and Cdb2Store. SqlStore implements for CI/conformance only.
 */
public interface StoreOrder {

    /** $DATA: 0 none · 1 value · 10 descendants · 11 both. */
    int data(List<String> path, boolean global);

    /** $ORDER among children of path (last subscript is cursor). */
    String order(List<String> path, boolean global, double dir);

    /** $QUERY: next node in collation order (full path string) or "". */
    String queryNext(List<String> path, boolean global, int dir);
}
