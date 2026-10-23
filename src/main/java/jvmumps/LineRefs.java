package jvmumps;

import java.util.ArrayList;
import java.util.List;

/**
 * Name/subscript parsing for {@link LineInterp} (deep split, #58).
 * {@code NAME(a,b)} or {@code ^G(sub)} → path list; quoted subs keep quotes
 * stripped; others evaluate to M numeric text.
 */
final class LineRefs {
    private final ExprEval eval;

    LineRefs(ExprEval eval) {
        this.eval = eval;
    }

    List<String> parseRef(String token) {
        List<String> path = new ArrayList<>();
        int p = token.indexOf('(');
        if (p < 0) {
            path.add(token);
            return path;
        }
        path.add(token.substring(0, p));
        String inner = token.substring(p + 1, token.lastIndexOf(')'));
        int depth = 0;
        boolean inS = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inS) {
                sb.append(c);
                if (c == '"') {
                    inS = false;
                }
                continue;
            }
            if (c == '"') {
                inS = true;
                sb.append(c);
            } else if (c == ',' && depth == 0) {
                path.add(subToken(sb.toString().trim()));
                sb.setLength(0);
            } else {
                if (c == '(') {
                    depth++;
                }
                if (c == ')') {
                    depth--;
                }
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            path.add(subToken(sb.toString().trim()));
        }
        return path;
    }

    String subToken(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return ExprEval.mstr(eval.eval(s).asNum());
    }
}
