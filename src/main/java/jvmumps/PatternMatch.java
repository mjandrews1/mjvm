package jvmumps;

import java.util.ArrayList;
import java.util.List;

/**
 * M pattern match (ISO 7.1.4.1): classes, literals, counts, and
 * groups {@code n.(...)} / {@code n(alternative,...)}.
 * Full-string match.
 */
public final class PatternMatch {

    /** Full-string M pattern match (`?` is a complete match). */
    public static boolean match(String s, String pat) {
        List<Atom> atoms = parse(pat, 0, pat.length(), false);
        return atoms != null && matchAtoms(s, 0, atoms, 0, true);
    }

    /** Parse a pattern segment; null on syntax error. Stops at unmatched ')' unless inGroup. */
    static List<Atom> parse(String pat, int start, int end, boolean inGroup) {
        List<Atom> out = new ArrayList<>();
        int i = start;
        while (i < end) {
            char c = pat.charAt(i);
            if (c == ')' && inGroup) {
                break;
            }
            if (c == ',' && inGroup) {
                break;
            }
            if (c == ',') {
                // top-level comma is only legal inside groups (alternation)
                return null;
            }
            int nmin = 1;
            int nmax = 1;
            if (c == '.') {
                i++;
                nmin = 0;
                nmax = Integer.MAX_VALUE;
                if (i < end && digit(pat.charAt(i))) {
                    nmax = readInt(pat, i, end);
                    i = lastEnd;
                }
            } else if (digit(c)) {
                nmin = readInt(pat, i, end);
                i = lastEnd;
                if (i < end && pat.charAt(i) == '.') {
                    i++;
                    nmax = Integer.MAX_VALUE;
                    if (i < end && digit(pat.charAt(i))) {
                        nmax = readInt(pat, i, end);
                        i = lastEnd;
                    }
                } else {
                    nmax = nmin;
                }
            }
            if (i >= end) {
                return null; // count without body
            }
            c = pat.charAt(i);
            if (c == '(') {
                // group: alternative [, alternative]*
                i++;
                List<List<Atom>> alts = new ArrayList<>();
                while (i < end && pat.charAt(i) != ')') {
                    List<Atom> alt = parse(pat, i, end, true);
                    if (alt == null) {
                        return null;
                    }
                    alts.add(alt);
                    i = lastEnd;
                    if (i < end && pat.charAt(i) == ',') {
                        i++;
                    } else {
                        break;
                    }
                }
                if (i >= end || pat.charAt(i) != ')') {
                    return null;
                }
                i++;
                if (alts.isEmpty()) {
                    return null;
                }
                out.add(new Atom(nmin, nmax, alts));
                continue;
            }
            if (c == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < end && pat.charAt(i) != '"') {
                    sb.append(pat.charAt(i++));
                }
                if (i >= end) {
                    return null;
                }
                i++;
                out.add(new Atom(nmin, nmax, sb.toString(), null));
                continue;
            }
            if (c == '\'') {
                // negated class/literal (ISO)
                i++;
                if (i < end && pat.charAt(i) == '"') {
                    i++;
                    StringBuilder sb = new StringBuilder();
                    while (i < end && pat.charAt(i) != '"') {
                        sb.append(pat.charAt(i++));
                    }
                    if (i >= end) return null;
                    i++;
                    out.add(new Atom(nmin, nmax, sb.toString(), null, true));
                    continue;
                }
                int cs = i;
                while (i < end && Character.isLetter(pat.charAt(i))) {
                    i++;
                }
                if (i == cs) return null;
                out.add(new Atom(nmin, nmax, null, pat.substring(cs, i).toUpperCase(), true));
                continue;
            }
            if (Character.isLetter(c)) {
                int cs = i;
                while (i < end && Character.isLetter(pat.charAt(i))) {
                    i++;
                }
                out.add(new Atom(nmin, nmax, null, pat.substring(cs, i).toUpperCase(), false));
                continue;
            }
            return null;
        }
        lastEnd = i;
        return out;
    }

    /** One atom occurrence of the group (any alternative), then recurse. */
    static boolean matchGroupOnce(String s, int si, List<List<Atom>> alts, int[] endPos) {
        for (List<Atom> alt : alts) {
            if (matchAtoms(s, si, alt, 0, false)) {
                // need end position of this alternative
                int[] ep = new int[] {si};
                if (matchAtomsPos(s, si, alt, 0, ep)) {
                    endPos[0] = ep[0];
                    return true;
                }
            }
        }
        return false;
    }

    static boolean matchAtomsPos(String s, int si, List<Atom> atoms, int ai, int[] ep) {
        if (ai == atoms.size()) {
            ep[0] = si;
            return true;
        }
        Atom a = atoms.get(ai);
        int min = a.nmin;
        int max = a.nmax == Integer.MAX_VALUE ? s.length() - si : a.nmax;
        if (max < min) {
            return false;
        }
        for (int k = max; k >= min; k--) {
            int[] next = new int[] {si};
            if (matchRepeat(s, si, a, k, next) && matchAtomsPos(s, next[0], atoms, ai + 1, ep)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchAtoms(String s, int si, List<Atom> atoms, int ai, boolean full) {
        if (ai == atoms.size()) {
            return full ? si == s.length() : true;
        }
        Atom a = atoms.get(ai);
        int min = a.nmin;
        int max = a.nmax == Integer.MAX_VALUE ? s.length() - si : a.nmax;
        if (max < min) {
            return false;
        }
        for (int k = max; k >= min; k--) {
            int[] next = new int[] {si};
            if (matchRepeat(s, si, a, k, next) && matchAtoms(s, next[0], atoms, ai + 1, full)) {
                return true;
            }
        }
        return false;
    }

    /** Match atom body k times starting at si; sets next[0]. */
    static boolean matchRepeat(String s, int si, Atom a, int k, int[] next) {
        int pos = si;
        for (int t = 0; t < k; t++) {
            if (a.alts != null) {
                // group: one alternative, match its atom sequence fully
                int[] ep = new int[] {pos};
                if (!matchGroupOnce(s, pos, a.alts, ep)) {
                    return false;
                }
                pos = ep[0];
                continue;
            }
            if (a.negLit != null) {
                if (a.negLit.isEmpty()) {
                    // empty negated literal: 0-width? treat as no-char requirement
                    continue;
                }
                if (s.startsWith(a.negLit, pos)) {
                    return false;
                }
                if (pos >= s.length()) {
                    return false;
                }
                pos++; // consume one char that is not the literal start — ISO quirk
                // Safer: negated literal means "not this string here" for one occurrence of length
                continue;
            }
            if (a.negCls != null) {
                if (pos >= s.length() || classMatch(a.negCls, s.charAt(pos))) {
                    return false;
                }
                pos++;
                continue;
            }
            if (a.lit != null) {
                if (a.lit.isEmpty()) {
                    continue; // 0-width
                }
                if (!s.startsWith(a.lit, pos)) {
                    return false;
                }
                pos += a.lit.length();
                continue;
            }
            // class
            if (pos >= s.length() || !classMatch(a.cls, s.charAt(pos))) {
                return false;
            }
            pos++;
        }
        next[0] = pos;
        return true;
    }

    static int lastEnd;

    static int readInt(String pat, int at, int end) {
        int v = 0;
        int i = at;
        while (i < end && digit(pat.charAt(i))) {
            v = v * 10 + (pat.charAt(i) - '0');
            i++;
        }
        lastEnd = i;
        return v;
    }

    static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    static final class Atom {
        final int nmin;
        final int nmax;
        final String lit;
        final String cls;
        final String negLit;
        final String negCls;
        final List<List<Atom>> alts;

        Atom(int nmin, int nmax, String lit, String cls) {
            this(nmin, nmax, lit, cls, false);
        }

        Atom(int nmin, int nmax, String lit, String cls, boolean neg) {
            this.nmin = nmin;
            this.nmax = nmax;
            if (neg && lit != null) {
                this.negLit = lit;
                this.lit = null;
                this.cls = null;
                this.negCls = null;
            } else if (neg) {
                this.negCls = cls;
                this.lit = null;
                this.cls = null;
                this.negLit = null;
            } else {
                this.lit = lit;
                this.cls = cls;
                this.negLit = null;
                this.negCls = null;
            }
            this.alts = null;
        }

        Atom(int nmin, int nmax, List<List<Atom>> alts) {
            this.nmin = nmin;
            this.nmax = nmax;
            this.lit = null;
            this.cls = null;
            this.negLit = null;
            this.negCls = null;
            this.alts = alts;
        }
    }

    static boolean classMatch(String cls, char c) {
        for (int i = 0; i < cls.length(); i++) {
            char k = cls.charAt(i);
            switch (k) {
                case 'A':
                case 'L':
                case 'U':
                    if (Character.isLetter(c)) return true;
                    break;
                case 'N':
                    if (digit(c)) return true;
                    break;
                case 'P':
                    if (!Character.isLetterOrDigit(c) && !Character.isISOControl(c)) return true;
                    break;
                case 'C':
                    if (Character.isISOControl(c)) return true;
                    break;
                case 'E':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private PatternMatch() {}
}
