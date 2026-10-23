#!/usr/bin/env python3
"""Build a SQLite + FTS5 index over a FileMan / VistA M routine corpus.

Companion to jvmumps#32 (FileMan Tier A). Derived, disposable index —
does not touch MeshGraph (YottaDB) or tsx (Comdb2).

Env:
  FILEMAN_CORPUS  corpus root (default $HOME/_fileman)
  FILEMAN_INDEX   sqlite file  (default $FILEMAN_CORPUS/fileman-index.sqlite)

Read-only for the corpus. Idempotent: DROP + CREATE on each run.

Usage:
  tools/load_fileman_index.py [--limit N] [--stats]
"""
from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
from pathlib import Path

# ---- line shapes (ZSAVE .m) -------------------------------------------

# Tag at column 0: NAME, then space / ( / EOL. Not a command word.
RE_TAG = re.compile(r"^([A-Za-z%][A-Za-z0-9%]*)(\(([^)]*)\))?(?=[ \t]|$)")
# Commands we never treat as tags
CMD_WORDS = {
    "SET", "IF", "ELSE", "FOR", "DO", "GOTO", "QUIT", "WRITE", "READ", "KILL",
    "NEW", "MERGE", "HALT", "HANG", "LOCK", "UNLOCK", "OPEN", "CLOSE", "USE",
    "VIEW", "XECUTE", "JOB", "TSTART", "TCOMMIT", "TROLLBACK", "BREAK",
}
# DO / GOTO targets: tag, tag^RTN, ^RTN, $$tag, $$tag^RTN
RE_DO = re.compile(r"\bD(?:O)?\s+(\$\$)?([A-Za-z%][A-Za-z0-9%]*)?(\^([A-Za-z%][A-Za-z0-9%]*))?")
RE_GOTO = re.compile(r"\bG(?:OTO)?\s+([A-Za-z%][A-Za-z0-9%]*)?(\^([A-Za-z%][A-Za-z0-9%]*))?")
RE_EXTRINSIC = re.compile(r"\$\$([A-Za-z%][A-Za-z0-9%]*)(\^([A-Za-z%][A-Za-z0-9%]*))?")
RE_INTRINSIC = re.compile(r"\$([A-Za-z%][A-Za-z0-9%]*)")
# Known-gap markers
RE_ZSTAR = re.compile(r"\$Z[A-Z0-9%]*")
RE_XCALL = re.compile(r"\$&[A-Za-z]|X\^|XECUTE\s+@?")
# Pass-by-reference actual: .NAME (not a decimal like .01)
RE_BYREF = re.compile(r"^\.([A-Za-z%][A-Za-z0-9%]*)$")


def read_arglist(line: str, i: int):
    """If line[i] is '(' return (arg_text_inside, end_index_after_rparen).
    Quote- and depth-aware. Else (None, i)."""
    if i >= len(line) or line[i] != "(":
        return None, i
    depth = 0
    in_str = False
    j = i
    while j < len(line):
        c = line[j]
        if in_str:
            if c == '"':
                if j + 1 < len(line) and line[j + 1] == '"':
                    j += 2
                    continue
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return line[i + 1 : j], j + 1
        j += 1
    return None, i


def split_args(argtext: str):
    """Split an M arg list on top-level commas (quote- and depth-aware)."""
    if not argtext.strip():
        return []
    out, buf, depth, in_str = [], [], 0, False
    j = 0
    while j < len(argtext):
        c = argtext[j]
        if in_str:
            buf.append(c)
            if c == '"':
                if j + 1 < len(argtext) and argtext[j + 1] == '"':
                    buf.append(argtext[j + 1])
                    j += 2
                    continue
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
            out.append("".join(buf).strip())
            buf = []
        else:
            buf.append(c)
        j += 1
    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out


def byref_name(arg: str):
    """Return the actual variable name if arg is a pass-by-ref `.NAME`, else None."""
    m = RE_BYREF.match(arg.strip())
    return m.group(1) if m else None


def args_after(line: str, i: int):
    """Split the `(…)` arg list that may follow a call at line[i:]."""
    j = i
    while j < len(line) and line[j] in " \t":
        j += 1
    argtext, _ = read_arglist(line, j)
    return split_args(argtext) if argtext is not None else []


def package_of(rel: str) -> str:
    top = rel.split("/", 1)[0]
    return top if "/" in rel else "root"


def strip_comment(line: str) -> str:
    """Drop an M `;` comment, honoring double-quoted strings.

    A call on a comment-only line is not a call: `;D DF(.ZOSH1) CHANGE TO
    USE $TR` is dead text. Without this, ~3.8% of indexed calls are
    phantom (commented-out code) and pollute the byref analysis.
    """
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


def mask_strings(code: str) -> str:
    """Blank out double-quoted string interiors, preserving length/offsets.

    Code-generators emit M source as string text (`D L(" Q $$UNIQUE^DIE17(.X,.DA,…")`)
    — those are not live calls. Keeping offsets lets the same indexes work
    on the masked text for arg splitting.
    """
    out, in_str, i = [], False, 0
    while i < len(code):
        c = code[i]
        if in_str:
            if c == '"':
                if i + 1 < len(code) and code[i + 1] == '"':
                    out.append("  ")
                    i += 2
                    continue
                in_str = False
                out.append(c)
            else:
                out.append(" ")
        elif c == '"':
            in_str = True
            out.append(c)
        else:
            out.append(c)
        i += 1
    return "".join(out)


def parse_routine(text: str):
    """Return (labels, calls, intrinsics, gaps).

    Each call is (line, kind, target, tag, ext, args) where args is the list
    of raw arg strings from the call site (empty if no paren list).
    Labels come from the raw line (col-0 tag); everything else from the
    comment-stripped code.
    """
    labels, calls, intrinsics, gaps = [], [], [], []
    for n, raw in enumerate(text.splitlines(), 1):
        if not raw:
            continue
        # tag at col 0
        m = RE_TAG.match(raw)
        if m and m.group(1).upper() not in CMD_WORDS:
            formals = m.group(3).strip() if m.group(3) else None
            labels.append((m.group(1), n, formals))
        body = strip_comment(raw)
        if not body.strip():
            continue
        # scan masked text (no string interiors); arg text still read from
        # `body` at the same offsets — mask_strings preserves length
        code = mask_strings(body)
        for mm in RE_EXTRINSIC.finditer(code):
            args = args_after(body, mm.end())
            calls.append((n, "EXTRINSIC", mm.group(0), mm.group(1), mm.group(3), args))
        for mm in RE_DO.finditer(code):
            if mm.group(1):  # $$ handled above
                continue
            tag, ext = mm.group(2), mm.group(4)
            if tag or ext:
                tgt = (tag or "") + ("^" + ext if ext else "")
                args = args_after(body, mm.end())
                calls.append((n, "DO", tgt, tag, ext, args))
        for mm in RE_GOTO.finditer(code):
            tag, ext = mm.group(1), mm.group(3)
            if tag or ext:
                tgt = (tag or "") + ("^" + ext if ext else "")
                calls.append((n, "GOTO", tgt, tag, ext, []))
        for mm in RE_INTRINSIC.finditer(code):
            name = mm.group(1).upper()
            if name.startswith("Z") or name in {"H", "I", "J", "K", "L", "N", "P", "Q", "R", "S", "T", "X"}:
                intrinsics.append((n, name))
            elif name.isalpha() or any(c.isdigit() for c in name):
                intrinsics.append((n, name))
        for mm in RE_ZSTAR.finditer(code):
            gaps.append((n, "ZSTAR", mm.group(0)))
        for mm in RE_XCALL.finditer(code):
            gaps.append((n, "XCALL", mm.group(0)[:40]))
    return labels, calls, intrinsics, gaps


def schema(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        PRAGMA journal_mode=WAL;
        DROP TABLE IF EXISTS routine_fts;
        DROP TABLE IF EXISTS routine;
        DROP TABLE IF EXISTS label;
        DROP TABLE IF EXISTS call_arg;
        DROP TABLE IF EXISTS call;
        DROP TABLE IF EXISTS intrinsic;
        DROP TABLE IF EXISTS gap;

        CREATE TABLE routine (
          id INTEGER PRIMARY KEY, name TEXT NOT NULL, path TEXT NOT NULL UNIQUE,
          package TEXT NOT NULL, labels INTEGER NOT NULL DEFAULT 0,
          lines INTEGER NOT NULL DEFAULT 0, bytes INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX ix_routine_name ON routine(name);
        CREATE INDEX ix_routine_pkg  ON routine(package);

        CREATE TABLE label (
          id INTEGER PRIMARY KEY, routine INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
          name TEXT NOT NULL, line INTEGER NOT NULL, formals TEXT,
          extrinsic INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX ix_label_routine ON label(routine);
        CREATE INDEX ix_label_name    ON label(name);

        CREATE TABLE call (
          id INTEGER PRIMARY KEY, routine INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
          line INTEGER NOT NULL, kind TEXT NOT NULL, target TEXT NOT NULL,
          tag TEXT, ext TEXT
        );
        CREATE INDEX ix_call_target ON call(target);
        CREATE INDEX ix_call_ext    ON call(ext);

        -- caller-side args of each DO/$$ (pass-by-ref = FileMan .var idiom)
        CREATE TABLE call_arg (
          id INTEGER PRIMARY KEY, call INTEGER NOT NULL REFERENCES call(id) ON DELETE CASCADE,
          pos INTEGER NOT NULL,          -- 1-based
          src TEXT NOT NULL,             -- raw arg text as written
          byref INTEGER NOT NULL DEFAULT 0,
          byref_name TEXT                -- actual var name when byref
        );
        CREATE INDEX ix_call_arg_call ON call_arg(call);
        CREATE INDEX ix_call_arg_byref ON call_arg(byref);
        CREATE INDEX ix_call_arg_name  ON call_arg(byref_name);

        CREATE TABLE intrinsic (
          id INTEGER PRIMARY KEY, routine INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
          line INTEGER NOT NULL, name TEXT NOT NULL
        );
        CREATE INDEX ix_intrinsic_name ON intrinsic(name);

        CREATE TABLE gap (
          id INTEGER PRIMARY KEY, routine INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
          line INTEGER NOT NULL, kind TEXT NOT NULL, detail TEXT
        );
        CREATE INDEX ix_gap_kind ON gap(kind);

        CREATE VIRTUAL TABLE routine_fts USING fts5(name, body);
        """
    )


def load(db: sqlite3.Connection, root: Path, limit: int) -> dict:
    counts = {
        "routines": 0, "labels": 0, "calls": 0, "call_args": 0,
        "byref_args": 0, "intrinsics": 0, "gaps": 0,
    }
    files = sorted(root.rglob("*.m"))
    if limit:
        files = files[:limit]
    for p in files:
        rel = p.relative_to(root).as_posix()
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except OSError as e:
            print(f"  READ FAIL {rel}: {e}", file=sys.stderr)
            continue
        name = p.stem.upper()
        pkg = package_of(rel)
        labels, calls, intrinsics, gaps = parse_routine(text)
        cur = db.execute(
            "INSERT INTO routine(name,path,package,labels,lines,bytes) VALUES(?,?,?,?,?,?)",
            (name, rel, pkg, len(labels), text.count("\n") + 1, len(text)),
        )
        rid = cur.lastrowid
        db.execute(
            "INSERT INTO routine_fts(rowid,name,body) VALUES(?,?,?)", (rid, name, text)
        )
        for lab, line, formals in labels:
            db.execute(
                "INSERT INTO label(routine,name,line,formals,extrinsic) VALUES(?,?,?,?,?)",
                (rid, lab, line, formals, 1 if formals else 0),
            )
        for line, kind, target, tag, ext, args in calls:
            cur = db.execute(
                "INSERT INTO call(routine,line,kind,target,tag,ext) VALUES(?,?,?,?,?,?)",
                (rid, line, kind, target, tag, ext),
            )
            cid = cur.lastrowid
            for pos, src in enumerate(args, 1):
                bn = byref_name(src)
                db.execute(
                    "INSERT INTO call_arg(call,pos,src,byref,byref_name) VALUES(?,?,?,?,?)",
                    (cid, pos, src, 1 if bn else 0, bn),
                )
                counts["call_args"] += 1
                if bn:
                    counts["byref_args"] += 1
        for line, iname in intrinsics:
            db.execute("INSERT INTO intrinsic(routine,line,name) VALUES(?,?,?)", (rid, line, iname))
        for line, kind, detail in gaps:
            db.execute("INSERT INTO gap(routine,line,kind,detail) VALUES(?,?,?,?)", (rid, line, kind, detail))
        counts["routines"] += 1
        counts["labels"] += len(labels)
        counts["calls"] += len(calls)
        counts["intrinsics"] += len(intrinsics)
        counts["gaps"] += len(gaps)
    db.commit()
    return counts


def stats(db: sqlite3.Connection) -> None:
    print("\n### package × routines")
    for pkg, n in db.execute("SELECT package, COUNT(*) FROM routine GROUP BY 1 ORDER BY 2 DESC"):
        print(f"  {pkg:<12} {n}")
    print("\n### top targets (DO/$$/GOTO)")
    for tgt, n in db.execute(
        "SELECT target, COUNT(*) FROM call GROUP BY 1 ORDER BY 2 DESC LIMIT 12"
    ):
        print(f"  {n:>5}  {tgt}")
    print("\n### top intrinsics")
    for nm, n in db.execute(
        "SELECT name, COUNT(*) FROM intrinsic GROUP BY 1 ORDER BY 2 DESC LIMIT 12"
    ):
        print(f"  {n:>5}  ${nm}")
    print("\n### gaps by kind")
    for kind, n in db.execute("SELECT kind, COUNT(*) FROM gap GROUP BY 1 ORDER BY 2 DESC"):
        print(f"  {kind:<8} {n}")
    print("\n### byref .var args (caller-side)")
    tot_args = db.execute("SELECT COUNT(*) FROM call_arg").fetchone()[0]
    tot_by = db.execute("SELECT COUNT(*) FROM call_arg WHERE byref=1").fetchone()[0]
    print(f"  args={tot_args}  byref={tot_by}  "
          f"({(100.0*tot_by/tot_args):.1f}% of args)" if tot_args else "  args=0")
    print("  -- top actuals passed byref")
    for nm, n in db.execute(
        "SELECT byref_name, COUNT(*) FROM call_arg WHERE byref=1"
        " GROUP BY 1 ORDER BY 2 DESC LIMIT 12"
    ):
        print(f"  {n:>5}  .{nm}")
    print("  -- targets most often called with byref")
    for tgt, n, nb in db.execute(
        "SELECT c.target, COUNT(*) n, SUM(a.byref) nb"
        " FROM call c JOIN call_arg a ON a.call=c.id"
        " WHERE a.byref=1 GROUP BY 1 ORDER BY nb DESC, n DESC LIMIT 12"
    ):
        print(f"  {nb:>5}/{n:<5}  {tgt}")
    print("  -- calls with any byref arg, by kind")
    for kind, n in db.execute(
        "SELECT c.kind, COUNT(DISTINCT c.id) FROM call c"
        " JOIN call_arg a ON a.call=c.id WHERE a.byref=1 GROUP BY 1 ORDER BY 2 DESC"
    ):
        print(f"  {n:>5}  {kind}")
    print("\n### extrinsics (formals) sample")
    for r, lab, f in db.execute(
        "SELECT r.name, l.name, l.formals FROM label l JOIN routine r ON r.id=l.routine"
        " WHERE l.extrinsic=1 ORDER BY r.name, l.name LIMIT 12"
    ):
        print(f"  $${lab}^{r}({f})")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--stats", action="store_true")
    args = ap.parse_args()

    root = Path(os.environ.get("FILEMAN_CORPUS", Path.home() / "_fileman"))
    db_path = Path(os.environ.get("FILEMAN_INDEX", root / "fileman-index.sqlite"))
    if not root.is_dir():
        print(f"no corpus at {root}", file=sys.stderr)
        return 1

    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    db = sqlite3.connect(db_path)
    try:
        schema(db)
        counts = load(db, root, args.limit)
        print(f"indexed {counts['routines']} routines -> {db_path}")
        for k in ("labels", "calls", "call_args", "byref_args", "intrinsics", "gaps"):
            print(f"  {k}: {counts[k]}")
        if args.stats:
            stats(db)
    finally:
        db.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
