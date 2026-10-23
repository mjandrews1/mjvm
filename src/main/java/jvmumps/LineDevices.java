package jvmumps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPEN/USE/READ/CLOSE device state for {@link LineInterp} (split from god file, #58).
 * File-backed devices with sequential READ lines.
 */
final class LineDevices {
    private final Map<Integer, String> devPath = new HashMap<>();
    private final Map<Integer, List<String>> devLines = new HashMap<>();
    private final Map<Integer, Integer> devIdx = new HashMap<>();
    int io = 0;

    void open(int n, String path) {
        try {
            List<String> lines = new ArrayList<>();
            for (String ln : java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))) {
                lines.add(ln);
            }
            devPath.put(n, path);
            devLines.put(n, lines);
            devIdx.put(n, 0);
            io = n;
        } catch (Exception e) {
            throw new MError("OPEN failed: " + e);
        }
    }

    void close(int n) {
        devPath.remove(n);
        devLines.remove(n);
        devIdx.remove(n);
        if (io == n) {
            io = 0;
        }
    }

    void use(int n) {
        if (n != 0 && !devPath.containsKey(n)) {
            throw new MError("USE unopened device");
        }
        io = n;
    }

    String readLine() {
        String line = "";
        List<String> lines = devLines.get(io);
        Integer idx = devIdx.get(io);
        if (lines != null && idx != null && idx < lines.size()) {
            line = lines.get(idx);
            devIdx.put(io, idx + 1);
        }
        return line;
    }
}
