package jvmumps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * J2 interpreter — command-aware parsing (IF/FOR eat their argument lists).
 * Direct-mode one physical line at a time. J13: tagged routines, DO/GOTO/XECUTE,
 * extrinsics {@code $$tag}.
 */
/**
 * @deprecated For execution, use {@link mjvm.compiler.MFrontend#compileProgram}
 * + {@link mjvm.runtime.IrMachine#run} instead. LineInterp is retained for
 * backward compatibility. See #197 for unification plan.
 */
@Deprecated
public final class LineInterp {
    final StoreApi store;
    final ExprEval eval;
    final StringBuilder out = new StringBuilder();
    boolean test = true;
    boolean quit = false;
    int vx = 0;
    int vy = 0;
    final LineDevices devices = new LineDevices();
    final LineTxLocks tx;
    LineRefs refs;

    final LineState state = new LineState();

    public LineInterp() {
        this(new MStore());
    }

    public LineInterp(StoreApi store) {
        this.store = store;
        this.tx = new LineTxLocks(store);
        this.eval = new ExprEval(new LineEnv(this, store, null));
        this.refs = new LineRefs(this.eval);
    }

    public String run(String line) {
        out.setLength(0);
        quit = false;
        test = true;
        eval.vx = vx;
        eval.vy = vy;
        try {
            if (LineRoutine.looksLikeRoutine(line)) {
                LineRoutine.runRoutine(this, line);
            } else {
                LineExec.exec(this, line);
            }
        } catch (MError e) {
            return LineErrors.report(e);
        }
        return out.toString();
    }

    public void reset() {
        out.setLength(0);
        quit = false;
        test = true;
    }

    /**
     * J14: run one command; on MError fire $ETRAP/$ZTRAP if set.
     * Returns true if the command ran without a trap-handled error.
     */


    // ---- J13 routines -------------------------------------------------







    /** Run from pc until halt or end of program. DO/Q/GOTO steer pc. */












    void doXecute(Cursor c) {
        // X expr[,expr…] — each string runs as commands in this context
        while (!c.atEnd()) {
            c.skipWs();
            if (c.atEnd() || c.startsCommand()) {
                break;
            }
            String ex = c.readExpr();
            if (ex.isEmpty()) {
                break;
            }
            MValue v = eval.eval(ex);
            String body = v.asStr();
            if (!body.isEmpty()) {
                // run as commands (may contain DO/GOTO/XECUTE)
                Cursor xc = new Cursor(body);
                while (!xc.atEnd() && !quit) {
                    int before = xc.i;
                    LineDispatch.execOne(this, xc);
                    if (xc.i == before && !xc.atEnd()) {
                        xc.next();
                    }
                }
            }
            c.skipWs();
            if (c.peek() == ',') {
                c.next();
                continue;
            }
            break;
        }
    }

    String readTagName(Cursor c) {
        c.skipWs();
        int st = c.i;
        if (c.peek() == '^') {
            c.next();
            while (c.i < c.s.length() && (Character.isLetterOrDigit(c.s.charAt(c.i)) || c.s.charAt(c.i) == '%')) {
                c.next();
            }
            return c.s.substring(st, c.i);
        }
        while (c.i < c.s.length() && (Character.isLetterOrDigit(c.s.charAt(c.i)) || c.s.charAt(c.i) == '%')) {
            c.next();
        }
        return c.s.substring(st, c.i);
    }

    List<MValue> evalArgs(Cursor c) {
        return evalArgs(c, null);
    }

    /**
     * Parse a DO/extrinsic arg list. {@code byrefs} (when non-null) receives
     * the caller's actual variable name for {@code .var} pass-by-reference
     * args (FileMan idiom); null entry = by-value.
     */
    List<MValue> evalArgs(Cursor c, List<String> byrefs) {
        List<MValue> args = new ArrayList<>();
        if (c.peek() == ')') {
            return args;
        }
        while (!c.atEnd()) {
            c.skipWs();
            if (c.peek() == '.' && c.i + 1 < c.s.length()
                    && Character.isLetter(c.s.charAt(c.i + 1))) {
                // pass-by-reference .name
                c.next();
                String nm = readTagName(c);
                args.add(MValue.EMPTY);
                if (byrefs != null) {
                    byrefs.add(nm);
                }
            } else {
                String ex = c.readExpr();
                args.add(eval.eval(ex));
                if (byrefs != null) {
                    byrefs.add(null);
                }
            }
            c.skipWs();
            if (c.peek() == ',') {
                c.next();
                continue;
            }
            break;
        }
        return args;
    }







    /** J12b: true if @expr is a whole write-arg (next is ,!#?/end/cmd), not @x+1. */
    boolean argIndirection(Cursor c) {
        int save = c.i;
        c.next(); // @
        try {
            readIndOperand(c);
            c.skipWs();
            if (c.atEnd()) return true;
            char p = c.peek();
            return p == ',' || p == '!' || p == '#' || p == '?' || c.startsCommand();
        } finally {
            c.i = save;
        }
    }

    /** Operand of @: parenthesized expr or name[+subs] (stops before operators). */
    String readIndOperand(Cursor c) {
        c.skipWs();
        if (c.peek() == '(') {
            return c.readExpr();
        }
        int st = c.i;
        while (c.i < c.s.length()) {
            char ch = c.s.charAt(c.i);
            if (ch == ' ' || ch == ',' || ch == ')' || ch == '=' || ch == '!'
                    || ch == '#' || ch == '?' || ch == '_'
                    || ch == '+' || ch == '-' || ch == '*' || ch == '/'
                    || ch == '<' || ch == '>' || ch == '[' || ch == ']' || ch == '&') {
                break;
            }
            c.i++;
        }
        return c.s.substring(st, c.i).trim();
    }

    /** Split argument-indirection text on top-level commas (strings/parens aware). */
    static List<String> splitTopCommas(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        boolean inS = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inS) {
                sb.append(ch);
                if (ch == '"') {
                    inS = false;
                }
                continue;
            }
            if (ch == '"') {
                inS = true;
                sb.append(ch);
                continue;
            }
            if (ch == '(') {
                depth++;
                sb.append(ch);
                continue;
            }
            if (ch == ')') {
                depth--;
                sb.append(ch);
                continue;
            }
            if (ch == ',' && depth == 0) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
                continue;
            }
            sb.append(ch);
        }
        parts.add(sb.toString().trim());
        return parts;
    }



    void flushWriteLine(StringBuilder buf) {
        out.append(buf);
        out.append('\n');
        buf.setLength(0);
        vx = 0;
        vy++;
        eval.vx = vx;
        eval.vy = vy;
    }



    static boolean isExtractSet(String lhs) {
        String u = lhs.toUpperCase();
        return u.startsWith("$E(") || u.startsWith("$EXTRACT(")
                || u.equals("$E") || u.equals("$EXTRACT");
    }

    /** SET $EXTRACT(s,from[,to])=v — replace chars from..to (1-based inclusive). */
    static String setExtractOf(String s, int from, int to, String v) {
        if (to < from) {
            to = from;
        }
        if (from < 1) {
            from = 1;
        }
        int n = s.length();
        if (from > n) {
            // pad with spaces to reach from
            StringBuilder sb = new StringBuilder(s);
            while (sb.length() < from - 1) {
                sb.append(' ');
            }
            sb.append(v);
            return sb.toString();
        }
        int end = Math.min(to, n);
        return s.substring(0, from - 1) + v + s.substring(end);
    }

    /** SET $PIECE(s,d,f[,t])=v — replace pieces f..t with v. */
    public static String setPieceOf(String s, String d, int from, int to, String v) {
        if (to < from) {
            to = from;
        }
        if (from < 1) {
            from = 1;
        }
        if (d.isEmpty()) {
            return s;
        }
        int dl = d.length();
        int piece = 1;
        int start = 0;
        int n = s.indexOf(d);
        while (piece < from && n >= 0) {
            start = n + dl;
            n = s.indexOf(d, start);
            piece++;
        }
        if (piece < from) {
            // pad with delimiters
            StringBuilder pad = new StringBuilder(s);
            while (piece < from - 1) {
                pad.append(d);
                piece++;
            }
            if (from > 1) {
                pad.append(d);
            }
            pad.append(v);
            return pad.toString();
        }
        int end = s.length();
        if (to == from) {
            int n2 = s.indexOf(d, start);
            if (n2 >= 0) {
                end = n2;
            }
        } else {
            int p = from;
            int pos = start;
            while (p < to) {
                int n2 = s.indexOf(d, pos);
                if (n2 < 0) {
                    end = s.length();
                    break;
                }
                pos = n2 + dl;
                p++;
                if (p == to) {
                    int n3 = s.indexOf(d, pos);
                    end = n3 >= 0 ? n3 : s.length();
                    break;
                }
            }
        }
        return s.substring(0, start) + v + s.substring(end);
    }



























    // ---- cursor ------------------------------------------------------


}
