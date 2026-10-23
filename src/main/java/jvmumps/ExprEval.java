package jvmumps;

import java.util.ArrayList;
import java.util.List;

/**
 * J1 expression evaluator: M values + ISO arithmetic, concat, compare, logic.
 *
 * <p>Precedence (high to low), left-associative including
 * {@code **} (C32: {@code 2**3**2 = 64}):
 * <pre>
 *   unary  - + '
 *   ** * / \ #
 *   + -
 *   _
 *   = &lt; &gt; &lt;= &gt;= [ ] ]] '=
 *   &amp;
 *   !
 * </pre>
 *
 * <p>Number display uses M {@code mstr} ({@code 0.5} -&gt; {@code ".5"}).
 */
public final class ExprEval {
    /** Token cache: expression string → tokenized result. Eliminates re-lexing. */
    private static final java.util.concurrent.ConcurrentHashMap<String, List<ExprTokens.Tok>> tokenCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static String fmtNumber(double n, String fmt, int dec) {
        return Parser.fmtNumber(n, fmt, dec);
    }

    static String fmtDec(double n, int dec) {
        return Parser.fmtDec(n, dec);
    }

    final Env env;

    public interface Env {
        MValue get(String name);

        void set(String name, MValue v);

        /** $DATA: 0/1/10/11. Default: 0/1 from get. */
        default int data(String name) {
            try {
                MValue v = get(name);
                return v == MValue.EMPTY ? 0 : 1;
            } catch (MError e) {
                return 0;
            }
        }

        /** $QUERY next closed ref. */
        default String query(String key, int dir) {
            return key;
        }

        /** $ORDER among siblings. */
        default String order(String key, double dir) {
            return "";
        }

        /** J13: $$tag(args) extrinsic — run a routine tag, return QUIT expr. */
        default MValue extrinsic(String name, List<MValue> args) {
            throw new MError("Extrinsic $$" + name + " not available");
        }

        /**
         * Extracinsic with pass-by-reference {@code .var} args (FileMan).
         * {@code byrefs.get(i)} is the caller's actual name when arg i is
         * byref; else null. Default ignores byrefs → value-only hosts.
         */
        default MValue extrinsic(String name, List<MValue> args, List<String> byrefs) {
            return extrinsic(name, args);
        }
    }

    public ExprEval(Env env) {
        this.env = env;
    }

    /** J13: bind a formal / set via the environment. */
    public void set(String name, MValue v) {
        env.set(name, v);
    }

    /** Last resolved reference name (for $D). */
    private final java.util.Map<MValue, String> refOf = new java.util.IdentityHashMap<>();
    String lastRef;
    public int vx, vy;
    /** J14: trap specials (SET/GET). */
    public String etrap = "";
    public String ecode = "";
    public String ztrap = "";

    public MValue eval(String src) {
        // Fast path: simple $$fn(args) extrinsic call — skip full parser
        // Only for simple args (no byref `.var`, no nested $$, no `@` indirection)
        if (src.startsWith("$$") && src.indexOf('.') < 0 && src.indexOf('@') < 0 && src.indexOf('?') < 0) {
            int paren = src.indexOf('(');
            if (paren > 2 && src.endsWith(")")) {
                String fn = src.substring(2, paren);
                // Strip ^ROUTINE qualifier if present
                int caret = fn.indexOf('^');
                if (caret > 0) fn = fn.substring(0, caret);
                String argStr = src.substring(paren + 1, src.length() - 1);
                // Parse simple args: comma-separated values (variables, numbers, strings)
                List<MValue> args = new ArrayList<>();
                if (!argStr.isEmpty()) {
                    int start = 0;
                    int depth = 0;
                    for (int j = 0; j <= argStr.length(); j++) {
                        if (j == argStr.length() || (argStr.charAt(j) == ',' && depth == 0)) {
                            String a = argStr.substring(start, j).trim();
                            if (!a.isEmpty()) {
                                // Evaluate simple arg (variable, number, or string)
                                List<ExprTokens.Tok> atoks = tokenCache.computeIfAbsent(a, k -> ExprTokens.lex(k));
                                Parser ap = new Parser(atoks);
                                args.add(ap.expr());
                            }
                            start = j + 1;
                        } else if (argStr.charAt(j) == '(') depth++;
                        else if (argStr.charAt(j) == ')') depth--;
                    }
                }
                return env.extrinsic(fn, args, null);
            }
        }
        List<ExprTokens.Tok> toks = tokenCache.computeIfAbsent(src, k -> ExprTokens.lex(k));
        Parser p = new Parser(toks);
        MValue v = p.expr();
        if (!p.atEnd()) {
            throw new MError("syntax: trailing input at " + p.peek());
        }
        return v;
    }

    // ---- number format ------------------------------------------------


    /** $PIECE without splitting the whole string (huge-safe). */
    public static String pieceOf(String s, String d, int from, int to) {
        if (to < 0 || to < from) to = from;
        if (from < 1) from = 1;
        if (d.isEmpty()) {
            return (from <= 1 && to >= 1) ? s : "";
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
        if (piece < from) return "";
        int end = s.length();
        if (to == from) {
            int n2 = s.indexOf(d, start);
            if (n2 >= 0) end = n2;
        } else {
            int p = from;
            int pos = start;
            while (p < to) {
                int n2 = s.indexOf(d, pos);
                if (n2 < 0) { end = s.length(); break; }
                pos = n2 + dl;
                p++;
                if (p == to) {
                    int n3 = s.indexOf(d, pos);
                    end = n3 >= 0 ? n3 : s.length();
                    break;
                }
            }
        }
        return s.substring(start, end);
    }

    public static String mstr(double n) {
        if (n == Math.floor(n) && !Double.isInfinite(n) && Math.abs(n) < 1e15) {
            return Long.toString((long) n);
        }
        // Avoid binary float noise (0.05 → 0.05000000000000001)
        java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(n)).stripTrailingZeros();
        if (bd.scale() < 0) {
            bd = bd.setScale(0);
        }
        String s = bd.toPlainString();
        if (s.startsWith("-0.")) {
            s = "-." + s.substring(3);
        } else if (s.startsWith("0.")) {
            s = "." + s.substring(2);
        }
        return s;
    }

    public static double toNum(String s) {
        try {
            String t = s.trim();
            if (t.isEmpty()) {
                return 0;
            }
            // signs toggle like ncopy
            int i = 0;
            boolean neg = false;
            while (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
                if (t.charAt(i) == '-') {
                    neg = !neg;
                }
                i++;
            }
            String body = t.substring(i);
            // keep leading digits/fraction/exponent
            java.math.BigDecimal bd = new java.math.BigDecimal(body);
            double v = bd.doubleValue();
            return neg ? -v : v;
        } catch (Exception e) {
            return toNumSlow(s);
        }
    }

    static double toNumSlow(String s) {
        int i = 0;
        boolean neg = false;
        while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            if (s.charAt(i) == '-') {
                neg = !neg;
            }
            i++;
        }
        double v = 0;
        boolean saw = false;
        while (i < s.length() && digit(s.charAt(i))) {
            v = v * 10 + (s.charAt(i) - '0');
            i++;
            saw = true;
        }
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            double scale = 0.1;
            while (i < s.length() && digit(s.charAt(i))) {
                v += (s.charAt(i) - '0') * scale;
                scale *= 0.1;
                i++;
                saw = true;
            }
        }
        if (!saw) {
            return 0;
        }
        return neg ? -v : v;
    }

    static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    /** ISO truthiness for IF/AND/OR (RSM cstringtob-like). */
    public static boolean truthy(MValue v) {
        if (v.isnum) {
            return v.num != 0;
        }
        String s = v.str;
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        boolean dp = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '0') {
                i++;
                continue;
            }
            if (c == '.') {
                if (dp) {
                    break;
                }
                dp = true;
                i++;
                continue;
            }
            if (c >= '1' && c <= '9') {
                return true;
            }
            break;
        }
        return false;
    }

    /** Numeric-ish string? whole-string canonical / E-numeric for compare. */
    public static boolean numericStr(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int i = 0;
        if (s.charAt(0) == '+' || s.charAt(0) == '-') {
            i = 1;
        }
        int digits = 0;
        while (i < s.length() && digit(s.charAt(i))) {
            i++;
            digits++;
        }
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            while (i < s.length() && digit(s.charAt(i))) {
                i++;
                digits++;
            }
        }
        if (digits == 0) {
            return false;
        }
        if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            int ed = 0;
            while (i < s.length() && digit(s.charAt(i))) {
                i++;
                ed++;
            }
            if (ed == 0) {
                return false;
            }
        }
        return i == s.length();
    }

    // tokens: see ExprTokens (Kind, Tok, lex) — #58 split

    // ---- parser / eval ------------------------------------------------

    final class Parser {
        final List<ExprTokens.Tok> toks;
        int i = 0;

        Parser(List<ExprTokens.Tok> toks) {
            this.toks = toks;
        }

        ExprTokens.Tok peek() {
            return toks.get(i);
        }

        boolean atEnd() {
            return peek().kind == ExprTokens.Kind.EOF;
        }

        boolean isOp(String... ops) {
            ExprTokens.Tok t = peek();
            if (t.kind != ExprTokens.Kind.OP) {
                return false;
            }
            for (String o : ops) {
                if (t.text.equals(o)) {
                    return true;
                }
            }
            return false;
        }

        String takeOp() {
            return toks.get(i++).text;
        }

        MValue expr() {
            return binaryExpr();
        }

        /**
         * All binary operators are one level, left-associative (C30–C32):
         *   1+2*3 = 9   2*3+1 = 7   2**3**2 = 64   1_2=12 → (1_2)=12
         */
        MValue binaryExpr() {
            MValue a = unary();
            // String concat builder: accumulate consecutive _ operations
            StringBuilder concatBuf = null;
            while (true) {
                if (isOp("_")) {
                    if (concatBuf == null) concatBuf = new StringBuilder(a.asStr());
                    takeOp();
                    MValue b = unary();
                    concatBuf.append(b.asStr());
                } else if (isOp("**", "*", "/", "\\", "#", "+", "-")) {
                    // Flush pending concat before arithmetic
                    if (concatBuf != null) { a = MValue.ofString(concatBuf.toString()); concatBuf = null; }
                    String op = takeOp();
                    MValue b = unary();
                    a = arith(op, a, b);
                } else if (isOp("?", "'?")) {
                    if (concatBuf != null) { a = MValue.ofString(concatBuf.toString()); concatBuf = null; }
                    String op = takeOp();
                    String pat = readPatternSrc();
                    boolean m = PatternMatch.match(a.asStr(), pat);
                    if (op.equals("'?")) {
                        m = !m;
                    }
                    a = m ? MValue.ofNumber(1) : MValue.ofNumber(0);
                } else if (isOp("=", "'=", "<", ">", "<=", ">=", "'<", "'>", "[", "']", "]", "]]")) {
                    if (concatBuf != null) { a = MValue.ofString(concatBuf.toString()); concatBuf = null; }
                    String op = takeOp();
                    MValue b = unary();
                    a = compare(op, a, b);
                } else if (isOp("&", "'&", "!", "'!")) {
                    if (concatBuf != null) { a = MValue.ofString(concatBuf.toString()); concatBuf = null; }
                    String op = takeOp();
                    MValue b = unary();
                    a = logic(op, a, b);
                } else {
                    break;
                }
            }
            // Flush any remaining concat
            if (concatBuf != null) a = MValue.ofString(concatBuf.toString());
            return a;
        }

        MValue arith(String op, MValue a, MValue b) {
            double x = a.asNum();
            double y = b.asNum();
            double r;
            switch (op) {
                case "+":
                    r = x + y;
                    break;
                case "-":
                    r = x - y;
                    break;
                case "*":
                    r = x * y;
                    break;
                case "/":
                    r = y == 0 ? 0 : x / y;
                    break;
                case "\\":
                    r = y == 0 ? 0 : Math.floor(x / y);
                    break;
                case "#":
                    // M #: x - y*floor(x/y)  (C22: -7#3 = 2)
                    r = y == 0 ? 0 : x - y * Math.floor(x / y);
                    break;
                case "**":
                    r = Math.pow(x, y);
                    break;
                default:
                    throw new MError("bad arith op " + op);
            }
            return MValue.ofNumber(r);
        }

        MValue logic(String op, MValue a, MValue b) {
            boolean t;
            switch (op) {
                case "&":
                    t = truthy(a) && truthy(b);
                    break;
                case "'&":
                    t = !(truthy(a) && truthy(b));
                    break;
                case "!":
                    t = truthy(a) || truthy(b);
                    break;
                case "'!":
                    t = !(truthy(a) || truthy(b));
                    break;
                default:
                    throw new MError("bad logic op " + op);
            }
            return t ? MValue.ofNumber(1) : MValue.ofNumber(0);
        }

        MValue unary() {
            if (isOp("-")) {
                takeOp();
                return MValue.ofNumber(-unary().asNum());
            }
            if (isOp("+")) {
                takeOp();
                return MValue.ofNumber(unary().asNum());
            }
            if (isOp("'")) {
                takeOp();
                MValue v = unary();
                return truthy(v) ? MValue.ofNumber(0) : MValue.ofNumber(1);
            }
            return primary();
        }

        MValue primary() {
            ExprTokens.Tok t = peek();
            if (t.kind == ExprTokens.Kind.NUM) {
                i++;
                return MValue.ofNumber(toNum(t.text));
            }
            if (t.kind == ExprTokens.Kind.STR) {
                i++;
                return MValue.ofString(t.text);
            }
            // J5: @expr — argument/name indirection
            if (t.kind == ExprTokens.Kind.OP && t.text.equals("@")) {
                i++;
                MValue v = expr();
                return eval(v.asStr());
            }
            if (t.kind == ExprTokens.Kind.NAME || t.kind == ExprTokens.Kind.GLOBAL) {
                i++;
                if (t.text.startsWith("$$")) {
                    // J13 extrinsic $$tag(args) — may include pass-by-ref .var
                    List<MValue> args = new ArrayList<>();
                    List<String> byrefs = new ArrayList<>();
                    // consume ^routine if present ($$TAG^ROUTINE syntax)
                    if (peek().kind == ExprTokens.Kind.GLOBAL && peek().text.startsWith("^")) {
                        i++;
                    }
                    if (peek().kind == ExprTokens.Kind.LP) {
                        i++;
                        argList(args, byrefs);
                        if (peek().kind == ExprTokens.Kind.RP) {
                            i++;
                        }
                    }
                    // $$TAG^ROUTINE syntax — strip ^routine part
                    String fn = t.text.substring(2);
                    int caret = fn.indexOf('^');
                    if (caret > 0) fn = fn.substring(0, caret);
                    return env.extrinsic(fn, args, byrefs);
                }
                if (t.text.startsWith("$")) {
                    String un = t.text.toUpperCase();
                    if (un.equals("$X")) return MValue.ofNumber(vx);
                    if (un.equals("$Y")) return MValue.ofNumber(vy);
                    return callIntrinsic(t.text);
                }
                String key;
                if (peek().kind == ExprTokens.Kind.LP) {
                    i++;
                    List<MValue> args = argList();
                    if (peek().kind == ExprTokens.Kind.RP) {
                        i++;
                    }
                    StringBuilder inner = new StringBuilder();
                    for (int k = 0; k < args.size(); k++) {
                        if (k > 0) {
                            inner.append(',');
                        }
                        MValue a = args.get(k);
                        if (a.isnum) {
                            inner.append(mstr(a.asNum()));
                        } else {
                            inner.append('"').append(a.str).append('"');
                        }
                    }
                    key = t.text + "(" + inner + ")";
                } else {
                    key = t.text;
                }
                lastRef = key;
                return env.get(key);
            }
            if (t.kind == ExprTokens.Kind.LP) {
                i++;
                MValue v = expr();
                if (peek().kind == ExprTokens.Kind.RP) {
                    i++;
                }
                return v;
            }
            throw new MError("syntax: unexpected " + t);
        }

        List<MValue> argList() {
            List<MValue> args = new ArrayList<>();
            argList(args, null);
            return args;
        }

        /**
         * Fill {@code args}; when {@code byrefs} is non-null, also record the
         * caller's actual variable name for {@code .var} pass-by-reference
         * args (FileMan idiom). Null byref entry = by-value.
         */
        void argList(List<MValue> args, List<String> byrefs) {
            if (peek().kind == ExprTokens.Kind.RP) {
                return;
            }
            while (true) {
                // pass-by-reference .name (not a decimal literal — those are NUM)
                if (peek().kind == ExprTokens.Kind.OP && ".".equals(peek().text)
                        && i + 1 < toks.size()
                        && toks.get(i + 1).kind == ExprTokens.Kind.NAME
                        && !toks.get(i + 1).text.startsWith("$")) {
                    i++; // consume '.'
                    String nm = toks.get(i).text;
                    i++;
                    args.add(MValue.EMPTY);
                    if (byrefs != null) {
                        byrefs.add(nm);
                    }
                } else {
                    args.add(expr());
                    if (byrefs != null) {
                        byrefs.add(null);
                    }
                }
                if (peek().kind == ExprTokens.Kind.COMMA) {
                    i++;
                    continue;
                }
                break;
            }
        }

        /**
         * Pattern source after `?` — counts + class letters or quoted literal.
         * Tokens may be split (1A → NUM 1, NAME A); reassemble.
         */
        String readPatternSrc() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd()) {
                ExprTokens.Tok t = peek();
                if (t.kind == ExprTokens.Kind.RP || t.kind == ExprTokens.Kind.COMMA || t.kind == ExprTokens.Kind.EOF) {
                    break;
                }
                if (t.kind == ExprTokens.Kind.OP && !t.text.equals(".")) {
                    break;
                }
                if (t.kind == ExprTokens.Kind.OP && t.text.equals(".")) {
                    sb.append('.');
                    i++;
                    continue;
                }
                if (t.kind == ExprTokens.Kind.NUM) {
                    sb.append(t.text);
                    i++;
                    continue;
                }
                if (t.kind == ExprTokens.Kind.STR) {
                    sb.append('"').append(t.text).append('"');
                    i++;
                    continue;
                }
                if (t.kind == ExprTokens.Kind.NAME) {
                    sb.append(t.text);
                    i++;
                    continue;
                }
                if (t.kind == ExprTokens.Kind.GLOBAL) {
                    sb.append(t.text);
                    i++;
                    continue;
                }
                break;
            }
            return sb.toString();
        }

        MValue callIntrinsic(String name) {
            String up = name.toUpperCase();
            if (up.equals("$TEXT")) {
                // C71 — argument is a label/ref, not a variable lookup
                if (peek().kind == ExprTokens.Kind.LP) {
                    i++;
                    while (!atEnd() && peek().kind != ExprTokens.Kind.RP) {
                        i++;
                    }
                    if (peek().kind == ExprTokens.Kind.RP) {
                        i++;
                    }
                }
                return MValue.ofString("");
            }
            if (up.equals("$G") || up.equals("$GET") || up.equals("$D") || up.equals("$DATA")
                    || up.equals("$NA") || up.equals("$NAME") || up.equals("$I")
                    || up.equals("$INCREMENT") || up.equals("$O") || up.equals("$ORDER")
                    || up.equals("$NEXT") || up.equals("$N")
                    || up.equals("$Q") || up.equals("$QUERY") || up.equals("$QUIT")) {
                // bare $Q/$QUIT is $QUIT special (C81), not $QUERY
                if ((up.equals("$Q") || up.equals("$QUERY") || up.equals("$QUIT")) && peek().kind != ExprTokens.Kind.LP) {
                    if (up.equals("$Q") || up.equals("$QUIT")) {
                        return MValue.ofNumber(0);
                    }
                }
                if (peek().kind == ExprTokens.Kind.LP) {
                    i++;
                    String key = readRefKey();
                    List<MValue> extra = new ArrayList<>();
                    while (peek().kind == ExprTokens.Kind.COMMA) {
                        i++;
                        extra.add(expr());
                    }
                    if (peek().kind == ExprTokens.Kind.RP) {
                        i++;
                    }
                    lastRef = key;
                    return ExprIntrinsics.intrinsic(ExprEval.this, name, join(key, extra));
                }
                return ExprIntrinsics.intrinsic(ExprEval.this, name, new ArrayList<>());
            }
            List<MValue> args = new ArrayList<>();
            if (peek().kind == ExprTokens.Kind.LP) {
                i++;
                if (up.equals("$S") || up.equals("$SELECT")) {
                    args = selectArgList();
                } else {
                    args = argList();
                }
                if (peek().kind == ExprTokens.Kind.RP) {
                    i++;
                }
            }
            return ExprIntrinsics.intrinsic(ExprEval.this, name, args);
        }

        static List<MValue> join(String key, List<MValue> extra) {
            List<MValue> args = new ArrayList<>();
            args.add(MValue.ofString(key));
            args.addAll(extra);
            return args;
        }

        /** Consume a ref name[+subs] without evaluating it. @ → J12b indirection. */
        String readRefKey() {
            ExprTokens.Tok t = peek();
            if (t.kind == ExprTokens.Kind.OP && t.text.equals("@")) {
                i++;
                MValue ind = expr();
                return ind.asStr();
            }
            if (t.kind != ExprTokens.Kind.NAME && t.kind != ExprTokens.Kind.GLOBAL) {
                return "";
            }
            i++;
            StringBuilder key = new StringBuilder(t.text);
            if (peek().kind == ExprTokens.Kind.LP) {
                i++;
                StringBuilder inner = new StringBuilder();
                boolean first = true;
                while (peek().kind != ExprTokens.Kind.RP && !atEnd()) {
                    if (!first && peek().kind == ExprTokens.Kind.COMMA) {
                        inner.append(',');
                    }
                    if (peek().kind == ExprTokens.Kind.COMMA) {
                        i++;
                        continue;
                    }
                    MValue a = expr();
                    if (!first) {
                        // already appended comma
                    }
                    if (a.isnum) {
                        inner.append(mstr(a.asNum()));
                    } else {
                        inner.append('"').append(a.str).append('"');
                    }
                    first = false;
                }
                if (peek().kind == ExprTokens.Kind.RP) {
                    i++;
                }
                key.append('(').append(inner).append(')');
            }
            return key.toString();
        }

        /** $SELECT pairs: cond:expr, cond:expr, … */
        List<MValue> selectArgList() {
            List<MValue> args = new ArrayList<>();
            if (peek().kind == ExprTokens.Kind.RP) {
                return args;
            }
            while (true) {
                MValue cond = expr();
                args.add(cond);
                if (peek().kind == ExprTokens.Kind.OP && peek().text.equals(":")) {
                    i++;
                    args.add(expr());
                } else {
                    args.add(cond); // degenerate
                }
                if (peek().kind == ExprTokens.Kind.COMMA) {
                    i++;
                    continue;
                }
                break;
            }
            return args;
        }



        static String fmtDec(double n, int dec) {
            java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(n))
                    .setScale(dec, java.math.RoundingMode.HALF_UP);
            return bd.toPlainString();
        }

        static String fmtNumber(double n, String fmt, int dec) {
            if (fmt.isEmpty() && dec < 0) {
                return mstr(n);
            }
            int d = dec >= 0 ? dec : 0;
            String body = fmtDec(n, d);
            boolean comma = fmt.indexOf(',') >= 0;
            int dot = body.indexOf('.');
            String ip = dot < 0 ? body : body.substring(0, dot);
            String fp = dot < 0 ? "" : body.substring(dot);
            if (comma) {
                StringBuilder sb = new StringBuilder();
                int cnt = 0;
                for (int k = ip.length() - 1; k >= 0; k--) {
                    sb.append(ip.charAt(k));
                    cnt++;
                    if (cnt % 3 == 0 && k > 0 && ip.charAt(k - 1) != '-') {
                        sb.append(',');
                    }
                }
                ip = sb.reverse().toString();
            }
            return ip + fp;
        }
    }

    static MValue compare(String op, MValue a, MValue b) {
        boolean res;
        if (op.equals("=")) {
            // ISO: both numeric → numeric compare; else string. (J17 / C24)
            boolean an = a.isnum || numericStr(a.str);
            boolean bn = b.isnum || numericStr(b.str);
            if (an && bn) {
                res = a.asNum() == b.asNum();
            } else {
                res = a.asStr().equals(b.asStr());
            }
        } else if (op.equals("'=")) {
            return compare("=", a, b).str.equals("1")
                    ? MValue.ofNumber(0) : MValue.ofNumber(1);
        } else if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")
                || op.equals("'<") || op.equals("'>")) {
            if (a.isnum && b.isnum) {
                res = cmpNum(op, a.num, b.num);
            } else if (a.isnum || b.isnum) {
                // mixed or numeric-string vs number: numeric compare (C23)
                res = cmpNum(op, a.asNum(), b.asNum());
            } else {
                res = cmpStr(op, a.asStr(), b.asStr());
            }
        } else if (op.equals("[")) {
            res = a.asStr().contains(b.asStr());
        } else if (op.equals("']")) {
            res = !a.asStr().contains(b.asStr());
        } else if (op.equals("]")) {
            res = a.asStr().compareTo(b.asStr()) > 0;
        } else if (op.equals("]]")) {
            res = follows(a.asStr(), b.asStr());
        } else {
            throw new MError("bad rel op " + op);
        }
        return res ? MValue.ofNumber(1) : MValue.ofNumber(0);
    }

    /** C24: only canonical numerics (no +, no 1E2, no 1.0 with extra) compare as numbers with =. */
    static boolean canonicalNum(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int i = 0;
        if (s.charAt(0) == '-') {
            i = 1;
        }
        int digits = 0;
        while (i < s.length() && digit(s.charAt(i))) {
            i++;
            digits++;
        }
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            while (i < s.length() && digit(s.charAt(i))) {
                i++;
                digits++;
            }
        }
        return i == s.length() && digits > 0 && s.charAt(0) != '+';
    }

    static boolean follows(String a, String b) {
        // M ]]: a sorts after b
        return a.compareTo(b) > 0;
    }

    static boolean cmpNum(String op, double x, double y) {
        switch (op) {
            case "<":
                return x < y;
            case ">":
                return x > y;
            case "<=":
                return x <= y;
            case ">=":
                return x >= y;
            case "'<":
                return !(x < y);
            case "'>":
                return !(x > y);
            default:
                return false;
        }
    }

    static boolean cmpStr(String op, String a, String b) {
        int c = a.compareTo(b);
        switch (op) {
            case "<":
                return c < 0;
            case ">":
                return c > 0;
            case "<=":
                return c <= 0;
            case ">=":
                return c >= 0;
            case "'<":
                return !(c < 0);
            case "'>":
                return !(c > 0);
            default:
                return false;
        }
    }
}
