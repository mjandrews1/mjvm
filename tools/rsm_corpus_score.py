#!/usr/bin/env python3
"""Score tests/conformance.json against the RSM oracle (jvmumps#99 row 14).

Usage:
  tools/rsm_corpus_score.py [--rsm /path/rsm] [--json] [conformance.json]

Prints MATCH/MISS/ERR and wall time. Exit 0 even with misses (oracle may
disagree on documented divergences — record them, do not fail CI).
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time


def normalize(s: str) -> str:
    t = s.replace("\r\n", "\n")
    while t.endswith("\n"):
        t = t[:-1]
    return t


def run_rsm(rsm: str, code: str, timeout: float = 10.0) -> tuple[str, str]:
    """Return (stdout, error) for one M fragment via rsm -x."""
    try:
        p = subprocess.run(
            [rsm, "-x", code],
            capture_output=True,
            text=True,
            timeout=timeout,
            env={**os.environ, "RSM_DBFILE": os.environ.get("RSM_DBFILE", "/tmp/rsm-score.db")},
        )
        err = (p.stderr or "").strip()
        return p.stdout or "", err
    except subprocess.TimeoutExpired:
        return "", "timeout"
    except Exception as e:
        return "", str(e)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rsm", default="/home/mark/rsm-fresh/rsm")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("corpus", nargs="?", default="tests/conformance.json")
    ap.add_argument("--prefix", default="*")
    args = ap.parse_args()

    with open(args.corpus) as f:
        data = json.load(f)
    cases = data["cases"]
    if args.prefix != "*":
        cases = [c for c in cases if c["name"].startswith(args.prefix)]

    match = miss = err = 0
    misses = []
    t0 = time.perf_counter()
    for c in cases:
        got, e = run_rsm(args.rsm, c["code"])
        if e and not got:
            err += 1
            misses.append({"name": c["name"], "error": e})
            continue
        if normalize(got) == normalize(c.get("expected", "")):
            match += 1
        else:
            miss += 1
            misses.append({"name": c["name"], "got": got[:80], "want": c.get("expected", "")[:80]})
    wall = time.perf_counter() - t0

    summary = {
        "engine": "RSM",
        "rsm": args.rsm,
        "cases": len(cases),
        "match": match,
        "miss": miss,
        "err": err,
        "wall_s": round(wall, 3),
    }
    if args.json:
        print(json.dumps({"summary": summary, "misses": misses[:20]}, indent=2))
    else:
        print(
            f"RSM CORPUS cases={len(cases)} MATCH={match} MISS={miss} ERR={err} "
            f"wall_s={wall:.3f} (harness=rsm -x, process-per-case)"
        )
        for m in misses[:15]:
            print(" ", m)
    return 0


if __name__ == "__main__":
    sys.exit(main())
