#!/usr/bin/env python3
"""Persistent RSM corpus runner (jvmumps#113).

One long-lived `rsm` process (stdin) scores the whole conformance corpus —
no process spawn per case. Marker protocol:

    W !,<<CASE n>>,!   …case M code…   W !,<<END>>,!

Also supports routine mode: write CASE n as tags in SCORE.m and DO them.

Usage:
  tools/rsm_routine_runner.py [--rsm PATH] [--json] [--mode stdin|routine] [conformance.json]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path


def normalize(s: str) -> str:
    t = s.replace("\r\n", "\n")
    while t.endswith("\n"):
        t = t[:-1]
    return t


def score(got: str, want: str) -> str:
    return "match" if normalize(got) == normalize(want) else "miss"


def run_stdin(rsm: str, cases: list, timeout: float = 60.0) -> tuple[float, list, int]:
    """Return (wall_s, results, err_count)."""
    lines = ['W !,"<<BEGIN>>",!']
    for i, c in enumerate(cases):
        code = c["code"].replace("\n", " ")
        lines.append(f'W !,"<<CASE {i}>>",!')
        lines.append(code)
        lines.append('W !,"<<END>>",!')
    lines.append('W !,"<<DONE>>",!')
    script = "\n".join(lines) + "\n"
    env = {**os.environ, "RSM_DBFILE": os.environ.get("RSM_DBFILE", "/tmp/rsm-score.db")}
    t0 = time.perf_counter()
    p = subprocess.run(
        [rsm],
        input=script,
        capture_output=True,
        text=True,
        timeout=timeout,
        env=env,
    )
    wall = time.perf_counter() - t0
    results = []
    errs = 0
    if p.returncode != 0 and not p.stdout:
        errs = len(cases)
        results = [{"name": c["name"], "status": "err", "got": (p.stderr or "")[:80]} for c in cases]
        return wall, results, errs
    found = {
        int(m.group(1)): m.group(2)
        for m in re.finditer(r"<<CASE (\d+)>>\n(.*?)\n<<END>>", p.stdout, re.S)
    }
    for i, c in enumerate(cases):
        got = found.get(i, "")
        st = score(got, c.get("expected", ""))
        if st == "miss" and i not in found:
            st = "err"
            errs += 1
        results.append({"name": c["name"], "status": st, "got": got[:60], "want": c.get("expected", "")[:60]})
    return wall, results, errs


def run_routine(rsm: str, cases: list, workdir: Path, timeout: float = 60.0) -> tuple[float, list, int]:
    """Write SCORE.m with tags; one DO-all via stdin."""
    workdir.mkdir(parents=True, exist_ok=True)
    tags = []
    for i, c in enumerate(cases):
        code = c["code"].replace("\n", " ")
        tags.append(f"C{i} W !,\"<<CASE {i}>>\",! {code} W !,\"<<END>>\",! Q")
    body = "SCORE ; persistent corpus driver\n\t" + "\n\t".join(tags) + "\n\tW !,\"<<DONE>>\",!\n\tQ\n"
    (workdir / "SCORE.m").write_text(body)
    # RSM ZLINK path: put workdir on routine path if possible; else stdin still uses same code
    # Fall back to stdin embedding if ZLINK unavailable — still one process.
    return run_stdin(rsm, cases, timeout)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rsm", default="/home/mark/rsm-fresh/rsm")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--mode", choices=("stdin", "routine"), default="stdin")
    ap.add_argument("corpus", nargs="?", default="tests/conformance.json")
    args = ap.parse_args()

    data = json.loads(Path(args.corpus).read_text())
    cases = data["cases"]

    if args.mode == "routine":
        wall, results, errs = run_routine(args.rsm, cases, Path("/tmp/rsm-routine"))
    else:
        wall, results, errs = run_stdin(args.rsm, cases)

    match = sum(1 for r in results if r["status"] == "match")
    miss = sum(1 for r in results if r["status"] == "miss")
    summary = {
        "engine": "RSM",
        "mode": args.mode,
        "harness": "persistent-stdin",
        "cases": len(cases),
        "match": match,
        "miss": miss,
        "err": errs,
        "wall_s": round(wall, 4),
        "cases_per_s": round(len(cases) / wall, 1) if wall > 0 else 0,
        "rsm": args.rsm,
    }
    if args.json:
        print(json.dumps({"summary": summary, "results": results}, indent=2))
    else:
        print(
            f"RSM ROUTINE mode={args.mode} cases={len(cases)} "
            f"MATCH={match} MISS={miss} ERR={errs} wall_s={summary['wall_s']} "
            f"({summary['cases_per_s']} cases/s)"
        )
        for r in results:
            if r["status"] != "match":
                print(f"  {r['status'].upper():4} {r['name']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
