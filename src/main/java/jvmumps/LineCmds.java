package jvmumps;

import java.util.ArrayList;
import java.util.List;

/** SET / WRITE for {@link LineInterp} (deep split, #58). */
@SuppressWarnings("deprecation")
final class LineCmds {
  private LineCmds() {}

  static void doSet(LineInterp fe, Cursor c) {
    // SET var=expr[,var=expr…]  (ISO 8.1.2)
    for (; ; ) {
      c.skipWs();
      if (c.atEnd()) break;
      String lhs = c.readRefToken();
      if (lhs.startsWith("@")) {
        // J12b indirect SET target: S @W2=99 → S Y=99
        lhs = fe.eval.eval(lhs.substring(1)).asStr();
      }
      c.skipWs();
      if (c.peek() != '=') {
        c.skipCmd();
        return;
      }
      c.next();
      MValue v = fe.eval.eval(c.readExpr());
      if (!lhs.startsWith("$")) {
        List<String> path = fe.refs.parseRef(lhs);
        boolean glob = path.get(0).startsWith("^");
        fe.tx.recordUndo(path, fe.store.get(path, glob));
        fe.store.set(path, glob, v);
      } else if (lhs.equalsIgnoreCase("$ETRAP")) {
        fe.eval.etrap = v.asStr();
      } else if (lhs.equalsIgnoreCase("$ECODE")) {
        fe.eval.ecode = v.asStr();
      } else if (lhs.equalsIgnoreCase("$ZTRAP")) {
        fe.eval.ztrap = v.asStr();
      } else if (lhs.equalsIgnoreCase("$X")) {
        fe.vx = (int) v.asNum();
        fe.eval.vx = fe.vx;
      } else if (lhs.equalsIgnoreCase("$Y")) {
        fe.vy = (int) v.asNum();
        fe.eval.vy = fe.vy;
      } else if (fe.isExtractSet(lhs)) {
        // SET $EXTRACT(ref,from[,to])=val (C49)
        int lp = lhs.indexOf('(');
        String inner = lhs.substring(lp + 1, lhs.lastIndexOf(')'));
        List<String> parts = fe.splitTopCommas(inner);
        if (parts.size() >= 2) {
          String ref = parts.get(0);
          int from = (int) fe.eval.eval(parts.get(1)).asNum();
          int to = parts.size() > 2 ? (int) fe.eval.eval(parts.get(2)).asNum() : from;
          String newv = v.asStr();
          List<String> path = fe.refs.parseRef(ref);
          boolean glob = path.get(0).startsWith("^");
          String cur = fe.store.get(path, glob).asStr();
          fe.tx.recordUndo(path, fe.store.get(path, glob));
          fe.store.set(path, glob, MValue.ofString(fe.setExtractOf(cur, from, to, newv)));
        }
      } else if (lhs.toUpperCase(java.util.Locale.ROOT).startsWith("$P")
          || lhs.toUpperCase(java.util.Locale.ROOT).startsWith("$PIECE")) {
        // SET $PIECE(ref,d,f[,t])=val
        int lp = lhs.indexOf('(');
        if (lp > 0) {
          String inner = lhs.substring(lp + 1, lhs.lastIndexOf(')'));
          // first arg is ref name (maybe with subs)
          List<String> parts = new ArrayList<>();
          StringBuilder sb = new StringBuilder();
          int depth = 0;
          boolean inS = false;
          for (char ch : inner.toCharArray()) {
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
            } else if (ch == ')') {
              depth--;
              sb.append(ch);
            } else if (ch == ',' && depth == 0) {
              parts.add(sb.toString().trim());
              sb.setLength(0);
            } else {
              sb.append(ch);
            }
          }
          if (sb.length() > 0) {
            parts.add(sb.toString().trim());
          }
          if (parts.size() >= 3) {
            String ref = parts.get(0);
            String del = fe.eval.eval(parts.get(1)).asStr();
            int from = (int) fe.eval.eval(parts.get(2)).asNum();
            int to = parts.size() > 3 ? (int) fe.eval.eval(parts.get(3)).asNum() : from;
            String newv = v.asStr();
            List<String> path = fe.refs.parseRef(ref);
            boolean glob = path.get(0).startsWith("^");
            String cur = fe.store.get(path, glob).asStr();
            fe.tx.recordUndo(path, fe.store.get(path, glob));
            fe.store.set(path, glob, MValue.ofString(fe.setPieceOf(cur, del, from, to, newv)));
          }
        }
      }
      // next assignment after comma
      c.skipWs();
      if (c.peek() == ',') {
        c.next();
        continue;
      }
      break;
    }
    fe.test = true;
  }

  @SuppressWarnings("RedundantThrows")
  static void doWrite(LineInterp fe, Cursor c) {
    // Line-oriented (shared corpus / PyMumps): each WRITE flushes lines on
    // ! / # / *10 and a trailing flush. Append each line + \n to fe.out.
    StringBuilder buf = new StringBuilder();
    while (!c.atEnd()) {
      c.skipWs();
      if (c.atEnd()) {
        break;
      }
      if (c.peek() == ',') {
        c.next();
        continue;
      }
      if (c.peek() == '!') {
        c.next();
        fe.flushWriteLine(buf);
        continue;
      }
      if (c.peek() == '#') {
        c.next();
        fe.flushWriteLine(buf);
        continue;
      }
      if (c.peek() == '?') {
        c.next();
        String ex = c.readExpr();
        int n = (int) fe.eval.eval(ex).asNum();
        if (n > fe.vx) {
          buf.append(" ".repeat(n - fe.vx));
          fe.vx = n;
          fe.eval.vx = fe.vx;
        }
        continue;
      }
      if (c.peek() == '*') {
        c.next();
        String ex = c.readExpr();
        int code = (int) fe.eval.eval(ex).asNum();
        if (code == 10) {
          fe.flushWriteLine(buf);
        } else {
          char ch = (code >= 0 && code <= 255) ? (char) code : '?';
          buf.append(ch);
          fe.vx++;
          fe.eval.vx = fe.vx;
        }
        continue;
      }
      // next command?
      if (c.startsCommand()) {
        break;
      }
      // J12b argument indirection: @expr as a whole write-arg reparses its
      // string (commas → multiple args). Followed by an operator → name form.
      if (c.peek() == '@' && fe.argIndirection(c)) {
        c.next(); // consume @
        String text = fe.eval.eval(fe.readIndOperand(c)).asStr();
        for (String part : fe.splitTopCommas(text)) {
          MValue v = fe.eval.eval(part);
          String s = v.isnum ? ExprEval.mstr(v.num) : v.str;
          buf.append(s);
          fe.vx += s.length();
          fe.eval.vx = fe.vx;
          fe.eval.vy = fe.vy;
        }
        continue;
      }
      MValue v = fe.eval.eval(c.readExpr());
      String s = v.isnum ? ExprEval.mstr(v.num) : v.str;
      buf.append(s);
      fe.vx += s.length();
      fe.eval.vx = fe.vx;
      fe.eval.vy = fe.vy;
    }
    if (buf.length() > 0) {
      // end-of-WRITE line break does not reset $X/$Y (C90)
      fe.out.append(buf);
      fe.out.append('\n');
      buf.setLength(0);
    }
    fe.test = true;
  }

  @SuppressWarnings("RedundantThrows")
  static void doKill(LineInterp fe, Cursor c) {
    while (!c.atEnd()) {
      c.skipWs();
      if (c.atEnd() || c.startsCommand()) {
        break;
      }
      String ref = c.readRefToken();
      if (ref.startsWith("@")) {
        // J12b indirect KILL: K @S2 → K A(1)
        ref = fe.eval.eval(ref.substring(1)).asStr();
      }
      List<String> path = fe.refs.parseRef(ref);
      fe.store.kill(path, path.get(0).startsWith("^"));
      c.skipWs();
      if (c.peek() == ',') {
        c.next();
      } else {
        break;
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  static void doJob(LineInterp fe, Cursor c) {
    // J10: real JOB — spawn a child JVM running the snippet (async)
    c.skipWs();
    String code = c.readExpr();
    if (code.startsWith("\"") && code.endsWith("\"") && code.length() >= 2) {
      code = code.substring(1, code.length() - 1).replace("\"\"", "\"");
    }
    try {
      String cp = System.getProperty("java.class.path");
      ProcessBuilder pb =
          new ProcessBuilder(
              System.getProperty("java.home") + "/bin/java", "-cp", cp, "jvmumps.JobChild", code);
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);
      pb.start();
    } catch (Exception e) {
      // fallback: local eval
      fe.eval.eval(code);
    }
    fe.test = true;
  }

  @SuppressWarnings("RedundantThrows")
  static void doTStart(LineInterp fe, Cursor c) {
    fe.tx.tstart();
    c.skipCmd();
  }

  @SuppressWarnings("RedundantThrows")
  static void doTCommit(LineInterp fe, Cursor c) {
    fe.tx.tcommit();
    c.skipCmd();
  }

  @SuppressWarnings("RedundantThrows")
  static void doTRollback(LineInterp fe, Cursor c) {
    fe.tx.trollback();
    c.skipCmd();
  }

  @SuppressWarnings("RedundantThrows")
  static void doOpen(LineInterp fe, Cursor c) {
    c.skipWs();
    int n = 1;
    String path = null;
    String tok = c.readRefToken();
    int colon = tok.indexOf(':');
    if (colon > 0) {
      n = (int) ExprEval.toNum(tok.substring(0, colon));
      path = tok.substring(colon + 1);
    } else if (c.peek() == ':') {
      c.next();
      n = (int) ExprEval.toNum(tok);
      path = c.readExpr().replace("\"", "");
    } else {
      path = tok;
    }
    if (path != null) {
      path = path.replace("\"", "");
    }
    if (path != null && !path.isEmpty()) {
      fe.devices.open(n, path);
    }
  }

  @SuppressWarnings("RedundantThrows")
  static void doClose(LineInterp fe, Cursor c) {
    c.skipWs();
    String tok = c.readRefToken();
    int n = 1;
    if (!tok.isEmpty()) {
      n = (int) ExprEval.toNum(tok);
    }
    fe.devices.close(n);
  }

  @SuppressWarnings("RedundantThrows")
  static void doUse(LineInterp fe, Cursor c) {
    c.skipWs();
    String tok = c.readRefToken();
    int n = tok.isEmpty() ? 0 : (int) ExprEval.toNum(tok);
    fe.devices.use(n);
  }

  @SuppressWarnings("RedundantThrows")
  static void doRead(LineInterp fe, Cursor c) {
    String line = fe.devices.readLine();
    c.skipWs();
    String ref = c.readRefToken();
    if (!ref.isEmpty()) {
      List<String> path = fe.refs.parseRef(ref);
      fe.store.set(path, path.get(0).startsWith("^"), MValue.ofString(line));
    }
  }

  @SuppressWarnings("RedundantThrows")
  static void doLock(LineInterp fe, Cursor c) {
    while (!c.atEnd()) {
      c.skipWs();
      if (c.atEnd() || c.startsCommand()) {
        break;
      }
      int sign = 1;
      if (c.peek() == '+') {
        c.next();
        sign = 1;
      } else if (c.peek() == '-') {
        c.next();
        sign = -1;
      }
      String ref = c.readRefToken();
      if (ref.isEmpty()) {
        break;
      }
      fe.tx.lock(ref, sign > 0);
      c.skipWs();
      if (c.peek() == ':') {
        c.next();
        c.readExpr();
        c.skipWs();
      }
      if (c.peek() == ',') {
        c.next();
      } else {
        break;
      }
    }
    fe.test = true;
  }

  @SuppressWarnings("RedundantThrows")
  static void doNew(LineInterp fe, Cursor c) {
    List<String> names = new ArrayList<>();
    while (!c.atEnd()) {
      c.skipWs();
      if (c.atEnd() || c.startsCommand()) {
        break;
      }
      String ref = c.readRefToken();
      if (!ref.isEmpty() && !ref.startsWith("$")) {
        names.add(ref);
      }
      c.skipWs();
      if (c.peek() == ',') {
        c.next();
      } else {
        break;
      }
    }
    fe.store.newScope(names.isEmpty() ? null : names);
  }

  @SuppressWarnings("RedundantThrows")
  static void doMerge(LineInterp fe, Cursor c) {
    c.skipWs();
    String dst = c.readRefToken();
    c.skipWs();
    if (c.peek() != '=') {
      return;
    }
    c.next();
    String src = c.readRefToken();
    List<String> dp = fe.refs.parseRef(dst);
    List<String> sp = fe.refs.parseRef(src);
    fe.store.merge(dp, dp.get(0).startsWith("^"), sp, sp.get(0).startsWith("^"));
  }

  @SuppressWarnings("RedundantThrows")
  static void doFor(LineInterp fe, Cursor c) {
    c.skipWs();
    String var = c.readRefToken();
    c.skipWs();
    if (c.peek() != '=') {
      return;
    }
    c.next();
    // spec is first, second, third or list
    String spec = c.readForSpec();
    String body = c.rest().trim();
    c.skipToEnd();
    if (spec.contains(":")) {
      String[] bits = spec.split(":");
      double i = fe.eval.eval(bits[0]).asNum();
      double step = bits.length > 1 ? fe.eval.eval(bits[1]).asNum() : 1;
      double limit = bits.length > 2 ? fe.eval.eval(bits[2]).asNum() : Double.POSITIVE_INFINITY;
      if (step == 0) {
        step = 1;
      }
      int guard = 0;
      while ((step > 0 ? i <= limit : i >= limit) && !fe.quit && guard++ < 200000) {
        fe.store.set(List.of(var), false, MValue.ofNumber(i));
        if (!body.isEmpty()) {
          LineExec.exec(fe, body);
        }
        i += step;
      }
    } else {
      int guard = 0;
      for (String item : spec.split(",")) {
        if (fe.quit || guard++ > 200000) {
          break;
        }
        fe.store.set(List.of(var), false, fe.eval.eval(item));
        if (!body.isEmpty()) {
          LineExec.exec(fe, body);
        }
      }
    }
    fe.test = true;
  }
}
