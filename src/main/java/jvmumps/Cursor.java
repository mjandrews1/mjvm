package jvmumps;

/** Direct-mode line cursor for LineInterp (split from god file, #58). */
final class Cursor {
    final String s;
    int i = 0;
    boolean elsePending = false;

    Cursor(String s) {
        this.s = s;
    }

    boolean atEnd() {
        return i >= s.length();
    }

    char peek() {
        return atEnd() ? '\0' : s.charAt(i);
    }

    char next() {
        return s.charAt(i++);
    }

    void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    void skipToEnd() {
        i = s.length();
    }

    /** Skip commands until ELSE or end (false IF). */
    void skipUntilElse() {
        while (i < s.length()) {
            skipWs();
            if (atEnd()) {
                return;
            }
            if (startsElse()) {
                return;
            }
            skipOneArg();
        }
    }

    boolean startsElse() {
        int j = i;
        while (j < s.length() && s.charAt(j) == ' ') {
            j++;
        }
        if (j >= s.length() || !Character.isLetter(s.charAt(j))) {
            return false;
        }
        int k = j;
        while (k < s.length() && Character.isLetter(s.charAt(k))) {
            k++;
        }
        String w = s.substring(j, k);
        if (k < s.length() && s.charAt(k) != ' ' && s.charAt(k) != ':') {
            return false;
        }
        return w.equalsIgnoreCase("E") || w.equalsIgnoreCase("ELSE");
    }

    /** Consume one command's arguments (up to next command name). */
    void skipOneArg() {
        String name = readCmdName();
        if (name.isEmpty()) {
            skipToEnd();
            return;
        }
        if (peek() == ':') {
            next();
            readExpr();
        }
        skipCmd();
    }

    void markElse() {
        elsePending = true;
    }

    String rest() {
        return s.substring(i);
    }

    String readCmdName() {
        skipWs();
        if (atEnd()) {
            return "";
        }
        int st = i;
        if (Character.isLetter(s.charAt(i))) {
            while (i < s.length() && Character.isLetter(s.charAt(i))) {
                i++;
            }
            return s.substring(st, i);
        }
        return "";
    }

    boolean startsCommand() {
        int j = i;
        while (j < s.length() && s.charAt(j) == ' ') {
            j++;
        }
        if (j >= s.length()) {
            return false;
        }
        if (!Character.isLetter(s.charAt(j))) {
            return false;
        }
        int k = j;
        while (k < s.length() && Character.isLetter(s.charAt(k))) {
            k++;
        }
        String w = s.substring(j, k);
        if (k >= s.length()) {
            return false;
        }
        // command name then space (or colon for postcond)
        if (s.charAt(k) == ' ' || s.charAt(k) == ':') {
            return isCmdWord(w);
        }
        return false;
    }

    static boolean isCmdWord(String w) {
        String up = w.toUpperCase();
        if (up.equals("SET") || up.equals("WRITE") || up.equals("IF") || up.equals("ELSE")
                || up.equals("FOR") || up.equals("KILL") || up.equals("NEW") || up.equals("MERGE")
                || up.equals("QUIT") || up.equals("HALT") || up.equals("HANG")
                || up.equals("TSTART") || up.equals("TCOMMIT") || up.equals("TROLLBACK")
                || up.equals("LOCK") || up.equals("OPEN") || up.equals("CLOSE")
                || up.equals("USE") || up.equals("READ") || up.equals("JOB")) {
            return true;
        }
        return w.length() == 1 && "SWIEFKNMQHGDXLROUBJ".indexOf(Character.toUpperCase(w.charAt(0))) >= 0;
    }

    /** Read a full expression (parens/strings aware) up to space or comma. */
    String readExpr() {
        skipWs();
        int st = i;
        int depth = 0;
        boolean inS = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (inS) {
                if (c == '"') {
                    inS = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inS = true;
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                if (depth == 0) {
                    break; // caller's closer (arg lists)
                }
                depth--;
                i++;
                continue;
            }
            if (depth == 0 && (c == ',' || c == ' ')) {
                break;
            }
            i++;
        }
        return s.substring(st, i).trim();
    }

    String readRefToken() {
        skipWs();
        int st = i;
        int depth = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (depth == 0 && (c == ' ' || c == ',' || c == '=')) {
                break;
            }
            i++;
        }
        return s.substring(st, i).trim();
    }

    String readForSpec() {
        skipWs();
        int st = i;
        int depth = 0;
        boolean inS = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (inS) {
                if (c == '"') {
                    inS = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inS = true;
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (depth == 0 && c == ' ') {
                break;
            }
            i++;
        }
        return s.substring(st, i).trim();
    }

    void skipCmd() {
        // skip to next likely command or end
        int depth = 0;
        boolean inS = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (inS) {
                if (c == '"') {
                    inS = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inS = true;
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (depth == 0 && c == ' ' && i + 1 < s.length()) {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == ' ') {
                    j++;
                }
                if (j < s.length() && Character.isLetter(s.charAt(j))) {
                    int k = j;
                    while (k < s.length() && Character.isLetter(s.charAt(k))) {
                        k++;
                    }
                    String w = s.substring(j, k);
                    if ((k >= s.length() || s.charAt(k) == ' ' || s.charAt(k) == ':')
                            && isCmdWord(w)) {
                        i = j;
                        return;
                    }
                }
            }
            i++;
        }
    }
}
