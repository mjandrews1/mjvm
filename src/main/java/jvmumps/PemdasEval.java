package jvmumps;

/**
 * Optional <code>$$PEMDAS(expr)</code> — school precedence (m-semantics#31).
 *
 * <p><b>Not ISO M.</b> Default expression semantics remain {@link ExprEval}.
 * This helper only accepts {@code + - * / **}, unary minus, parens, and
 * numeric literals. Power is <b>right-associative</b> ({@code 2**3**2=512}).
 * Division by zero is <b>0</b> (same as M). Any other token is an error.
 */
public final class PemdasEval {

    private PemdasEval() {}

    /** Evaluate a PEMDAS arithmetic string to an M numeric value. */
    public static MValue eval(String src) {
        P p = new P(src);
        double v = p.parseExpr();
        p.skipWs();
        if (!p.atEnd()) {
            throw new MError("PEMDAS: trailing input at '" + p.rest() + "'");
        }
        return MValue.ofNumber(v);
    }

    /** $$PEMDAS(arg) extrinsic entry (first arg is the expression). */
    public static MValue call(java.util.List<MValue> args) {
        if (args == null || args.isEmpty()) {
            throw new MError("$$PEMDAS requires one argument");
        }
        return eval(args.get(0).asStr());
    }

    private static final class P {
        private final String s;
        private int i;

        P(String s) {
            this.s = s == null ? "" : s;
            this.i = 0;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        String rest() {
            return s.substring(i);
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        boolean peekChar(char c) {
            skipWs();
            return i < s.length() && s.charAt(i) == c;
        }

        char nextChar() {
            return s.charAt(i++);
        }

        double parseExpr() {
            double a = parseMul();
            for (;;) {
                skipWs();
                if (peekChar('+')) {
                    nextChar();
                    a = a + parseMul();
                } else if (peekChar('-')) {
                    nextChar();
                    a = a - parseMul();
                } else {
                    return a;
                }
            }
        }

        double parseMul() {
            double a = parsePow();
            for (;;) {
                skipWs();
                // '**' is handled in parsePow; here only single * and /
                if (i + 1 < s.length() && s.charAt(i) == '*' && s.charAt(i + 1) == '*') {
                    return a;
                }
                if (peekChar('*')) {
                    nextChar();
                    a = a * parsePow();
                } else if (peekChar('/')) {
                    nextChar();
                    double b = parsePow();
                    a = b == 0.0 ? 0.0 : a / b;
                } else {
                    return a;
                }
            }
        }

        /** PEMDAS: unary minus binds looser than ** (-2**2 = -4). */
        double parsePow() {
            skipWs();
            if (peekChar('+')) {
                nextChar();
                return parsePow();
            }
            if (peekChar('-')) {
                nextChar();
                return -parsePow();
            }
            double a = parsePrimary();
            skipWs();
            if (i + 1 < s.length() && s.charAt(i) == '*' && s.charAt(i + 1) == '*') {
                i += 2;
                double b = parsePow(); // right-assoc
                return pow(a, b);
            }
            return a;
        }

        double parseUnary() {
            return parsePow();
        }

        double parsePrimary() {
            skipWs();
            if (peekChar('(')) {
                nextChar();
                double v = parseExpr();
                skipWs();
                if (!peekChar(')')) {
                    throw new MError("PEMDAS: missing ')'");
                }
                nextChar();
                return v;
            }
            int start = i;
            if (i < s.length() && (s.charAt(i) == '.' || Character.isDigit(s.charAt(i)))) {
                while (i < s.length()
                        && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    i++;
                }
                try {
                    return Double.parseDouble(s.substring(start, i));
                } catch (NumberFormatException e) {
                    throw new MError("PEMDAS: bad number '" + s.substring(start, i) + "'");
                }
            }
            throw new MError("PEMDAS: unexpected '" + (atEnd() ? "<eof>" : rest()) + "'");
        }

        static double pow(double base, double exp) {
            // integer exponents (typical); else Math.pow
            if (exp == Math.rint(exp) && Math.abs(exp) <= 64) {
                int n = (int) exp;
                double r = 1.0;
                double b = n < 0 ? 1.0 / base : base;
                int k = Math.abs(n);
                for (int t = 0; t < k; t++) {
                    r *= b;
                }
                return r;
            }
            return Math.pow(base, exp);
        }
    }
}
