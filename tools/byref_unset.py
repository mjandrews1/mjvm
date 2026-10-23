#!/usr/bin/env python3
"""Find FileMan byref actuals (.var) that are never set before the call.

Reads the index built by tools/load_fileman_index.py (call_arg.byref rows)
plus the corpus source, and answers: which pass-by-reference actuals are
undefined at the call site in their own routine — i.e. true output-only
parameters (or latent undefined-variable bugs).

Event model per name, in source order (line, col):
  SET   assignment (S x=, S x(, S (x,y)=, S $P(x,…)=, S $E(x,…)=, F x=)
  NEW   N x          → undefines x
  KILL  K x          → undefines x
A name is UNSET at a call when its last event before the call is not SET.

Env:
  FILEMAN_CORPUS  corpus root (default $HOME/_fileman)
  FILEMAN_INDEX   sqlite file  (default $FILEMAN_CORPUS/fileman-index.sqlite)

Usage:
  tools/byref_unset.py [--limit N] [--samples N] [--json]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

NAME = r"[A-Za-z%][A-Za-z0-9%]*"
# Tag at column 0: NAME, then space / ( / EOL. Not a command word.
RE_TAG = re.compile(r"^([A-Za-z%][A-Za-z0-9%]*)(\(([^)]*)\))?(?=[ \t]|$)")
CMD_WORDS = {
    "SET", "IF", "ELSE", "FOR", "DO", "GOTO", "QUIT", "WRITE", "READ", "KILL",
    "NEW", "MERGE", "HALT", "HANG", "LOCK", "UNLOCK", "OPEN", "CLOSE", "USE",
    "VIEW", "XECUTE", "JOB", "TSTART", "TCOMMIT", "TROLLBACK", "BREAK",
}
# Dotted-block lines (.N Y / .S X=1) — allow leading . depth before a command.
DOTS = r"\.*[ \t]*"
RE_SET_LEAD = re.compile(rf"(?:^|[ \t:]){DOTS}(?:S|SET)(?::[^ \t]*)?[ \t]+")
RE_SET_NAME = re.compile(rf"({NAME})(?:\(([^)]*)\))?[ \t]*=")
RE_SET_GROUP = re.compile(rf"\(([^)]*)\)[ \t]*=")
RE_SET_INDIR = re.compile(rf"\$[A-Z]+[ \t]*\([ \t]*({NAME})")
RE_FOR = re.compile(rf"(?:^|[ \t:]){DOTS}(?:F|FOR)(?::[^ \t]*)?[ \t]+({NAME})[ \t]*[=(]")
RE_NEW = re.compile(rf"(?:^|[ \t:]){DOTS}(?:N|NEW)(?::[^ \t]*)?[ \t]+(.+)$")
RE_KILL = re.compile(rf"(?:^|[ \t:]){DOTS}(?:K|KILL)(?::[^ \t]*)?[ \t]+(.+)$")
# R / READ assigns to its target (device input) — `R %XX`, `R REC#255`, `R X:5`
RE_READ = re.compile(rf"(?:^|[ \t:]){DOTS}(?:R|READ)(?::[^ \t]*)?[ \t]+(.+)$")
# M / MERGE assigns its left-hand name (possibly subscripted): `M A=B`, `M A(1)=B`.
# Capture only the lhs before `=` so a line with several M commands
# (`M I=DIC N DIC M DIC=I K I`) yields every written name — a trailing
# `(.+)$` would swallow the rest of the line and hide the later merges.
RE_MERGE = re.compile(
    rf"(?:^|[ \t:.]){DOTS}(?:M|MERGE)(?::[^ \t]*)?[ \t]+({NAME}(?:\([^)]*\))?)\s*="
)
RE_NAME = re.compile(rf"{NAME}")


def strip_comment(line: str) -> str:
    """Drop an M `;` comment, honoring double-quoted strings."""
    out, in_str, i = [], False, 0
    while i < len(line):
        c = line[i]
        if in_str:
            out.append(c)
            if c == '"':
                if i + 1 < len(line) and line[i + 1] == '"':
                    out.append(line[i + 1])
                    i += 2
                    continue
                in_str = False
        elif c == '"':
            in_str = True
            out.append(c)
        elif c == ";":
            break
        else:
            out.append(c)
        i += 1
    return "".join(out)


def split_commas(s: str):
    """Split on top-level commas (quote- and paren-aware)."""
    return [t for _off, t in split_commas_pos(s)]


def split_commas_pos(s: str):
    """Like split_commas but yields (offset_in_s, text)."""
    out, buf, depth, in_str, start = [], [], 0, False, 0
    for j, c in enumerate(s):
        if in_str:
            buf.append(c)
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            buf.append(c)
        elif c == "(":
            depth += 1
            buf.append(c)
        elif c == ")":
            depth -= 1
            buf.append(c)
        elif c == "," and depth == 0:
            out.append((start, "".join(buf).strip()))
            buf = []
            start = j + 1
        else:
            buf.append(c)
    tail = "".join(buf).strip()
    if tail:
        out.append((start, tail))
    return out


def name_of(expr: str):
    """Leading bare name of an lvalue (drops subscripts / @indirection)."""
    e = expr.strip()
    if e.startswith("@"):
        return None  # indirection — unknown name
    m = re.match(rf"({NAME})", e)
    return m.group(1) if m else None


def name_col(part: str, base: int):
    """Column of the leading name inside part (absolute = base + local)."""
    m = re.match(rf"\s*({NAME})", part)
    return base + m.start(1) if m else base


def _skip_parens(s: str, i: int) -> int:
    """Index just past the balanced paren group opening at s[i] == '('."""
    depth, in_str = 0, False
    while i < len(s):
        c = s[i]
        if in_str:
            if c == '"':
                if i + 1 < len(s) and s[i + 1] == '"':
                    i += 2
                    continue
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return len(s)


def set_targets(part: str):
    """Names assigned by one SET comma-part: `NAME(sub)=`, `(a,b(1))=`,
    `$P(NAME,…)=`. Yields (offset_of_name, name).

    Balanced-paren aware — `S X(+^DD(F,0))=""` and `S X($P(N,U,2))=…` write X,
    which a `[^)]*` subscript regex misses.
    """
    p = part
    # group set  (a,b(1),c)=
    if p.startswith("("):
        inner_end = _skip_parens(p, 0)
        rest = p[inner_end:]
        if rest.lstrip().startswith("="):
            for off, g in split_commas_pos(p[1:inner_end - 1]):
                nm = name_of(g)
                if nm:
                    # offset within p: 1 (past the '(') + part offset + name col in g
                    yield 1 + off + name_col(g, 0), nm
            return
    # $P(NAME,…)= / $E(NAME,…)= / $NA(NAME)=  — writes NAME
    im = RE_SET_INDIR.match(p)
    if im:
        yield im.start(1), im.group(1)
        return
    # NAME(sub-with-nested-parens)=  or  NAME=
    m = re.match(rf"({NAME})", p)
    if not m:
        return
    name = m.group(1)
    k = m.end()
    if k < len(p) and p[k] == "(":
        k = _skip_parens(p, k)
    rest = p[k:]
    if rest.lstrip().startswith("="):
        yield m.start(1), name


def events_for_code(code: str):
    """Return (col, kind, name) assignment events, sorted by column.

    kind: SET | NEW | KILL. col is the column of the *name*, so read/write
    classification can use exact positions. Handles group set as any
    comma-part of a SET (S A=1,(B,C)=2), and $P(NAME,…)= as a write of NAME.
    """
    ev = []
    for m in RE_FOR.finditer(code):
        ev.append((m.start(1), "SET", m.group(1)))
    for m in RE_SET_LEAD.finditer(code):
        j = m.end()
        for off, part in split_commas_pos(code[j:]):
            base = j + off
            if not part:
                continue
            for rel, nm in set_targets(part):
                ev.append((base + rel, "SET", nm))
    for m in RE_NEW.finditer(code):
        for off, part in split_commas_pos(m.group(1)):
            nm = name_of(part)
            if nm:
                ev.append((name_col(part, m.start(1) + off), "NEW", nm))
    for m in RE_KILL.finditer(code):
        for off, part in split_commas_pos(m.group(1)):
            nm = name_of(part)
            if nm:
                ev.append((name_col(part, m.start(1) + off), "KILL", nm))
    # R / READ assigns device input to its target: `R %XX`, `R REC#255`, `R X:5`
    for m in RE_READ.finditer(code):
        for off, part in split_commas_pos(m.group(1)):
            nm = name_of(part)  # leading name; strips #len / :timeout
            if nm:
                ev.append((name_col(part, m.start(1) + off), "SET", nm))
    # M / MERGE writes its left-hand side: `M A=B`, `M A(1)=B`, `M A=^G`
    for m in RE_MERGE.finditer(code):
        nm = name_of(m.group(1))
        if nm:
            ev.append((m.start(1), "SET", nm))
    # .name inside an arg list (after ( or ,) is a byref pass. It is a *potential*
    # write — only a real write if the callee assigns. Marked BYREF so
    # ever_written() can resolve before counting it; occs_of maps it to WRITE
    # for read_before_write (conservative: the callee *may* write).
    # Not the dotted-block depth marker (".K DA", ".S X=1"): those have no ( or , before.
    for m in re.finditer(rf"[(,][ \t]*\.({NAME})", code):
        ev.append((m.start(1), "BYREF", m.group(1)))
    ev.sort(key=lambda e: e[0])
    return ev


def events_for_line(ln: str, lineno: int):
    """(line, col, kind, name) events on one source line, sorted by column."""
    return [(lineno, c, k, n) for (c, k, n) in events_for_code(strip_comment(ln))]


def call_col(line: str, target: str, src: str):
    """Best-effort column of the arg list for this call on the line."""
    i = 0
    if target.startswith("$$"):
        i = line.find(target)
    else:
        i = line.find(target)
        if i < 0 and target:
            i = line.find(target.split("^")[0])
    if i < 0:
        return 0
    j = line.find("(", i)
    if j < 0:
        return i
    k = line.find(src, j)
    return k if k >= 0 else j


# ---- callee-side read-before-write -----------------------------------

# Word boundary matters: `$P(` is the PIECE intrinsic, not a use of variable P.
RE_WORD = re.compile(rf"(?<![A-Za-z0-9%$]){NAME}")
# name used as an argument to a defined-ness / undefined-safe intrinsic
# $G/$D/$O/$ORDER/$NA/$NAME/$Q/$QUERY all tolerate an undefined target.
RE_GUARDED = re.compile(
    r"\$(?:G|D|O|ORDER|NA|NAME|Q|QUERY)[ \t]*\([ \t]*$"
)
# single-letter names that are also command words — only a "use" outside
# a command slot (start of line / after dots / after ':')
CMD_ONE = set("ABCDEFGHIJKLMNOPQRSTUVWXYZ%")


def is_cmd_pos(code: str, c: int) -> bool:
    """True when col c is a command name (line start / after block dots).

    `:` is NOT a command slot — in `S:Y="" Y="TO"` the `:` is a postcondition,
    so the `Y` after it is an expression, not a command.
    """
    i = c - 1
    while i >= 0 and code[i] in ". \t":
        i -= 1
    return i < 0


RE_DO_TARGET = re.compile(
    rf"(?:^|[ \t:.])(?:D|DO|G|GOTO)(?::[^ \t]*)?[ \t]+(\$\$)?({NAME})"
)
RE_EXTRINSIC_TGT = re.compile(rf"\$\$({NAME})")


def do_target_cols(code: str):
    """Columns of DO / GOTO / $$ target names (not variable uses).

    `D DA(RIENS,.DA)` — the first `DA` is the tag being called; the `.DA`
    after it is the byref actual. Reading the tag as a variable is a
    false positive.
    """
    cols = set()
    for m in RE_DO_TARGET.finditer(code):
        cols.add(m.start(2))
    for m in RE_EXTRINSIC_TGT.finditer(code):
        cols.add(m.start(1))
    return cols


def is_cmd_name(code: str, c: int, end: int) -> bool:
    """True when the name at c looks like a command/label name, not a var.

    Command names are followed by whitespace plus an argument/next-command
    (`S F=1`, `K DTOUT`, `N E1,E2`, `S (D,F,L,M)=""`). A trailing `=` / operator
    means a variable use (`Y=""`).

    Bare end-of-line: at a command slot it is a label/command (`DO ; comment`
    strips to `DO `, and `X` alone is a label) — skip it. In an argument
    position it is a trailing variable read (`W X`) — keep it.
    """
    j = end
    while j < len(code) and code[j] in " \t":
        j += 1
    if j >= len(code):
        return is_cmd_pos(code, c)
    nxt = code[j]
    # `S (D,F,L,M)=""` — command + group set, not a var with subscripts
    if nxt == "(" and is_cmd_pos(code, c):
        return True
    if nxt in "=(),+-*/\\#!'<>[]":
        return False
    if is_cmd_pos(code, c):
        return True
    # mid-line: only if preceded by whitespace
    return c > 0 and code[c - 1] in " \t"


def split_target(target: str):
    """'$$UP^XLFSTR' → ('XLFSTR','UP'); 'INIT' → (None,'INIT'); '^DIC0' → ('DIC0',None)."""
    t = target[2:] if target.startswith("$$") else target
    if "^" in t:
        tag, rtn = t.split("^", 1)
        return (rtn or None), (tag or None)
    return None, (t or None)


def callee_labels(path: Path):
    """Map tag (upper) → (line_no, formals_list, body_lines[(line,text)])."""
    if not path.is_file():
        return {}
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    out = {}
    starts = []
    for n, ln in enumerate(lines, 1):
        m = RE_TAG.match(ln)
        if m and m.group(1).upper() not in CMD_WORDS:
            formals = [p.strip() for p in m.group(3).split(",")] if m.group(3) else []
            starts.append((n, m.group(1).upper(), formals))
    for i, (n, tag, formals) in enumerate(starts):
        end = starts[i + 1][0] - 1 if i + 1 < len(starts) else len(lines)
        body = [(k + 1, lines[k]) for k in range(n, end)]
        out[tag] = (n, formals, body)
    return out


def string_spans(code: str):
    """(start, end) of double-quoted string literals in code."""
    spans, i, n = [], 0, len(code)
    while i < n:
        if code[i] == '"':
            j = i + 1
            while j < n:
                if code[j] == '"':
                    if j + 1 < n and code[j + 1] == '"':
                        j += 2
                        continue
                    break
                j += 1
            spans.append((i, min(j + 1, n)))
            i = j + 1
        else:
            i += 1
    return spans


def in_string(code: str, col: int, spans) -> bool:
    return any(a <= col < b for a, b in spans)


def occs_of(code: str, name: str):
    """Classify every use of name on a line: WRITE / UNDEF / READ_SAFE / READ_RAW.

    WRITE is a real assignment (SET / MERGE / READ / byref). NEW and KILL
    *undefine* the name — `N X` then `W X` is an UNDEF, not safe — so they
    get their own UNDEF kind.

    Skips string literals (DIR("X") is not a use of X). A $G(NAME)/$D(NAME)
    guard earlier on the line shields later reads on that line (FileMan idiom
    `I $G(X)]"" … S Y=X`).
    """
    evs = events_for_code(code)
    set_cols = {c for (c, k, n) in evs
                if n.upper() == name.upper() and k in ("SET", "BYREF")}
    undef_cols = {c for (c, k, n) in evs
                  if n.upper() == name.upper() and k in ("NEW", "KILL")}
    spans = string_spans(code)
    tgts = do_target_cols(code)
    guarded_at = None
    for m in re.finditer(rf"\$(?:G|D)[ \t]*\([ \t]*({NAME})", code):
        if m.group(1).upper() == name.upper() and guarded_at is None:
            guarded_at = m.start(1)
    out = []
    for m in RE_WORD.finditer(code):
        if m.group(0).upper() != name.upper():
            continue
        c = m.start()
        if in_string(code, c, spans):
            continue
        # writes win over command-name skipping (S:Y="" Y="TO" — the second Y)
        if c in set_cols:
            out.append((c, "WRITE"))
            continue
        if c in undef_cols:
            out.append((c, "UNDEF"))
            continue
        # DO/GOTO/$$ target names are not variable uses (D DA(RIENS,.DA))
        if c in tgts:
            continue
        # command words at a command slot are not variable uses — including
        # multi-letter ones (`DO ; GET FILE ATTR` is a label, not a read of DO)
        if m.group(0).upper() in CMD_WORDS and is_cmd_name(code, c, m.end()):
            continue
        if RE_GUARDED.search(code[:c]) or (guarded_at is not None and c > guarded_at):
            out.append((c, "READ_SAFE"))
        else:
            out.append((c, "READ_RAW"))
    out.sort()
    return out


def read_before_write(body, name):
    """First unguarded read of name before any write. Returns (line, col, snippet, maybe_nested)
    or None. maybe_nested=True when a DO/XECUTE earlier in the body may have
    written name via side effect (we cannot see into the callee).

    A `$D(NAME`/`$G(NAME` guard on an *earlier* line shields later reads —
    `Q:'$D(DA(1))` at the top of a body is the FileMan existence-check idiom.
    """
    saw_do = False
    guarded = False   # an existence test on an *earlier* line
    for n, text in body:
        code = strip_comment(text)
        if re.search(r"(?:^|[ \t:])(?:D|DO|X|XECUTE)(?:[: \t]|$)", code):
            # only count DOs that precede the read on this or earlier lines
            if not any(k == "READ_RAW" for _c, k in occs_of(code, name)):
                saw_do = True
        gcol = name_guard_col(code, name)
        for col, kind in occs_of(code, name):
            if kind == "READ_RAW":
                # shielded by an earlier line's guard, or by a guard earlier
                # on this line (`$D(X)` then `X(1)`); not by a later one
                if guarded or (gcol is not None and gcol < col):
                    continue
                return n, col, text.strip()[:100], saw_do
            if kind == "WRITE":
                return None  # assigned before any raw read — safe
            # UNDEF (N / K) does not make a later read safe; keep scanning
        if gcol is not None:
            guarded = True
    return None


def name_guarded(code: str, name: str) -> bool:
    """True when code tests `NAME` / `NAME(sub)` for existence ($D or $G)."""
    return name_guard_col(code, name) is not None


def name_guard_col(code: str, name: str):
    """Column of the first `$D(NAME`/`$G(NAME` existence test, or None.

    Only a guard *before* a read can shield it — `S (...)=Y S:$D(Y(0)) …`
    reads Y first and tests Y(0) later on the same line.
    """
    up = name.upper()
    for m in re.finditer(rf"\$(?:G|D)[ \t]*\([ \t]*({NAME})(\([^)]*\))?", code):
        if m.group(1).upper() == up:
            return m.start(1)
    return None


def used_as_array(body, name):
    """True when the byref formal is used *as an array* — subscripted at least
    once (`DA(1)`, `$D(DA(`, `$O(DA(`). This is what the `.var` byref is for:
    M passes an array by reference, not by value.

    Says nothing about whether the callee writes it — see `ever_written` to
    split input-only arrays from in/out arrays.
    """
    up = name.upper()
    for n, text in body:
        code = strip_comment(text)
        spans = string_spans(code)
        for m in re.finditer(rf"(?<![A-Za-z0-9%]){re.escape(name)}\s*\(", code, re.I):
            if in_string(code, m.start(), spans):
                continue
            if m.group(0).split("(")[0].strip().upper() == up:
                return True
    return False


def guarded_before_write(body, name):
    """True if name is only seen via $G/$D before its first write."""
    for n, text in body:
        code = strip_comment(text)
        for col, kind in occs_of(code, name):
            if kind == "READ_RAW":
                return False
            if kind == "WRITE":
                return True
    return True  # never used at all in body — callee ignores it


def ever_written(body, name, root=None, db=None, cache=None, self_path=None):
    """True when the callee *assigns* `name` anywhere in its body.

    A byref pass (`D FOO(.X)`) is only a write when `FOO` itself assigns `X` —
    forwarding to a read-only callee must not flip the call to read-write.
    Without a resolver a byref pass is conservatively treated as a write.
    """
    for n, text in body:
        code = strip_comment(text)
        for _c, kind, nm in events_for_code(code):
            if nm.upper() != name.upper():
                continue
            if kind == "SET":
                return True
            if kind == "BYREF" and root is not None:
                if byref_assigns([(n, text)], name, root, db, cache, self_path):
                    return True
    return False


def byref_assigns(body, name, root, db, cache, self_path):
    """True when a `.name` byref pass on one of these lines reaches a callee
    (depth-capped) that assigns `name`."""
    for n, text in body:
        code = strip_comment(text)
        has_byref = any(k == "BYREF" and nm.upper() == name.upper()
                        for _c, k, nm in events_for_code(code))
        if not has_byref:
            continue
        for tag, rtn in do_targets_in([(n, text)]):
            sub = resolve_body(root, db, rtn, tag, cache, self_path)
            if sub and writes_name(sub, name, root, db, cache, self_path):
                return True
    return False


# Classic FileMan entry points write these names regardless of arg lists.
# Keeps `S DIC=… D ^DIC` / `D ^DIR` from reading like read-before-write.
KNOWN_OUTPUTS = {
    "DIC": ["Y", "DIC", "DTOUT", "DUOUT", "DIRUT", "DIROUT"],
    "DIR": ["Y", "DIR", "DTOUT", "DUOUT", "DIRUT", "DIROUT"],
    "DIE": ["DA", "DTOUT", "DUOUT", "DIROUT", "DR"],
    "DIK": ["DA", "DIK"],
    "DIPZ": ["Y", "DTOUT", "DUOUT"],
}

# Cross-routine shared state: set once at subsystem entry, read in callees.
# Same-routine analysis reports last=NONE correctly but cannot see the
# initializer (it is not in the call tree). Allowlist by name.
SHARED_STATE = {
    # DDW screen editor: initialized in DDW.m:65 `S DDWOFS="0^20^^1",…`
    "DDWOFS": "DDW.m",
    # FileMan lookup-index descriptor: written in 20+ DIC* sites
    # (DIC5/DICF/DICF0/DICF2/DICL/DICUIX), threaded through the lookup driver.
    "DINDEX": "DIC* lookup driver",
    # FileMan file number: set by the lookup driver (GETFILE / FLDVAL),
    # read throughout DIC*/DIE* as the current file.
    "DIFILEI": "DIC* lookup driver",
    # FileMan record number (IEN / IEN stack): set by the lookup driver
    # (^DIC / ^DIE / the DDS screen driver), read everywhere.
    "DA": "DIC*/DDS lookup driver",
    # File spec (global ref or file number): set by the lookup driver,
    # read as the current file in DIC*/DIE*.
    "DIC": "DIC* lookup driver",
    # Resolved file number: set by the lookup driver alongside DIFILEI.
    "DIFILE": "DIC* lookup driver",
}


def apply_known_outputs(body, name):
    """True if some line does `D ^X`/`D ^X…` whose known outputs include name."""
    up = name.upper()
    for _n, text in body:
        code = strip_comment(text)
        for m in re.finditer(r"\^(%?[A-Za-z][A-Za-z0-9%]*)", code):
            outs = KNOWN_OUTPUTS.get(m.group(1).upper())
            if outs and up in outs:
                return True
    return False


# ---- interprocedural write trace (resolves the `maybe` bucket) --------

RE_DO_CMD = re.compile(rf"(?:^|[ \t:]){DOTS}(?:D|DO)(?::[^ \t]*)?[ \t]+(.+)$")
RE_XE_CMD = re.compile(rf"(?:^|[ \t:]){DOTS}(?:X|XECUTE)(?:[: \t]|$)")


def do_targets_in(body, upto_line=None):
    """Yield (tag, rtn) for every DO target in body (up to and incl. upto_line).

    Handles comma-lists: `D INIT,^DIR01,END,PAINT`. XECUTE yields (None, None).
    """
    for n, text in body:
        if upto_line is not None and n > upto_line:
            break
        code = strip_comment(text)
        if RE_XE_CMD.search(code):
            yield None, None  # unknown — XECUTE target is data
            continue
        for m in RE_DO_CMD.finditer(code):
            rest = m.group(1)
            # cut the arg list and any postcond already handled; split on commas
            # at depth 0 that separate *targets* (D A,B(1),C^R)
            for part in split_commas(rest):
                # cut at "(" and at the next command word: `D FLDVAL G:…` is
                # one DO plus a GOTO, not a target named "FLDVAL G:…"
                t = part.split("(")[0].split()[0].strip() if part.split("(")[0].split() else ""
                if not t or t.startswith("."):
                    continue
                if t.startswith("$$"):
                    t = t[2:]
                if "^" in t:
                    tag, rtn = t.split("^", 1)
                    yield (tag or None), (rtn or None)
                else:
                    yield t, None


def resolve_body(root, db, rtn, tag, cache, self_path=None):
    """Body lines [(line,text)] of `tag^rtn` (or same-routine tag). None if unknown."""
    if rtn:
        key = rtn.upper()
        if key not in cache:
            p = find_path(db, rtn)
            cache[key] = callee_labels(root / p) if p else {}
        labs = cache[key]
    else:
        if not self_path:
            return None
        if self_path not in cache:
            cache[self_path] = callee_labels(root / self_path)
        labs = cache[self_path]
    if not tag:
        return None
    info = labs.get(tag.upper())
    return info[2] if info else None


def writes_name(body, name, root, db, cache, self_path=None, depth=0, seen=None):
    """True if body (or a callee it reaches) writes `name` before any read.

    Depth-capped at 3. Same-routine DO targets resolve through self_path.
    """
    if seen is None:
        seen = set()
    if depth > 3 or body is None:
        return False
    for n, text in body:
        code = strip_comment(text)
        evs = events_for_code(code)
        set_cols = {c for c, k, nm in evs if nm.upper() == name.upper() and k == "SET"}
        for col, kind in occs_of(code, name):
            if kind == "WRITE":
                # real assignment wins; a BYREF pass falls through to the
                # DO-target follow below
                if col in set_cols:
                    return True
                continue
            if kind == "READ_RAW":
                return False
        # no definitive use of name on this line — follow DO targets it makes
        for tag, rtn in do_targets_in([(n, text)]):
            sig = (rtn, tag, name.upper())
            if sig in seen:
                continue
            seen.add(sig)
            sub = resolve_body(root, db, rtn, tag, cache, self_path)
            if sub and writes_name(sub, name, root, db, cache, self_path, depth + 1, seen):
                return True
    return False


def classify_rbw(row, body, formal, root, db, label_cache, rbw_rows, self_path=None):
    """Decide read-before-write for one call. Resolves the `maybe` bucket by
    tracing DO targets (depth-capped) to see whether they write the formal."""
    rbw = read_before_write(body, formal)
    if not rbw:
        # read_before_write returns None when the formal is written first or
        # every raw read is existence-guarded. Both are safe — do not flag.
        # "callee never assigns" is already visible via row["callee_writes"].
        return
    line, _col, snippet, saw_do = rbw
    # Only reached when there IS a read-before-write, so shared-state and
    # array-in buckets hold real reads — not merely an allowlisted name.
    if formal.upper() in SHARED_STATE:
        # Split by whether the callee also mutates the shared state:
        # read-only consumer vs in/out mutator.
        mutates = ever_written(body, formal, root, db, label_cache, self_path)
        kind = "shared-state-rw" if mutates else "shared-state-ro"
        note = "mutates" if mutates else "read-only"
        row["rbw"] = {"line": line,
                      "snippet": "(shared-state " + note + " in "
                                 + SHARED_STATE[formal.upper()] + ": " + snippet + ")",
                      "status": kind}
        rbw_rows.append((row, (line, 0, snippet, False)))
        return
    # `.DA` byref hands over an array (DA, DA(1), DA(2)…) — the M array-passing
    # idiom. Distinct from shared state: the caller owns the data. Split by
    # whether the callee mutates the array (in/out) or only reads it (input).
    # Only reached when there *is* a read-before-write, so safe (write-first)
    # arrays stay in "callee safe".
    if used_as_array(body, formal):
        mutates = ever_written(body, formal, root, db, label_cache, self_path)
        kind = "array-in-rw" if mutates else "array-in-ro"
        note = "mutates" if mutates else "read-only"
        row["rbw"] = {"line": line,
                      "snippet": "(byref array " + note + " " + formal + ": " + snippet + ")",
                      "status": kind}
        rbw_rows.append((row, (line, 0, snippet, False)))
        return
    if apply_known_outputs(body, formal):
        row["rbw"] = {"line": line, "snippet": "(known-output: " + snippet + ")",
                      "status": "known-output"}
        rbw_rows.append((row, (line, 0, snippet, False)))
        return
    if not saw_do:
        row["rbw"] = {"line": line, "snippet": snippet, "status": "conclusive"}
        rbw_rows.append((row, rbw))
        return
    # nested DO/XECUTE precede the read — trace whether they write the formal
    upto = [(n, t) for (n, t) in body if n <= line]
    if writes_name(upto, formal, root, db, label_cache, self_path):
        row["rbw"] = {"line": line, "snippet": "(written by nested DO: " + snippet + ")",
                      "status": "resolved"}
        rbw_rows.append((row, (line, 0, snippet, False)))
    else:
        row["rbw"] = {"line": line, "snippet": snippet, "status": "conclusive"}
        rbw_rows.append((row, (line, 0, snippet, False)))


def enclosing_formals(lines, line_no, path_labels):
    """Formals of the tag that contains line_no (bound at entry → pre-set)."""
    best = None
    for tag, (n, formals, _body) in path_labels.items():
        if n <= line_no and (best is None or n > best[0]):
            best = (n, tag, formals)
    return best[2] if best else []


def analyze(db: sqlite3.Connection, root: Path, limit: int):
    """Return (rows, summary). rows are UNSET byref actuals."""
    rows = []
    total_byref = 0
    unset_by_target: dict[str, int] = {}
    unset_by_name: dict[str, int] = {}
    by_routine: dict[str, int] = {}
    rbw_rows = []

    calls = list(
        db.execute(
            """
            SELECT r.name, r.path, c.line, c.kind, c.target, a.pos, a.src, a.byref_name
              FROM call_arg a JOIN call c ON c.id=a.call JOIN routine r ON r.id=c.routine
             WHERE a.byref=1
             ORDER BY r.name, c.line, a.pos
            """
        )
    )
    if limit:
        calls = calls[:limit]

    cache: dict[str, list] = {}
    label_cache: dict[str, dict] = {}
    for rname, path, line, kind, target, pos, src, bname in calls:
        total_byref += 1
        if path not in cache:
            p = root / path
            text = p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""
            evs = []
            for n, ln in enumerate(text.splitlines(), 1):
                evs.extend(events_for_line(ln, n))
            evs.sort(key=lambda e: (e[0], e[1]))
            cache[path] = (text.splitlines(), evs)
        lines, evs = cache[path]
        col = call_col(lines[line - 1], target, src) if line <= len(lines) else 0

        # formals of the *caller's* enclosing tag are bound at entry → pre-set
        if path not in label_cache:
            label_cache[path] = callee_labels(root / path)
        bound = {f.upper() for f in enclosing_formals(lines, line, label_cache[path])}

        last = None
        for (el, ec, ek, en) in evs:
            if en.upper() != bname.upper():
                continue
            if (el, ec) < (line, col):
                last = ek
            else:
                break
        # BYREF is a potential set on the caller side too — an earlier
        # `D FOO(.X)` may have filled X.
        if last in ("SET", "BYREF") or bname.upper() in bound:
            continue
        # A nested DO before the call may set the actual (FLDVAL sets DIEV0
        # before AUXVAL reads it). Direct SET/NEW/KILL scanning cannot see
        # through a call — trace the caller's DO targets up to the call line.
        if last != "SET":
            upto_call = [(n, t) for (n, t) in enumerate(lines, 1) if n < line]
            if upto_call and writes_name(upto_call, bname, root, db, label_cache, self_path=path):
                continue
        row = {
            "routine": rname,
            "path": path,
            "line": line,
            "kind": kind,
            "target": target,
            "pos": pos,
            "src": src,
            "name": bname,
            "last_event": last or "NONE",
            "formal": None,
            "callee_writes": None,
            "rbw": None,
        }
        # ---- callee-side: does the formal get read before it is written? ----
        rtn, tag = split_target(target)
        if rtn:
            key = rtn.upper()
            if key not in label_cache:
                label_cache[key] = callee_labels(root / (find_path(db, rtn) or ""))
            labs = label_cache[key]
            info = labs.get((tag or "").upper()) if tag else None
        else:
            # same-routine call: use this routine's own labels
            key = path
            if key not in label_cache:
                label_cache[key] = callee_labels(root / path)
            labs = label_cache[key]
            info = labs.get((tag or "").upper()) if tag else None
        if info:
            _ln, formals, body = info
            if formals and 1 <= pos <= len(formals):
                formal = formals[pos - 1]
                row["formal"] = formal
                if formal:
                    # Does the callee assign the formal at all? Splits the
                    # unset-at-call population: output-only / in-out (writes)
                    # vs a read of something the caller never set (read-only).
                    callee_path = find_path(db, rtn) if rtn else path
                    row["callee_writes"] = ever_written(body, formal, root, db, label_cache, callee_path)
                    classify_rbw(row, body, formal, root, db, label_cache, rbw_rows,
                                 self_path=callee_path)
        rows.append(row)
        unset_by_target[target] = unset_by_target.get(target, 0) + 1
        unset_by_name[bname] = unset_by_name.get(bname, 0) + 1
        by_routine[rname] = by_routine.get(rname, 0) + 1

    summary = {
        "total_byref": total_byref,
        "unset": len(rows),
        "by_target": sorted(unset_by_target.items(), key=lambda kv: -kv[1]),
        "by_name": sorted(unset_by_name.items(), key=lambda kv: -kv[1]),
        "by_routine": sorted(by_routine.items(), key=lambda kv: -kv[1]),
        "rbw": len(rbw_rows),
    }
    return rows, summary, rbw_rows


def find_path(db: sqlite3.Connection, routine: str) -> str | None:
    r = db.execute("SELECT path FROM routine WHERE name=? LIMIT 1", (routine.upper(),)).fetchone()
    return r[0] if r else None


def report(rows, summary, samples: int) -> None:
    t, u = summary["total_byref"], summary["unset"]
    pct = (100.0 * u / t) if t else 0.0
    print("## byref actuals never set before the call")
    print(f"   byref args={t}  unset-at-call={u}  ({pct:.1f}%)")
    print("   (last event before call is NEW/KILL/none — output-only or a bug)")
    print("\n### top callees receiving unset actuals")
    for tgt, n in summary["by_target"][:15]:
        print(f"  {n:>5}  {tgt}")
    print("\n### top unset actual names")
    for nm, n in summary["by_name"][:15]:
        print(f"  {n:>5}  .{nm}")
    print("\n### routines with most unset actuals")
    for rn, n in summary["by_routine"][:15]:
        print(f"  {n:>5}  {rn}")
    print(f"\n### samples (up to {samples})")
    for r in rows[:samples]:
        print(
            f"  {r['routine']}:{r['line']}  {r['kind']} {r['target']}({r['src']})"
            f"  last={r['last_event']}"
        )


def report_rbw(rbw_rows, samples: int) -> None:
    concl = [(r, x) for r, x in rbw_rows if r.get("rbw", {}).get("status") == "conclusive"]
    other = [(r, x) for r, x in rbw_rows if r.get("rbw", {}).get("status") != "conclusive"]
    print("\n## callee reads the formal before setting it")
    print(f"   conclusive: {len(concl)}   resolved/known-output: {len(other)}")
    print("   (unset actual + callee uses the formal raw before any write)")
    print(f"\n### conclusive (up to {samples})")
    for (row, rbw) in concl[:samples]:
        print(
            f"\n  {row['routine']}:{row['line']}  {row['kind']} {row['target']}({row['src']})"
            f"  formal={row['formal']}  last={row['last_event']}"
        )
        print(f"     callee:{rbw[0]}  {rbw[2]}")
    if len(concl) > samples:
        print(f"\n  … {len(concl) - samples} more conclusive")
    if other:
        print(f"\n### resolved / known-output — up to {max(0, samples // 2)}")
        for (row, _rbw) in other[: max(0, samples // 2)]:
            st = row.get("rbw", {}).get("status")
            sn = row.get("rbw", {}).get("snippet", "")
            print(f"  [{st}]  {row['routine']}:{row['line']}  {row['target']}({row['src']})  {sn[:70]}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--samples", type=int, default=15)
    ap.add_argument("--rbw", action="store_true", help="only read-before-write section")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    root = Path(os.environ.get("FILEMAN_CORPUS", Path.home() / "_fileman"))
    db_path = Path(os.environ.get("FILEMAN_INDEX", root / "fileman-index.sqlite"))
    if not root.is_dir():
        print(f"no corpus at {root}", file=sys.stderr)
        return 1
    if not db_path.is_file():
        print(f"no index at {db_path} — run tools/load_fileman_index.py first", file=sys.stderr)
        return 1

    db = sqlite3.connect(db_path)
    try:
        rows, summary, rbw_rows = analyze(db, root, args.limit)
    finally:
        db.close()

    if args.json:
        print(json.dumps({"summary": summary, "rows": rows,
                          "rbw": [{"call": r, "at": list(x)} for r, x in rbw_rows]},
                         indent=2))
    elif args.rbw:
        report_rbw(rbw_rows, args.samples)
    else:
        report(rows, summary, args.samples)
        report_rbw(rbw_rows, args.samples)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
