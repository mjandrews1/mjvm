package jvmumps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locks + transaction undo for {@link LineInterp} (split from god file, #58).
 * Nested TCOMMIT folds undo into the parent level (ISO provisional).
 */
final class LineTxLocks {
    private final StoreApi store;
    int tlevel = 0;
    final Set<String> locks = new HashSet<>();
    private final List<List<Object[]>> txUndo = new ArrayList<>();
    private final Map<String, MValue> undoVals = new HashMap<>();

    LineTxLocks(StoreApi store) {
        this.store = store;
    }

    void recordUndo(List<String> path, MValue old) {
        if (tlevel > 0) {
            boolean glob = path.get(0).startsWith("^");
            txUndo.get(txUndo.size() - 1).add(new Object[] {path, old});
        }
    }

    void tstart() {
        tlevel++;
        txUndo.add(new ArrayList<>());
    }

    void tcommit() {
        if (tlevel > 0) {
            tlevel--;
            List<Object[]> top = txUndo.remove(txUndo.size() - 1);
            if (!txUndo.isEmpty()) {
                txUndo.get(txUndo.size() - 1).addAll(top);
            }
        }
        undoVals.clear();
    }

    void trollback() {
        if (tlevel > 0) {
            tlevel--;
            List<Object[]> top = txUndo.remove(txUndo.size() - 1);
            for (int i = top.size() - 1; i >= 0; i--) {
                Object[] u = top.get(i);
                @SuppressWarnings("unchecked")
                List<String> path = (List<String>) u[0];
                MValue old = (MValue) u[1];
                boolean glob = path.get(0).startsWith("^");
                // Undefined pre-image is MValue.EMPTY (identity) or null → KILL.
                // set(EMPTY) would leave the node defined ($DATA=1) — #122 leak.
                if (old == null || old == MValue.EMPTY) {
                    store.kill(path, glob);
                } else {
                    store.set(path, glob, old);
                }
            }
        }
        undoVals.clear();
    }

    /** LOCK +ref / -ref; always succeeds locally. */
    void lock(String ref, boolean acquire) {
        String pid = Long.toString(ProcessHandle.current().pid());
        if (acquire) {
            store.lockAcquire(ref, pid, 50);
            locks.add(ref);
        } else {
            store.lockRelease(ref, pid);
            locks.remove(ref);
        }
    }

    void clearUndoVals() {
        undoVals.clear();
    }
}
