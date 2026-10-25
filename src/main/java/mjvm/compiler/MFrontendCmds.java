package mjvm.compiler;

import java.util.ArrayList;
import java.util.List;
import jvmumps.MError;
import mjvm.ir.Ir;

/** SET / WRITE compilation for {@link MFrontend} (deep split, #58). */
final class MFrontendCmds {
    private MFrontendCmds() {}

    static int compileSet(MFrontend fe, String line, int i, List<Ir> out) {
        // M11: multi-assign S a=1,b=2 and specials $ETRAP/$E/$P
        for (;;) {
            i = MFrontendUtil.skipWs(line, i);
            if (i >= line.length() || MFrontendUtil.isCmdStart(line, i)) break;
            int st = i;
            if (i < line.length() && line.charAt(i) == '@') {
                int k = MFrontendUtil.scanExprEnd(line, i + 1, " ,=");
                String ind = line.substring(i + 1, k).trim();
                i = MFrontendUtil.skipWs(line, k);
                if (i < line.length() && line.charAt(i) == '=') {
                    i++;
                    int vs = i;
                    i = MFrontendUtil.scanExprEnd(line, i, " ,");
                    out.add(Ir.evalExpr(line.substring(vs, i).trim()));
                    out.add(new Ir(Ir.STORE, "@" + ind, null, 0, 0));
                }
                if (i < line.length() && line.charAt(i) == ',') {
                    i++;
                    continue;
                }
                break;
            }
            // target may be $E(…) $P(…) $ETRAP or a name/subscript ref
            if (i < line.length() && line.charAt(i) == '$') {
                int k = i + 1;
                while (k < line.length() && (Character.isLetter(line.charAt(k)))) k++;
                String sp = line.substring(i, k).toUpperCase();
                if (i < line.length() && k < line.length() && line.charAt(k) == '(') {
                    // $E(ref,f[,t])=v / $P(…) — bridge via evalExpr assignment is not
                    // supported; skip args and use EVAL of full SET via store of result
                    // Fall back: parse ref=target and store into the named variable.
                    k++;
                    int depth = 1;
                    int rs = k;
                    while (k < line.length() && depth > 0) {
                        char c = line.charAt(k);
                        if (c == '(') depth++;
                        else if (c == ')') depth--;
                        if (depth == 0) break;
                        k++;
                    }
                    String inner = line.substring(rs, k);
                    if (k < line.length() && line.charAt(k) == ')') k++;
                    if (k < line.length() && line.charAt(k) == '=') {
                        k++;
                        int vs = k;
                        k = MFrontendUtil.scanExprEnd(line, k, " ,");
                        String val = line.substring(vs, k).trim();
                        String[] parts = MFrontendUtil.splitArgsCommas(inner).toArray(new String[0]);
                        String ref = parts[0].trim();
                        int from = 1;
                        int to = 1;
                        String d = ",";
                        if (sp.equals("$E") || sp.equals("$EXTRACT")) {
                            if (parts.length > 1) from = (int) jvmumps.ExprEval.toNum(parts[1].trim());
                            to = from;
                            if (parts.length > 2) to = (int) jvmumps.ExprEval.toNum(parts[2].trim());
                            out.add(Ir.evalExpr(val));
                            out.add(Ir.setExtract(ref, from, to));
                        } else {
                            // $PIECE(ref,d,f[,t])
                            d = parts.length > 1 ? parts[1].trim() : ",";
                            if (d.length() >= 2 && d.charAt(0) == '"') d = d.substring(1, d.length() - 1);
                            if (parts.length > 2) from = (int) jvmumps.ExprEval.toNum(parts[2].trim());
                            to = from;
                            if (parts.length > 3) to = (int) jvmumps.ExprEval.toNum(parts[3].trim());
                            out.add(Ir.evalExpr(val));
                            out.add(Ir.setPiece(ref, d, from, to));
                        }
                        i = k;
                        if (i < line.length() && line.charAt(i) == ',') {
                            i++;
                            continue;
                        }
                        break;
                    }
                }
                // $ETRAP= etc.
                if (k < line.length() && line.charAt(k) == '=') {
                    k++;
                    int vs = k;
                    k = MFrontendUtil.scanExprEnd(line, k, " ,");
                    String val = line.substring(vs, k).trim();
                    out.add(Ir.evalExpr(val));
                    out.add(Ir.store(sp));
                    i = k;
                    if (i < line.length() && line.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    break;
                }
            }
            while (i < line.length() && (Character.isLetterOrDigit(line.charAt(i))
                    || line.charAt(i) == '%' || line.charAt(i) == '^')) i++;
            if (i == st) throw new MError("SET target must be a name");
            StringBuilder nm = new StringBuilder(line.substring(st, i));
            if (i < line.length() && line.charAt(i) == '(') {
                int depth = 0;
                int stp = i;
                while (i < line.length()) {
                    char c = line.charAt(i++);
                    if (c == '(') depth++;
                    else if (c == ')') {
                        depth--;
                        if (depth == 0) break;
                    }
                }
                nm.append(line, stp, i);
            }
            String name = nm.toString();
            i = MFrontendUtil.skipWs(line, i);
            if (i >= line.length() || line.charAt(i) != '=')
                throw new MError("SET requires = after " + name);
            i++;
            int vs = i;
            i = MFrontendUtil.scanExprEnd(line, i, " ,");
            String val = line.substring(vs, i).trim();
            // Peephole: detect S ^G=$G(^G)+expr → EVAL_EXPR(expr) + STORE_ADD(^G)
            // Also: S ^G=$G(^G)-expr → EVAL_EXPR(0-expr) + STORE_ADD(^G)
            if (name.startsWith("^") && (val.startsWith("$G(") || val.startsWith("$GET("))) {
                // Extract the $G(...) argument and check if it matches target
                int gl = val.indexOf('(');
                int gc = 0;
                int ge = gl;
                while (ge < val.length()) {
                    char ch = val.charAt(ge);
                    if (ch == '(') gc++;
                    else if (ch == ')') { gc--; if (gc == 0) break; }
                    ge++;
                }
                if (ge < val.length() && ge > gl) {
                    String gArg = val.substring(gl + 1, ge).trim();
                    String rest = val.substring(ge + 1).trim();
                    // Check if $G arg matches target and rest is +expr or -expr
                    if (gArg.equals(name)) {
                        if (rest.startsWith("+")) {
                            String expr = rest.substring(1).trim();
                            out.add(Ir.evalExpr(expr));
                            out.add(Ir.storeAdd(name));
                            i = MFrontendUtil.skipWs(line, i);
                            if (i < line.length() && line.charAt(i) == ',') { i++; continue; }
                            break;
                        } else if (rest.startsWith("-")) {
                            String expr = rest.substring(1).trim();
                            out.add(Ir.evalExpr("0-" + expr));
                            out.add(Ir.storeAdd(name));
                            i = MFrontendUtil.skipWs(line, i);
                            if (i < line.length() && line.charAt(i) == ',') { i++; continue; }
                            break;
                        }
                    }
                }
            }
            // Peephole: detect S %var=$I(path) → EVAL_EXPR(1) + STORE_ADD(path) + STORE(%var)
            // Also: S %var=$I(path,amount) → EVAL_EXPR(amount) + STORE_ADD(path) + STORE(%var)
            if ((val.startsWith("$I(") || val.startsWith("$INCREMENT(")) && name.startsWith("%")) {
                int gl = val.indexOf('(');
                int gc = 0;
                int ge = gl;
                while (ge < val.length()) {
                    char ch = val.charAt(ge);
                    if (ch == '(') gc++;
                    else if (ch == ')') { gc--; if (gc == 0) break; }
                    ge++;
                }
                if (ge > gl) {
                    String inner = val.substring(gl + 1, ge).trim();
                    // inner is "path" or "path,amount" — comma must be at paren-depth 0
                    int comma = -1;
                    int d = 0;
                    boolean inStr = false;
                    for (int ci = 0; ci < inner.length(); ci++) {
                        char c = inner.charAt(ci);
                        if (inStr) {
                            if (c == '"') {
                                if (ci + 1 < inner.length() && inner.charAt(ci + 1) == '"') { ci++; continue; }
                                inStr = false;
                            }
                            continue;
                        }
                        if (c == '"') { inStr = true; continue; }
                        if (c == '(') d++;
                        else if (c == ')') d--;
                        else if (c == ',' && d == 0) { comma = ci; break; }
                    }
                    String path = comma >= 0 ? inner.substring(0, comma).trim() : inner;
                    String amount = comma >= 0 ? inner.substring(comma + 1).trim() : "1";
                    out.add(Ir.evalExpr(amount));
                    out.add(Ir.storeAdd(path));
                    out.add(Ir.store(name));
                    i = MFrontendUtil.skipWs(line, i);
                    if (i < line.length() && line.charAt(i) == ',') { i++; continue; }
                    break;
                }
            }
            if (val.indexOf('$') >= 0 || val.indexOf('@') >= 0 || val.indexOf('?') > 0) {
                out.add(Ir.evalExpr(val));
            } else {
                // keep simple IR path
                List<Ir> tmp = new ArrayList<>();
                int ni = fe.compileExpr(line, vs, tmp, " ,");
                out.addAll(tmp);
                i = ni;
            }
            out.add(Ir.store(name));
            i = MFrontendUtil.skipWs(line, i);
            if (i < line.length() && line.charAt(i) == ',') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    static int compileWrite(MFrontend fe, String line, int i, List<Ir> out) {
        boolean pending = false;
        boolean firstArg = true;
        if (i < line.length() && line.charAt(i) == ':') {
            i++;
            int cs = i;
            i = MFrontendUtil.scanExprEnd(line, i, " ");
            String cond = line.substring(cs, i).trim();
            out.add(Ir.evalExpr(cond));
            int j0 = out.size();
            out.add(Ir.jmp0(-1));
            i = compileWrite(fe, line, i, out);
            out.set(j0, Ir.jmp0(out.size()));
            return i;
        }
        while (i < line.length()) {
            i = MFrontendUtil.skipWs(line, i);
            if (i >= line.length()) break;
            char c = line.charAt(i);
            if (c == '!') {
                out.add(Ir.writeNl());
                pending = false;
                firstArg = false;
                i++;
                i = MFrontendUtil.skipWs(line, i);
                if (i < line.length() && line.charAt(i) == ',') i++;
                continue;
            }
            if (c == '#') {
                out.add(Ir.writeNl());
                pending = false;
                i++;
                i = MFrontendUtil.skipWs(line, i);
                if (i < line.length() && line.charAt(i) == ',') i++;
                continue;
            }
            if (c == '*') {
                i++;
                int vs = i;
                i = MFrontendUtil.scanExprEnd(line, i, ", ");
                List<Ir> tmp = new ArrayList<>();
                fe.compileExprSrc(line.substring(vs, i).trim(), tmp);
                out.addAll(tmp);
                out.add(Ir.writeChar());
                pending = true;
                firstArg = false;
                i = MFrontendUtil.skipWs(line, i);
                if (i < line.length() && line.charAt(i) == ',') i++;
                continue;
            }
            if (c == '?') {
                i++;
                int vs = i;
                i = MFrontendUtil.scanExprEnd(line, i, ", ");
                List<Ir> tmp = new ArrayList<>();
                fe.compileExprSrc(line.substring(vs, i).trim(), tmp);
                out.addAll(tmp);
                out.add(Ir.writeCol());
                pending = true;
                firstArg = false;
                i = MFrontendUtil.skipWs(line, i);
                if (i < line.length() && line.charAt(i) == ',') i++;
                continue;
            }
            if (!firstArg && MFrontendUtil.isCmdStart(line, i)) break;
            if (c == '@') {
                int k = MFrontendUtil.scanExprEnd(line, i + 1, " ,");
                out.add(Ir.evalExpr(line.substring(i + 1, k).trim()));
                out.add(Ir.writeArgs());
                pending = true;
                firstArg = false;
                i = k;
                i = MFrontendUtil.skipWs(line, i);
                if (i < line.length() && line.charAt(i) == ',') i++;
                continue;
            }
            int before = i;
            i = fe.compileExpr(line, i, out, ",");
            out.add(Ir.write());
            pending = true;
            firstArg = false;
            if (i == before) {
                throw new MError("WRITE could not parse at " + line.substring(i));
            }
            i = MFrontendUtil.skipWs(line, i);
            if (i < line.length() && line.charAt(i) == ',') {
                i++;
                continue;
            }
            if (MFrontendUtil.isCmdStart(line, i)) break;
        }
        if (pending) out.add(Ir.endWrite());
        return i;
    }
}
