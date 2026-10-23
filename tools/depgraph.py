#!/usr/bin/env python3
"""JavaM #63 — dependency graph + cycle report.

jdeps package graph + optional M DO/GOTO walker -> DOT.
Exit 1 on cycles unless --allow-cycles.

  python3 tools/depgraph.py --build build --check
  python3 tools/depgraph.py --m-root /path/to/m --m-out build/m-calls.dot
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
from collections import defaultdict

LABEL = re.compile(r"^([A-Za-z%][\w%]*)\b")
DO_GOTO = re.compile(r"\b(?:DO|D|GOTO|G)\s+([\w%$.^]+)", re.I)


def find_cycles(adj):
    index, low, onstack, stack, out, counter = {}, {}, set(), [], [], [0]

    def strong(v):
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        onstack.add(v)
        for w in adj.get(v, ()):
            if w not in index:
                strong(w)
                low[v] = min(low[v], low[w])
            elif w in onstack:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                onstack.discard(w)
                comp.append(w)
                if w == v:
                    break
            if len(comp) > 1 or (len(comp) == 1 and comp[0] in adj.get(comp[0], ())):
                out.append(comp[::-1])

    for v in list(adj):
        if v not in index:
            strong(v)
    return out


def java_jdeps(build_dir, out):
    dotdir = os.path.dirname(out) or "."
    os.makedirs(dotdir, exist_ok=True)
    r = subprocess.run(
        ["jdeps", "-verbose:package", "-dotoutput", dotdir, build_dir],
        capture_output=True,
        text=True,
    )
    gen = None
    for cand in ("package.dot", "build.dot", "summary.dot"):
        path = os.path.join(dotdir, cand)
        if os.path.isfile(path) and os.path.getsize(path) > 100:
            gen = path
            break
    if gen and os.path.isfile(gen):
        data = open(gen).read()
        open(out, "w").write(data)
        # parse edges from dot for SCC
        edges = re.findall(r'"([^"]+)"\s*->\s*"([^"]+)"', data)
        adj = defaultdict(set)
        for a, b in edges:
            adj[a].add(b)
        cycles = find_cycles(adj)
        print(f"wrote {out} from jdeps nodes~{len(adj)} cycles={len(cycles)}")
        for c in cycles:
            print("CYCLE", " -> ".join(c))
        return 1 if cycles else 0
    print("jdeps package.dot missing, falling back", file=os.sys.stderr)
    return java_import_graph("src/main/java", out)


def java_import_graph(src_root, out):
    pkgs = set()
    imports = defaultdict(set)
    for dirpath, _, files in os.walk(src_root):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            text = open(os.path.join(dirpath, fn), encoding="utf-8", errors="ignore").read()
            m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
            pkg = m.group(1) if m else "default"
            pkgs.add(pkg)
            for im in re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M):
                ip = ".".join(im.split(".")[:-1])
                if ip and ip != pkg:
                    imports[pkg].add(ip)
    with open(out, "w") as f:
        f.write("digraph java_deps {\n  rankdir=LR;\n")
        for n in sorted(pkgs | set(imports) | {x for vs in imports.values() for x in vs}):
            f.write(f'  "{n}";\n')
        for a in sorted(imports):
            for b in sorted(imports[a]):
                f.write(f'  "{a}" -> "{b}";\n')
        f.write("}\n")
    cycles = find_cycles(imports)
    print(f"wrote {out} packages={len(pkgs)} cycles={len(cycles)}")
    for c in cycles:
        print("CYCLE", " -> ".join(c))
    return 1 if cycles else 0


def m_call_graph(m_root, out):
    graphs = defaultdict(set)
    for dirpath, _, files in os.walk(m_root):
        for fn in files:
            if not fn.endswith(".m"):
                continue
            routine = fn[:-2]
            cur = "MAIN"
            for line in open(os.path.join(dirpath, fn), encoding="utf-8", errors="ignore"):
                if line[:1] in (" ", "\t", "") or not line.strip():
                    continue
                lab = LABEL.match(line)
                if lab:
                    cur = lab.group(1).upper()
                for m in DO_GOTO.finditer(line):
                    tgt = m.group(1).split("(")[0].upper()
                    if "^" in tgt:
                        graphs[f"{routine}:{cur}"].add(f"EXT:{tgt}")
                    else:
                        graphs[f"{routine}:{cur}"].add(f"{routine}:{tgt}")
    with open(out, "w") as f:
        f.write("digraph m_calls {\n  rankdir=LR;\n")
        for s in sorted(graphs):
            f.write(f'  "{s}";\n')
            for d in sorted(graphs[s]):
                f.write(f'  "{s}" -> "{d}";\n')
        f.write("}\n")
    cycles = find_cycles(graphs)
    print(f"wrote {out} nodes={len(graphs)} cycles={len(cycles)}")
    for c in cycles:
        print("CYCLE", " -> ".join(c))
    return 1 if cycles else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", default="build")
    ap.add_argument("--jdeps-out", default="build/dep.dot")
    ap.add_argument("--m-root", default=None)
    ap.add_argument("--m-out", default="build/m-calls.dot")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--allow-cycles", action="store_true")
    args = ap.parse_args()
    rc = java_jdeps(args.build, args.jdeps_out)
    if args.m_root:
        rc |= m_call_graph(args.m_root, args.m_out)
    if args.allow_cycles:
        return 0
    return rc if args.check else 0


if __name__ == "__main__":
    raise SystemExit(main())
