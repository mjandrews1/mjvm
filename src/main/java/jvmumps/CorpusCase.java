package jvmumps;

import java.util.ArrayList;
import java.util.List;

/** One shared corpus case (same schema as pymumps/c3mumps conformance.json). */
public final class CorpusCase {
    public final String name;
    public final String code;
    public final String expected;

    public CorpusCase(String name, String code, String expected) {
        this.name = name;
        this.code = code;
        this.expected = expected;
    }

    /**
     * Minimal reader for the shared corpus JSON shape:
     * {"cases":[{"name":..,"code":..,"expected":..}, ...]}
     * Avoids a JSON dependency for J0.
     */

    /** Resolve corpus path: args[0] > $CORPUS > tests/conformance.json. */
    public static String corpusPath(String[] args) {
        if (args != null && args.length > 0 && !args[0].isEmpty()) return args[0];
        String env = System.getenv("CORPUS");
        if (env != null && !env.isEmpty()) return env;
        return "tests/conformance.json";
    }

    public static List<CorpusCase> load(String path) throws Exception {
        String text = java.nio.file.Files.readString(java.nio.file.Path.of(path));
        List<CorpusCase> out = new ArrayList<>();
        int arr = text.indexOf("\"cases\"");
        if (arr < 0) {
            return out;
        }
        int i = text.indexOf('[', arr);
        int depth = 0;
        int objStart = -1;
        boolean inStr = false;
        boolean esc = false;
        for (int p = i; p < text.length(); p++) {
            char c = text.charAt(p);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (c == '{' && depth == 1) {
                objStart = p;
            } else if (c == '}' && depth == 1 && objStart >= 0) {
                out.add(parseCase(text.substring(objStart, p + 1)));
                objStart = -1;
            }
        }
        return out;
    }

    private static CorpusCase parseCase(String obj) {
        return new CorpusCase(
                field(obj, "name"),
                field(obj, "code"),
                field(obj, "expected"));
    }

    /** Extract a JSON string field (no escapes beyond \\ and \"). */
    static String field(String obj, String key) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) {
            return "";
        }
        int colon = obj.indexOf(':', k + needle.length());
        int q = obj.indexOf('"', colon + 1);
        if (q < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = q + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (c == '\\' && i + 1 < obj.length()) {
                char n = obj.charAt(++i);
                if (n == 'n') {
                    sb.append('\n');
                } else if (n == 't') {
                    sb.append('\t');
                } else {
                    sb.append(n);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
