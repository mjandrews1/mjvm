package mjvm.tools;

import java.util.*;

/**
 * M code expansion tables and methods.
 * Command abbreviations (S→SET, W→WRITE, ...) and special variable
 * abbreviations ($G→$GET, $O→$ORDER, ...).
 */
public final class MFormatterExpansions {

    /** Command abbreviation → full name (ISO 8.1.1). */
    private static final Map<String, String> CMD_EXPAND = new LinkedHashMap<>();
    static {
        // Single-letter abbreviations (longest match wins)
        CMD_EXPAND.put("B", "BREAK");
        CMD_EXPAND.put("C", "CLOSE");
        CMD_EXPAND.put("D", "DO");
        CMD_EXPAND.put("E", "ELSE");
        CMD_EXPAND.put("F", "FOR");
        CMD_EXPAND.put("G", "GOTO");
        CMD_EXPAND.put("H", "HALT");
        CMD_EXPAND.put("I", "IF");
        CMD_EXPAND.put("J", "JOB");
        CMD_EXPAND.put("K", "KILL");
        CMD_EXPAND.put("L", "LOCK");
        CMD_EXPAND.put("M", "MERGE");
        CMD_EXPAND.put("N", "NEW");
        CMD_EXPAND.put("O", "OPEN");
        CMD_EXPAND.put("Q", "QUIT");
        CMD_EXPAND.put("R", "READ");
        CMD_EXPAND.put("S", "SET");
        CMD_EXPAND.put("T", "TSTART");
        CMD_EXPAND.put("U", "USE");
        CMD_EXPAND.put("V", "VIEW");
        CMD_EXPAND.put("W", "WRITE");
        CMD_EXPAND.put("X", "XECUTE");
        // Two-letter abbreviations
        CMD_EXPAND.put("TC", "TCOMMIT");
        CMD_EXPAND.put("TR", "TROLLBACK");
        // Full names (identity, for detection)
        for (String full : new String[]{"BREAK","CLOSE","DO","ELSE","FOR","GOTO",
                "HALT","HANG","IF","JOB","KILL","LOCK","MERGE","NEW","OPEN",
                "QUIT","READ","SET","TSTART","TCOMMIT","TROLLBACK","USE","VIEW",
                "WRITE","XECUTE"}) {
            CMD_EXPAND.put(full, full);
        }
    }

    /** Special variable abbreviation → full name. */
    private static final Map<String, String> SV_EXPAND = new LinkedHashMap<>();
    static {
        SV_EXPAND.put("C", "CHAR");
        SV_EXPAND.put("D", "DATA");
        SV_EXPAND.put("E", "EXTRACT");
        SV_EXPAND.put("F", "FIND");
        SV_EXPAND.put("G", "GET");
        SV_EXPAND.put("H", "HOROLOG");
        SV_EXPAND.put("I", "INCREMENT");
        SV_EXPAND.put("J", "JOB");
        SV_EXPAND.put("K", "KEY");
        SV_EXPAND.put("L", "LENGTH");
        SV_EXPAND.put("NA", "NAME");
        SV_EXPAND.put("O", "ORDER");
        SV_EXPAND.put("P", "PIECE");
        SV_EXPAND.put("QL", "QLENGTH");
        SV_EXPAND.put("QS", "QSUBSCRIPT");
        SV_EXPAND.put("Q", "QUERY");
        SV_EXPAND.put("R", "RANDOM");
        SV_EXPAND.put("S", "STACK");
        SV_EXPAND.put("ST", "TEXT");
        SV_EXPAND.put("T", "TEXT");
        SV_EXPAND.put("V", "VIEW");
        SV_EXPAND.put("X", "X");
        SV_EXPAND.put("Y", "Y");
        SV_EXPAND.put("Z", "Z");
        // Full names (identity)
        for (String full : new String[]{"ASCII","CHAR","DATA","EXTRACT","FIND",
                "GET","HOROLOG","INCREMENT","JOB","KEY","LENGTH","NAME","ORDER",
                "PIECE","QLENGTH","QSUBSCRIPT","QUERY","RANDOM","STACK","TEXT",
                "VIEW","X","Y","Z"}) {
            SV_EXPAND.put(full, full);
        }
    }

    public String expandCommands(String body) {
        if (body.isEmpty()) return body;
        StringBuilder sb = new StringBuilder();
        // Scan character by character, tracking command boundaries
        int i = 0;
        boolean atCmdStart = true;
        while (i < body.length()) {
            char c = body.charAt(i);
            // $$extrinsic call — copy through, don't expand
            if (c == '$' && i + 1 < body.length() && body.charAt(i + 1) == '$') {
                int j = i + 2;
                while (j < body.length() && (Character.isLetterOrDigit(body.charAt(j)) || body.charAt(j) == '%')) j++;
                // Skip past the function name and its argument list
                if (j < body.length() && body.charAt(j) == '(') {
                    int depth = 0;
                    while (j < body.length()) {
                        if (body.charAt(j) == '(') depth++;
                        if (body.charAt(j) == ')') { depth--; if (depth == 0) { j++; break; } }
                        j++;
                    }
                }
                sb.append(body, i, j);
                i = j;
                atCmdStart = false;
                continue;
            }
            // $special variable — copy through
            if (c == '$' && i + 1 < body.length() && body.charAt(i + 1) != '$') {
                int j = i + 1;
                while (j < body.length() && Character.isLetterOrDigit(body.charAt(j))) j++;
                // Skip argument list if present
                if (j < body.length() && body.charAt(j) == '(') {
                    int depth = 0;
                    while (j < body.length()) {
                        if (body.charAt(j) == '(') depth++;
                        if (body.charAt(j) == ')') { depth--; if (depth == 0) { j++; break; } }
                        j++;
                    }
                }
                sb.append(body, i, j);
                i = j;
                atCmdStart = false;
                continue;
            }
            // Command start: letter at command boundary
            if (atCmdStart && Character.isLetter(c)) {
                // Extract word (letters only) and check if it's a command
                int j = i;
                while (j < body.length() && Character.isLetter(body.charAt(j))) j++;
                String word = body.substring(i, j);
                // Check character AFTER the word — must be space/tab/EOL for command
                boolean isCmd = (j >= body.length() || body.charAt(j) == ' ' || body.charAt(j) == '\t');
                String expanded = isCmd ? expandOneCommand(word) : word;
                sb.append(expanded);
                // Preserve any non-letter chars after the word
                i = j;
                atCmdStart = false;
                // After a command word, we need a space before arguments
                // But M allows S X=1 (space between command and args)
                // Just continue scanning — the space will be copied naturally
                continue;
            }
            // Space after command → next word could be a command
            if (c == ' ' || c == '\t') {
                sb.append(c);
                i++;
                // Check if next word is a command
                if (i < body.length() && Character.isLetter(body.charAt(i))) {
                    atCmdStart = true;
                }
                continue;
            }
            // Any other char — copy
            sb.append(c);
            i++;
            atCmdStart = false;
        }
        return sb.toString();
    }

    private int countLeadingCmdChars(String tok) {
        int n = 0;
        while (n < tok.length() && Character.isLetter(tok.charAt(n))) n++;
        return Math.min(n, 7); // max command name length
    }

    /** Expand a single command abbreviation if known. */
    private String expandOneCommand(String word) {
        String up = word.toUpperCase();
        // Try longest match first (TC, TR before T)
        for (int len = Math.min(up.length(), 7); len >= 1; len--) {
            String prefix = up.substring(0, len);
            String expanded = CMD_EXPAND.get(prefix);
            if (expanded != null) {
                // Verify it's a command boundary: next char must be space, tab, or EOL
                // NOT = ( , " etc. — those mean it's a variable/expression, not a command
                if (len == up.length()) return expanded;
                char next = up.charAt(len);
                if (next == ' ' || next == '\t') return expanded;
                return word; // continues past abbreviation — not a command
            }
        }
        return word;
    }

    /**
     * Expand special variable abbreviations: $G→$GET, $O→$ORDER, etc.
     * Pattern: $NAME or $NAME( — expand NAME if it's a known abbreviation.
     */
    public String expandSpecialVars(String body) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            if (body.charAt(i) == '$' && i + 1 < body.length() && body.charAt(i + 1) == '$') {
                // $$extrinsic — copy through
                sb.append("$$");
                i += 2;
                continue;
            }
            if (body.charAt(i) == '$' && i + 1 < body.length()) {
                // $NAME — extract the name
                int j = i + 1;
                // Z-prefix: $Zxxx — extract full name
                if (body.charAt(j) == 'Z' || body.charAt(j) == 'z') {
                    while (j < body.length() && Character.isLetterOrDigit(body.charAt(j))) j++;
                    sb.append(body, i, j);
                    i = j;
                    continue;
                }
                // Standard $ — extract name letters
                while (j < body.length() && Character.isLetter(body.charAt(j))) j++;
                String name = body.substring(i + 1, j);
                String up = name.toUpperCase();
                // Try longest abbreviation match
                String expanded = expandOneSvName(up);
                sb.append('$').append(expanded);
                i = j;
                continue;
            }
            sb.append(body.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** Expand a special variable name abbreviation. */
    private String expandOneSvName(String up) {
        // Try longest match first (QL, QS before Q)
        for (int len = Math.min(up.length(), 8); len >= 1; len--) {
            String prefix = up.substring(0, len);
            String expanded = SV_EXPAND.get(prefix);
            if (expanded != null && (len == up.length() || !Character.isLetterOrDigit(up.charAt(len)))) {
                return expanded;
            }
        }
        return up;
    }

    /**
     * Break one-liners at command boundaries. Each command goes on its own line.
     * Preserves dot depth for continuation.
     */
    public String breakAtCommands(String body) {
        // Find command boundaries and insert newlines + indentation
        // This is a simplified version — full version needs expression parsing
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            // Check if this is a command start: preceded by space and is a known command
            if (i > 0 && (body.charAt(i - 1) == ' ' || body.charAt(i - 1) == '\t')) {
                // Try to match a command abbreviation
                String rest = body.substring(i);
                String cmd = extractCmdAtStart(rest);
                if (cmd != null && cmd.length() < rest.length()) {
                    // Found a command boundary — break here
                    sb.append('\n');
                    // Preserve original indentation (dots)
                    // For now, just indent by 2 spaces
                    sb.append("  ");
                    sb.append(body, i, i + cmd.length());
                    i += cmd.length();
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Try to extract a command name at the start of the string. */
    String extractCmdAtStart(String s) {
        if (s.isEmpty()) return null;
        String up = s.toUpperCase();
        // Try longest match first
        for (int len = Math.min(up.length(), 7); len >= 1; len--) {
            String prefix = up.substring(0, len);
            if (CMD_EXPAND.containsKey(prefix)) {
                // Must be at a boundary: next char is space, tab, or EOL
                // NOT = ( , " etc. — those mean it's an argument
                if (len == up.length()) return prefix;
                char next = up.charAt(len);
                if (next == ' ' || next == '\t') return prefix;
                return null;
            }
        }
        return null;
    }
}
