package jvmumps;

import java.util.List;

/** $intrinsic table for {@link ExprEval} (split from god file, #58). */
final class ExprIntrinsics {
  private ExprIntrinsics() {}

  static MValue intrinsic(ExprEval ev, String name, List<MValue> args) {
    String n = name.toUpperCase(java.util.Locale.ROOT);
    switch (n) {
      case "$H":
      case "$HOROLOG":
        {
          long secs = System.currentTimeMillis() / 1000;
          // Standard M: $H = days since 1840-12-31
          // Java epoch: 1970-01-01 = 47117 days after 1840-12-31
          long days = secs / 86400 + 47117;
          return MValue.ofString(days + "," + (secs % 86400));
        }
      case "$ZDATE":
        {
          // $ZDATE(horolog,format) - format date from $H
          // Default format: DD MMM YYYY
          if (args.isEmpty()) return MValue.ofString("");
          String h = args.get(0).asStr();
          int comma = h.indexOf(',');
          long days = comma > 0 ? Long.parseLong(h.substring(0, comma)) : 0;
          // Convert days since 1840-12-31 to calendar date
          int year = 1841;
          long rem = days;
          while (rem >= 365) {
            long dy = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 366 : 365;
            if (rem < dy) break;
            rem -= dy;
            year++;
          }
          int month = 1;
          while (month <= 12) {
            int dim =
                (month == 2)
                    ? ((year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) ? 29 : 28)
                    : (month == 4 || month == 6 || month == 9 || month == 11) ? 30 : 31;
            if (rem < dim) break;
            rem -= dim;
            month++;
          }
          int day = (int) rem + 1;
          String[] months = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
          };
          if (args.size() > 1 && args.get(1).asStr().equals("YYYY-MM-DD"))
            return MValue.ofString(String.format("%04d-%02d-%02d", year, month, day));
          return MValue.ofString(String.format("%02d %s %04d", day, months[month - 1], year));
        }
      case "$D":
      case "$DATA":
        {
          if (ev.lastRef != null) {
            return MValue.ofNumber(ev.env.data(ev.lastRef));
          }
          if (args.isEmpty()) {
            return MValue.ofNumber(0);
          }
          return MValue.ofNumber(ev.env.data(args.get(0).asStr()));
        }
      case "$L":
      case "$LENGTH":
        {
          if (args.isEmpty()) {
            return MValue.ofNumber(0);
          }
          String s = args.get(0).asStr();
          if (args.size() >= 2) {
            String d = args.get(1).asStr();
            if (d.isEmpty()) {
              return MValue.ofNumber(s.length() + 1);
            }
            int c = 0;
            int i = 0;
            while (i < s.length()) {
              c++;
              i = s.indexOf(d, i);
              if (i < 0) {
                break;
              }
              i += d.length();
            }
            return MValue.ofNumber(c);
          }
          return MValue.ofNumber(s.length());
        }
      case "$E":
      case "$EXTRACT":
        {
          String s = args.get(0).asStr();
          int a = args.size() > 1 ? (int) args.get(1).asNum() : 1;
          int b = args.size() > 2 ? (int) args.get(2).asNum() : a;
          if (a < 1) {
            a = 1;
          }
          if (b > s.length()) {
            b = s.length();
          }
          if (a > s.length() || a > b) {
            return MValue.ofString("");
          }
          return MValue.ofString(s.substring(a - 1, b));
        }
      case "$P":
      case "$PIECE":
        return MValue.ofString(
            ExprEval.pieceOf(
                args.get(0).asStr(),
                args.size() > 1 ? args.get(1).asStr() : " ",
                args.size() > 2 ? (int) args.get(2).asNum() : 1,
                args.size() > 3 ? (int) args.get(3).asNum() : -1));
      case "$C":
      case "$CHAR":
        {
          StringBuilder sb = new StringBuilder();
          for (MValue a : args) {
            int c = (int) a.asNum();
            // RSM/8-bit: 256+ yields empty (C39)
            if (c >= 1 && c <= 255) {
              sb.append((char) c);
            }
          }
          return MValue.ofString(sb.toString());
        }
      case "$A":
      case "$ASCII":
        {
          String s = args.get(0).asStr();
          int p = args.size() > 1 ? (int) args.get(1).asNum() : 1;
          if (p < 1 || p > s.length()) {
            return MValue.ofNumber(-1);
          }
          return MValue.ofNumber(s.charAt(p - 1));
        }
      // ---- J4 intrinsics ----
      case "$F":
      case "$FIND":
        {
          String s = args.get(0).asStr();
          String t = args.get(1).asStr();
          int from = args.size() > 2 ? (int) args.get(2).asNum() : 1;
          if (from < 1) {
            from = 1;
          }
          if (t.isEmpty()) {
            return MValue.ofNumber(from > s.length() + 1 ? 0 : from);
          }
          int idx = s.indexOf(t, from - 1);
          return MValue.ofNumber(idx < 0 ? 0 : idx + t.length() + 1);
        }
      case "$G":
      case "$GET":
        {
          if (args.isEmpty()) {
            return MValue.EMPTY;
          }
          try {
            MValue v = ev.env.get(args.get(0).asStr());
            if (v == MValue.EMPTY && args.size() > 1) {
              return args.get(1);
            }
            return v;
          } catch (MError e) {
            return args.size() > 1 ? args.get(1) : MValue.EMPTY;
          }
        }
      case "$J":
      case "$JUSTIFY":
        {
          String s;
          if (args.get(0).isnum && args.size() >= 3) {
            double jnum = args.get(0).asNum();
            int dec = (int) args.get(2).asNum();
            s = ExprEval.fmtDec(jnum, dec);
          } else {
            s = args.get(0).asStr();
          }
          int w = args.size() > 1 ? (int) args.get(1).asNum() : s.length();
          if (s.length() >= w) {
            return MValue.ofString(s);
          }
          StringBuilder sb = new StringBuilder();
          for (int k = s.length(); k < w; k++) {
            sb.append(' ');
          }
          sb.append(s);
          return MValue.ofString(sb.toString());
        }
      case "$RE":
      case "$REVERSE":
        return MValue.ofString(new StringBuilder(args.get(0).asStr()).reverse().toString());
      case "$TR":
      case "$TRANSLATE":
        {
          String s = args.get(0).asStr();
          String old = args.size() > 1 ? args.get(1).asStr() : "";
          String neu = args.size() > 2 ? args.get(2).asStr() : "";
          StringBuilder sb = new StringBuilder();
          for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            int p = old.indexOf(c);
            if (p < 0) {
              sb.append(c);
            } else if (p < neu.length()) {
              sb.append(neu.charAt(p));
            }
          }
          return MValue.ofString(sb.toString());
        }
      case "$NA":
      case "$NAME":
        {
          String key = ev.lastRef != null ? ev.lastRef : args.get(0).asStr();
          int keep = args.size() > 1 ? (int) args.get(1).asNum() : -1;
          if (keep < 0) {
            return MValue.ofString(key);
          }
          // keep first `keep` subscripts: A(1,"b") + keep 1 -> A(1)
          int depth = 0;
          int close = -1;
          for (int k = 0; k < key.length(); k++) {
            char c = key.charAt(k);
            if (c == '(') {
              depth++;
              if (depth == 1) {
                close = k;
              }
            } else if (c == ',' && depth == 1 && keep == 1) {
              return MValue.ofString(key.substring(0, k) + ")");
            }
          }
          if (keep == 0) {
            int p = key.indexOf('(');
            return MValue.ofString(p < 0 ? key : key.substring(0, p));
          }
          return MValue.ofString(key);
        }
      case "$QL":
      case "$QLENGTH":
        {
          String key = args.get(0).asStr();
          int p = key.indexOf('(');
          if (p < 0) {
            return MValue.ofNumber(0);
          }
          int depth = 0;
          int qlN = 1;
          boolean inS = false;
          for (int k = p + 1; k < key.length(); k++) {
            char c = key.charAt(k);
            if (inS) {
              if (c == '"') {
                inS = false;
              }
              continue;
            }
            if (c == '"') {
              inS = true;
            } else if (c == ',' && depth == 0) {
              qlN++;
            } else if (c == '(') {
              depth++;
            } else if (c == ')') {
              depth--;
            }
          }
          return MValue.ofNumber(qlN);
        }
      case "$QS":
      case "$QSUBSCRIPT":
        {
          String key = args.get(0).asStr();
          int qsN = (int) args.get(1).asNum();
          if (qsN <= 0) {
            int p = key.indexOf('(');
            return MValue.ofString(p < 0 ? key : key.substring(0, p));
          }
          int open = key.indexOf('(');
          if (open < 0) {
            return MValue.ofString("");
          }
          int nest = 0;
          int sub = 1;
          StringBuilder sb = new StringBuilder();
          boolean inS = false;
          for (int k = open + 1; k < key.length(); k++) {
            char c = key.charAt(k);
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
              continue;
            }
            if (c == '(') {
              nest++;
              sb.append(c);
              continue;
            }
            if (c == ')') {
              if (nest == 0) {
                break;
              }
              nest--;
              sb.append(c);
              continue;
            }
            if (c == ',' && nest == 0) {
              if (sub == qsN) {
                break;
              }
              sub++;
              sb.setLength(0);
              continue;
            }
            sb.append(c);
          }
          if (sub != qsN) {
            return MValue.ofString("");
          }
          String s = sb.toString();
          if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
          }
          return MValue.ofString(s);
        }
      case "$O":
      case "$ORDER":
        {
          String key =
              ev.lastRef != null ? ev.lastRef : (args.isEmpty() ? "" : args.get(0).asStr());
          double dir = args.size() > 1 ? args.get(1).asNum() : 1;
          return MValue.ofString(ev.env.order(key, dir));
        }
      case "$NEXT":
      case "$N":
        {
          // ISO $NEXT — forward $ORDER only (C86/C87)
          String key =
              ev.lastRef != null ? ev.lastRef : (args.isEmpty() ? "" : args.get(0).asStr());
          return MValue.ofString(ev.env.order(key, 1));
        }
      case "$Q":
      case "$QUERY":
        {
          // $Q(ref) / $Q("") — argument is a closed ref or empty start
          String key = "";
          if (!args.isEmpty()) {
            key = args.get(0).asStr();
          } else if (ev.lastRef != null) {
            key = ev.lastRef;
          }
          int dir = args.size() > 1 ? (int) args.get(1).asNum() : 1;
          return MValue.ofString(ev.env.query(key, dir));
        }
      case "$X":
        return MValue.ofNumber(ev.vx);
      case "$Y":
        return MValue.ofNumber(ev.vy);
      // W8 specials
      case "$IO":
        return MValue.ofNumber(0);
      case "$PRINCIPAL":
        return MValue.ofNumber(0);
      case "$DEVICE":
        return MValue.ofString("");
      case "$KEY":
        return MValue.ofString("");
      case "$REFERENCE":
        return MValue.ofString(ev.lastRef == null ? "" : ev.lastRef);
      case "$ESTACK":
        return MValue.ofNumber(0);
      case "$STACK":
      case "$ST":
      case "$STACKLEVEL":
        {
          // C69/C70/C72 — direct-mode stack frame
          if (args.isEmpty()) {
            // Bare $STACK returns stack level number (ISO 8.1.2)
            return MValue.ofNumber(0);
          }
          double lev = args.get(0).asNum();
          String what =
              args.size() > 1 ? args.get(1).asStr().toUpperCase(java.util.Locale.ROOT) : "";
          if (what.equals("PLACE")) {
            return MValue.ofString(lev == 0 ? "@" : "");
          }
          if (lev == 0) {
            return MValue.ofString("RUN");
          }
          if (lev == -1) {
            return MValue.ofString("0");
          }
          return MValue.ofString("");
        }
      case "$ECODE":
        return MValue.ofString(ev.ecode);
      case "$ETRAP":
        return MValue.ofString(ev.etrap);
      case "$ZTRAP":
        return MValue.ofString(ev.ztrap);
      case "$ZVERSION":
        return MValue.ofString("JvMumps");
      case "$ZJOB":
        return MValue.ofNumber(1);
      case "$ZLEVEL":
        return MValue.ofNumber(0);
      case "$ZSYSTEM":
        return MValue.ofString("JvMumps");
      case "$ZEOF":
        return MValue.ofNumber(0);
      case "$STORAGE":
        return MValue.ofNumber(1000000);
      case "$SYSTEM":
        return MValue.ofString("JvMumps");
      case "$TLEVEL":
        return MValue.ofNumber(0);
      case "$TRESTART":
        return MValue.ofNumber(0);
      case "$I":
      case "$INCREMENT":
        {
          if (args.isEmpty()) {
            return MValue.ofNumber(0);
          }
          String ivar = args.get(0).asStr();
          double inc = args.size() > 1 ? args.get(1).asNum() : 1;
          MValue cur;
          try {
            cur = ev.env.get(ivar);
          } catch (MError e) {
            cur = MValue.ofNumber(0);
          }
          double next = cur.asNum() + inc;
          MValue nv = MValue.ofNumber(next);
          ev.env.set(ivar, nv);
          return nv;
        }
      case "$FN":
      case "$FNUMBER":
        {
          double fnum = args.get(0).asNum();
          String fmt = args.size() > 1 ? args.get(1).asStr() : "";
          int dec = args.size() > 2 ? (int) args.get(2).asNum() : -1;
          return MValue.ofString(ExprEval.fmtNumber(fnum, fmt, dec));
        }
      case "$R":
      case "$RANDOM":
        {
          int rn = (int) args.get(0).asNum();
          if (rn <= 0) {
            return MValue.ofNumber(0);
          }
          return MValue.ofNumber(java.util.concurrent.ThreadLocalRandom.current().nextInt(rn));
        }
      case "$S":
      case "$SELECT":
        {
          // args come as pairs via raw — evaluate in order
          for (int k = 0; k + 1 < args.size(); k += 2) {
            if (ExprEval.truthy(args.get(k))) {
              return args.get(k + 1);
            }
          }
          throw new MError("No true condition in $SELECT");
        }
      default:
        return MValue.EMPTY;
    }
  }
}
