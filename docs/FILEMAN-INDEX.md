# FileMan / VistA source index

_Sketch companion to jvmumps#32 (FileMan Tier A). Derived index only — does
not touch MeshGraph (YottaDB) or tsx (Comdb2). Both live planes stay as they
are; this is a disposable, rebuildable SQLite file._

**Why not MeshGraph / Comdb2:** MeshGraph is the biomedical citation plane
(221 GB, 4 GB cap). Comdb2 is the tsx time-series plane. VistA M source is
neither. A local SQLite + FTS5 file is cheap, rebuildable, and answers the
queries that actually matter for corpus work.

## Store

| | |
|---|---|
| Engine | SQLite 3 + FTS5 |
| File | `$FILEMAN_INDEX` default `~/_fileman/fileman-index.sqlite` |
| Rebuild | `tools/load_fileman_index.py` (full rescan; idempotent) |
| Corpus | `$FILEMAN_CORPUS` default `~/_fileman` (also accepts U02 `vista-foia`) |

## Schema

```sql
-- one row per .m routine
CREATE TABLE routine (
  id        INTEGER PRIMARY KEY,
  name      TEXT NOT NULL,          -- XLFSTR
  path      TEXT NOT NULL UNIQUE,   -- relative to corpus root
  package   TEXT NOT NULL,          -- fileman | kernel | rtl | toolkit | utils | root
  labels    INTEGER NOT NULL DEFAULT 0,
  lines     INTEGER NOT NULL DEFAULT 0,
  bytes     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX ix_routine_name ON routine(name);
CREATE INDEX ix_routine_pkg  ON routine(package);

-- one row per tag / label
CREATE TABLE label (
  id        INTEGER PRIMARY KEY,
  routine   INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
  name      TEXT NOT NULL,          -- UP, LOW, SPLIT
  line      INTEGER NOT NULL,       -- 1-based
  formals   TEXT,                   -- "X,Y" or NULL
  extrinsic INTEGER NOT NULL DEFAULT 0   -- has formals → callable as $$name
);
CREATE INDEX ix_label_routine ON label(routine);
CREATE INDEX ix_label_name    ON label(name);

-- DO / $$ / GOTO edges (call graph)
CREATE TABLE call (
  id        INTEGER PRIMARY KEY,
  routine   INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
  line      INTEGER NOT NULL,
  kind      TEXT NOT NULL,          -- DO | EXTRINSIC | GOTO
  target    TEXT NOT NULL,          -- GETFILE^DIC0, $$UP^XLFSTR, INIT
  tag       TEXT,                   -- GETFILE / UP / INIT
  ext       TEXT                    -- DIC0 / XLFSTR / NULL (same-routine)
);
CREATE INDEX ix_call_target ON call(target);
CREATE INDEX ix_call_ext    ON call(ext);

-- caller-side args of each DO / $$ (pass-by-ref = FileMan .var idiom)
CREATE TABLE call_arg (
  id         INTEGER PRIMARY KEY,
  call       INTEGER NOT NULL REFERENCES call(id) ON DELETE CASCADE,
  pos        INTEGER NOT NULL,      -- 1-based
  src        TEXT NOT NULL,         -- raw arg text as written
  byref      INTEGER NOT NULL DEFAULT 0,
  byref_name TEXT                  -- actual var name when byref (.DIC → DIC)
);
CREATE INDEX ix_call_arg_call  ON call_arg(call);
CREATE INDEX ix_call_arg_byref ON call_arg(byref);
CREATE INDEX ix_call_arg_name  ON call_arg(byref_name);

-- $intrinsic usage (FileMan-shaped analysis)
CREATE TABLE intrinsic (
  id        INTEGER PRIMARY KEY,
  routine   INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
  line      INTEGER NOT NULL,
  name      TEXT NOT NULL           -- ORDER, PIECE, EXTRACT, ZCONVERT, …
);
CREATE INDEX ix_intrinsic_name ON intrinsic(name);

-- known-gap markers (vendor $Z*, XCall, OS interface) — allowlist source
CREATE TABLE gap (
  id        INTEGER PRIMARY KEY,
  routine   INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
  line      INTEGER NOT NULL,
  kind      TEXT NOT NULL,          -- ZSTAR | XCALL | OSV | ZISH
  detail    TEXT
);
CREATE INDEX ix_gap_kind ON gap(kind);

-- full-text over routine text (name + body)
CREATE VIRTUAL TABLE routine_fts USING fts5(
  name, body,
  content='routine', content_rowid='id'
);
```

## Example queries

```sql
-- Who calls FileMan lookup?
SELECT c.target, COUNT(*) n FROM call c
 WHERE c.target LIKE '%DIC0%' OR c.ext='DIC0'
 GROUP BY 1 ORDER BY n DESC LIMIT 20;

-- Which routines use $ORDER on FileMan-shaped keys?
SELECT r.name, COUNT(*) n FROM intrinsic i JOIN routine r ON r.id=i.routine
 WHERE i.name='ORDER' GROUP BY 1 ORDER BY n DESC LIMIT 20;

-- Extrinsics available in XLFSTR
SELECT name, formals FROM label
 WHERE routine=(SELECT id FROM routine WHERE name='XLFSTR') AND extrinsic=1;

-- Known-gap density by package
SELECT r.package, g.kind, COUNT(*) n
  FROM gap g JOIN routine r ON r.id=g.routine
 GROUP BY 1,2 ORDER BY n DESC;

-- byref .var usage: rate, top actuals, hottest call sites
SELECT COUNT(*) args,
       SUM(byref) byref,
       ROUND(100.0*SUM(byref)/COUNT(*),1) pct
  FROM call_arg;

SELECT byref_name, COUNT(*) n FROM call_arg
 WHERE byref=1 GROUP BY 1 ORDER BY n DESC LIMIT 20;

SELECT c.target, COUNT(*) n, SUM(a.byref) nb
  FROM call c JOIN call_arg a ON a.call=c.id
 WHERE a.byref=1 GROUP BY 1 ORDER BY nb DESC LIMIT 20;

-- Full-text: "lookup" near DIC
SELECT name FROM routine_fts WHERE routine_fts MATCH 'lookup AND DIC' LIMIT 20;
```

## Rebuild

```sh
FILEMAN_CORPUS=~/_fileman \
FILEMAN_INDEX=~/_fileman/fileman-index.sqlite \
  python3 tools/load_fileman_index.py
```

Read-only for the corpus. The SQLite file is disposable; delete and rerun.
