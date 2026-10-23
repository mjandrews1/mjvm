package jvmumps;

import java.util.List;

/**
 * Transaction and scope operations — NEW, MERGE.
 * Required by Cdb2Store (distributed transactions). Optional for others.
 */
public interface StoreTx {

    /** NEW: drop named locals for a new stack frame. */
    void newScope(List<String> names);

    /** MERGE: copy src subtree into dst (M MERGE). */
    void merge(List<String> dst, boolean dGlobal, List<String> src, boolean sGlobal);
}
