package jvmumps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Routine / DO / GOTO / QUIT / extrinsic runner for {@link LineInterp} (deep split, #58). */
@SuppressWarnings("deprecation")
final class LineRoutine {
        static java.util.List<LineState.RtLine> parseRoutine(String code) {
            java.util.List<LineState.RtLine> lines = new ArrayList<>();
            for (String raw : code.split("\n", -1)) {
                String tag = null;
                List<String> formals = null;
                String body = raw;
                if (!raw.isEmpty() && !raw.startsWith(" ") && !raw.startsWith(".") && !raw.startsWith("\t")) {
                    int i = 0;
                    while (i < raw.length() && (Character.isLetterOrDigit(raw.charAt(i)) || raw.charAt(i) == '%')) {
                        i++;
                    }
                    String name = i > 0 ? raw.substring(0, i) : "";
                    if (!name.isEmpty()) {
                        char nxt = i < raw.length() ? raw.charAt(i) : ' ';
                        boolean tagShape = (nxt == ' ' || nxt == '(' || i == raw.length());
                        if (tagShape && !Cursor.isCmdWord(name)) {
                            tag = name;
                            if (i < raw.length() && raw.charAt(i) == '(') {
                                int j = raw.indexOf(')', i);
                                if (j > i) {
                                    formals = new ArrayList<>();
                                    for (String f : raw.substring(i + 1, j).split(",")) {
                                        String t = f.trim();
                                        if (!t.isEmpty()) {
                                            formals.add(t);
                                        }
                                    }
                                    i = j + 1;
                                }
                            }
                            while (i < raw.length() && raw.charAt(i) == ' ') {
                                i++;
                            }
                            body = raw.substring(i);
                        }
                    }
                }
                lines.add(new LineState.RtLine(tag, formals, body));
            }
            return lines;
        }

        static boolean looksLikeRoutine(String code) {
            if (code.indexOf('\n') < 0) {
                return false;
            }
            for (String raw : code.split("\n", -1)) {
                if (raw.isEmpty() || raw.startsWith(" ") || raw.startsWith(".") || raw.startsWith("\t")) {
                    continue;
                }
                int i = 0;
                while (i < raw.length() && (Character.isLetterOrDigit(raw.charAt(i)) || raw.charAt(i) == '%')) {
                    i++;
                }
                if (i == 0) {
                    continue;
                }
                String name = raw.substring(0, i);
                char nxt = i < raw.length() ? raw.charAt(i) : ' ';
                boolean tagShape = (nxt == ' ' || nxt == '(' || i == raw.length());
                if (tagShape && !Cursor.isCmdWord(name)) {
                    return true;
                }
            }
            return false;
        }

    private LineRoutine() {}

    static void runRoutine(LineInterp fe, String code) {
        fe.state.prog = parseRoutine(code);
        fe.state.tags.clear();
        for (int i = 0; i < fe.state.prog.size(); i++) {
            String t = fe.state.prog.get(i).tag;
            if (t != null) {
                fe.state.tags.putIfAbsent(t.toUpperCase(), i);
            }
        }
        fe.state.routineMode = true;
        fe.state.pc = 0;
        fe.state.pendingRest = null;
        fe.state.doStack.clear();
        runLoop(fe);
        fe.state.routineMode = false;
        // Keep prog/tags linked: $$ / DO tag must find labels after the
        // top-down run (J13 / FileMan Tier A). Only clearRun drops them.
    }

    static void runLoop(LineInterp fe) {
        while (fe.state.pc < fe.state.prog.size() && !fe.quit) {
            String body = fe.state.pendingRest != null ? fe.state.pendingRest : fe.state.prog.get(fe.state.pc).body;
            boolean wasRest = fe.state.pendingRest != null;
            fe.state.pendingRest = null;
            int pcBefore = fe.state.pc;
            Cursor c = new Cursor(body);
            while (!c.atEnd() && !fe.quit && fe.state.pc == pcBefore && fe.state.pendingRest == null) {
                int before = c.i;
                LineDispatch.execOne(fe, c);
                if (c.i == before && !c.atEnd()) {
                    c.next();
                }
            }
            if (fe.quit) {
                return;
            }
            if (fe.state.pendingRest != null) {
                continue; // QUIT popped / DO redirected with rest
            }
            if (fe.state.pc != pcBefore) {
                continue; // GOTO / DO jumped
            }
            if (!wasRest) {
                fe.state.pc++;
            } else {
                // finished a resume-rest: continue with the next line after the DO line
                fe.state.pc++;
            }
        }
    }

    static int findTag(LineInterp fe, String name) {
        Integer ix = fe.state.tags.get(name.toUpperCase());
        return ix == null ? -1 : ix;
    }

    static void bindFormals(LineInterp fe, LineState.RtLine line, List<MValue> args) {
        bindFormals(fe, line, args, null, null);
    }

    /**
     * Bind formals at call time. {@code byrefs[i]} is the caller's actual
     * variable name when arg i is pass-by-reference {@code .var} (FileMan
     * idiom); else null. Copy-in current value; copy-out runs in
     * {@link #copyOutByref}. Mapping is recorded on {@code frame} when given.
     */
    static void bindFormals(LineInterp fe, LineState.RtLine line, List<MValue> args,
                            List<String> byrefs, LineState.Frame frame) {
        if (line.formals == null) {
            return;
        }
        for (int i = 0; i < line.formals.size(); i++) {
            String formal = line.formals.get(i);
            String by = byrefs != null && i < byrefs.size() ? byrefs.get(i) : null;
            if (by != null) {
                MValue cur = MValue.EMPTY;
                try {
                    cur = fe.eval.eval(by);
                } catch (MError ignored) {
                    // undefined actual — FileMan often passes unset .var
                }
                fe.eval.set(formal, cur);
                if (frame != null && frame.byref != null) {
                    frame.byref.put(formal.toUpperCase(), by);
                }
            } else {
                MValue v = i < args.size() ? args.get(i) : MValue.EMPTY;
                fe.eval.set(formal, v);
            }
        }
    }

    /** Copy byref formals back to caller names (pass-by-reference). */
    static void copyOutByref(LineInterp fe, Map<String, String> byref) {
        if (byref == null || byref.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : byref.entrySet()) {
            String formal = e.getKey();
            String actual = e.getValue();
            MValue v = MValue.EMPTY;
            try {
                v = fe.eval.eval(formal);
            } catch (MError ignored) {
            }
            fe.eval.set(actual, v);
            if (!formal.equalsIgnoreCase(actual)) {
                fe.eval.set(formal, MValue.EMPTY); // kill formal if it was a distinct local
            }
        }
    }

    static MValue callExtrinsic(LineInterp fe, String name, List<MValue> args) {
        return callExtrinsic(fe, name, args, null);
    }

    static MValue callExtrinsic(LineInterp fe, String name, List<MValue> args, List<String> byrefs) {
        if (name != null && name.equalsIgnoreCase("PEMDAS")) {
            return PemdasEval.call(args);
        }
        int target = findTag(fe, name);
        if (target < 0) {
            throw new MError("No such label '" + name + "'");
        }
        int savePc = fe.state.pc;
        String saveRest = fe.state.pendingRest;
        boolean saveQuit = fe.quit;
        MValue saveVal = fe.state.extrinsicValue;
        List<String> savedFormals = null;
        LineState.RtLine tl = fe.state.prog.get(target);
        if (tl.formals != null) {
            savedFormals = new ArrayList<>(tl.formals);
        }
        fe.state.pendingRest = null;
        fe.quit = false;
        fe.state.extrinsicValue = MValue.EMPTY;
        LineState.Frame frame = new LineState.Frame(savePc, saveRest, true, savedFormals);
        bindFormals(fe, tl, args, byrefs, frame);
        int stackBase = fe.state.doStack.size();
        fe.state.doStack.push(frame);
        fe.state.pc = target;
        boolean saveRoutineMode = fe.state.routineMode;
        fe.state.routineMode = true; // Q inside $$ must pop the frame / kill formals
        runLoop(fe);
        fe.state.routineMode = saveRoutineMode;
        // unwind if the target forgot QUIT
        while (fe.state.doStack.size() > stackBase) {
            LineState.Frame f = fe.state.doStack.pop();
            copyOutByref(fe, f.byref);
            if (f.extrinsic) {
                break;
            }
        }
        copyOutByref(fe, frame.byref);
        MValue v = fe.state.extrinsicValue;
        fe.state.extrinsicValue = saveVal;
        fe.state.pc = savePc;
        fe.state.pendingRest = saveRest;
        fe.quit = saveQuit;
        return v;
    }

    static void doDo(LineInterp fe, Cursor c) {
        c.skipWs();
        if (c.atEnd() || c.peek() == ',' || c.startsCommand()) {
            // argumentless DO: run following dotted block if any — J13 treats as no-op
            return;
        }
        boolean extrinsic = false;
        String target = null;
        if (c.peek() == '$' && c.i + 1 < c.s.length() && c.s.charAt(c.i + 1) == '$') {
            // DO $$tag — extrinsic style call
            extrinsic = true;
            c.next();
            c.next();
            target = fe.readTagName(c);
        } else {
            target = fe.readTagName(c);
            if (target.startsWith("^")) {
                // external routine DO ^RTN — not in J13 same-routine set
                return;
            }
        }
        List<MValue> args = new ArrayList<>();
        List<String> byrefs = new ArrayList<>();
        if (c.peek() == '(') {
            c.next();
            args = fe.evalArgs(c, byrefs);
            if (c.peek() == ')') {
                c.next();
            }
        }
        int ix = findTag(fe, target);
        if (ix < 0) {
            throw new MError("No such label '" + target + "'");
        }
        int retLine = fe.state.pc;
        String retRest = c.s.substring(c.i);
        List<String> savedFormals = null;
        LineState.RtLine tl = fe.state.prog.get(ix);
        if (tl.formals != null) {
            savedFormals = new ArrayList<>(tl.formals);
        }
        LineState.Frame frame = new LineState.Frame(retLine, retRest, extrinsic, savedFormals);
        bindFormals(fe, tl, args, byrefs, frame);
        fe.state.doStack.push(frame);
        fe.state.pc = ix;
        fe.state.pendingRest = null;
    }

    static void doGoto(LineInterp fe, Cursor c) {
        c.skipWs();
        String target = fe.readTagName(c);
        if (target.isEmpty() || target.startsWith("^")) {
            return;
        }
        int ix = findTag(fe, target);
        if (ix < 0) {
            throw new MError("No such label '" + target + "'");
        }
        fe.state.pc = ix;
        fe.state.pendingRest = null;
        c.skipToEnd();
    }

    static void doQuit(LineInterp fe) {
        if (!fe.state.routineMode || fe.state.doStack.isEmpty()) {
            fe.quit = true;
            return;
        }
        LineState.Frame f = fe.state.doStack.pop();
        copyOutByref(fe, f.byref);
        killNonByrefFormals(fe, f);
        if (f.extrinsic) {
            fe.quit = true; // end callExtrinsic runLoop
            return;
        }
        fe.state.pc = f.retLine;
        fe.state.pendingRest = f.retRest == null ? "" : f.retRest;
    }

    static void doQuitExpr(LineInterp fe, String exprSrc) {
        MValue v = fe.eval.eval(exprSrc);
        fe.state.extrinsicValue = v;
        if (!fe.state.routineMode || fe.state.doStack.isEmpty()) {
            fe.quit = true;
            return;
        }
        LineState.Frame f = fe.state.doStack.pop();
        copyOutByref(fe, f.byref);
        killNonByrefFormals(fe, f);
        if (f.extrinsic) {
            fe.quit = true;
            return;
        }
        fe.state.pc = f.retLine;
        fe.state.pendingRest = f.retRest == null ? "" : f.retRest;
    }

    /** Kill formals on return, but never the caller's byref actuals. */
    static void killNonByrefFormals(LineInterp fe, LineState.Frame f) {
        if (f.savedFormals == null) {
            return;
        }
        for (String n : f.savedFormals) {
            if (f.byref.containsKey(n.toUpperCase())) {
                continue;
            }
            fe.eval.set(n, MValue.EMPTY);
        }
    }

}
