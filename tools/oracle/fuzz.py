#!/usr/bin/env python3
"""P1d grammar fuzzer — random M programs, GT.M vs mJVM, auto-classified.

Workstream C of the conformance campaign (mjvm/docs/CONFORMANCE-CAMPAIGN.md).
Generates small deterministic M programs from the construct grammar, runs them
under GT.M (U01, via ssh — same mechanics as refresh-expected.sh) and mJVM
(locally), diffs, and classifies every divergence against the known bug
families so only NEW findings need human triage.

Usage: python3 tools/oracle/fuzz.py [--n 100] [--seed 1] [--keep-new]
  --n       programs to generate/run
  --seed    PRNG seed (reproducible batches)
  --keep-new write unclassified divergences to fuzz/findings/ for triage

Classifiers (in order):
  NEWLINE-ONLY  got == want after stripping newlines        -> WRITE-break upstream class
  KNOWN:<id>    program matches a known bug signature       -> see signatures below
  NEW           unexplained                                                -> triage
"""
import argparse
import datetime
import json
import os
import random
import subprocess
import sys
import tempfile

REMOTE = "mark@100.72.84.8"
GTM_ROUTINES = "/home/mark/gtm-ecosystem/routines"
MJVM_CP = None  # computed lazily

VARS = ["A", "B", "C", "X", "Y", "Z"]
FUNCS1 = ['$P({s},"{d}",{n})', "$E({s},{n},{m})", "$L({s})", "$G({v}({n}))", "$A({s})"]


def expr(r, s, v, n, m):
    kind = r.randint(0, 5)
    if kind == 0:
        return str(r.randint(1, 99))
    if kind == 1:
        return r.choice(VARS)
    if kind == 2:
        return f"{r.randint(1,9)}+{r.randint(1,9)}"
    if kind == 3:
        return f"{r.choice(VARS)}+{r.randint(1,20)}"
    if kind == 4:
        return r.choice(FUNCS1).format(s=s, v=v, n=n, m=m, d=r.choice("-,;:"))
    return f'{r.randint(1,9)}*{r.randint(1,9)}'


def gen_program(idx, r):
    lines = [f"FZ{idx} ; fuzz program {idx}"]
    s = "s" + str(idx)  # deterministic string var
    v = r.choice(VARS)
    n = r.randint(1, 3)
    m = n + r.randint(0, 3)
    lines.append(f' S {s}="a-b-c,d;e:f"')
    # initialize every variable the grammar may reference (avoids undefined-var
    # errors that mask real divergence — those are a generator artifact)
    for var in VARS:
        lines.append(f" S {var}={r.randint(1,50)}")
    nstmt = r.randint(3, 6)
    for _ in range(nstmt):
        k = r.randint(0, 10)
        if k == 0:  # scalar set
            lines.append(f" S {r.choice(VARS)}={expr(r,s,v,n,m)}")
        elif k == 1:  # subscripted local set
            lines.append(f" S {v}({r.randint(1,3)})={expr(r,s,v,n,m)}")
        elif k == 2:  # global set
            lines.append(f" K ^Z{idx}")
            lines.append(f" S ^Z{idx}({r.randint(1,3)})={expr(r,s,v,n,m)}")
            lines.append(f" S R{idx}=$G(^Z{idx}(1))+{r.randint(0,9)}")
        elif k == 3:  # forward FOR, dotted body, no IF (known-good shape family)
            lines.append(f" F I=1:1:{r.randint(2,4)} D")
            lines.append(f" . S {v}={v}+I")
        elif k == 4:  # FOR same-line single command
            lines.append(f" F I=1:1:{r.randint(2,4)} S {v}={v}+I")
        elif k == 5:  # IF chained + one command
            lines.append(f" I {v}>{r.randint(1,99)} W \"hi\"")
        elif k == 6:  # string op
            lines.append(f" S {s}={s}_\"-z\"")
        elif k == 7:  # $ORDER walk (bounded)
            lines.append(f" S J=\"\" S C{idx}=0")
            lines.append(f" F I=1:1:{r.randint(2,4)} S ^Q{idx}(I)=I")
            lines.append(
                f" F  S J=$O(^Q{idx}(J)) Q:J=\"\"  S C{idx}=C{idx}+1"
            )
            lines.append(f" K ^Q{idx}")
        elif k == 8:  # DO tag with argumentless NEW (mjvm#31 family)
            # GT.M: N stacks a new frame; L is undefined after QUIT (caller W L
            # errors). mJVM: N is a no-op, so L leaks and W L prints 7.
            tag = f"T{idx}{r.randint(1,99)}"
            leaked = f"L{idx}"
            lines.append(f" D {tag}")
            lines.append(f" W {leaked}")
            lines.append(tag)
            lines.append(" N")
            lines.append(f" S {leaked}=7")
            lines.append(" Q")
        elif k == 9:  # HALT mid-program (mjvm#29 family)
            lines.append(' W "pre"')
            lines.append(" HALT")
            lines.append(' W "post"')
        else:  # exclusive KILL (mjvm#30 family) — keep-var must survive
            keep = r.choice(VARS)
            lines.append(f" K ({keep})")
            lines.append(f" W {keep}")
            # re-initialize the killed vars so the terminal WRITE stays valid
            for var in VARS:
                if var != keep:
                    lines.append(f" S {var}={r.randint(1,50)}")
    # single terminal WRITE (compute-then-print style avoids newline-class noise)
    wargs = []
    for _ in range(r.randint(1, 3)):
        if r.randint(0, 1):
            wargs.append(r.choice(VARS + [f"C{idx}", f"R{idx}", s]))
        else:
            wargs.append(f'"{r.choice("out=|+-")}"')
    lines.append(" W " + ",".join(wargs))
    lines.append(" Q")
    return "\n".join(lines) + "\n"


KNOWN_SIGNATURES = [
    # (bug, predicate(program_text))
    ("mjvm#22 (2-arg W lit+var)", lambda p: _has_w22(p)),
    ("mjvm#17 (comma-IF)", lambda p: _has_comma_if(p)),
    ("mjvm#16 (negative FOR)", lambda p: _has_neg_for(p)),
    ("mjvm#18 (IF in dotted)", lambda p: _has_if_dotted(p)),
    ("mjvm#4/#21 (postcond W)", lambda p: "W:" in p),
    ("mjvm#19 (multi-cmd same-line FOR)", lambda p: _has_multicmd_for(p)),
    ("mjvm#23 (NEW in DO tag)", lambda p: _has_new_do(p)),
    ("mjvm#24 (multi-arg W drops final non-literal)", lambda p: _has_w24(p)),
    ("mjvm#29 (HALT continues)", lambda p: _has_halt(p)),
    ("mjvm#30 (K (V) kills V)", lambda p: _has_kexcl(p)),
    ("mjvm#31 (argumentless NEW no-op)", lambda p: _has_argless_new(p)),
]


def _has_halt(p):
    return any(l.strip() == "HALT" or l.strip().startswith("HALT ") for l in p.splitlines())


def _has_kexcl(p):
    return any(l.strip().startswith("K (") for l in p.splitlines())


def _has_argless_new(p):
    return any(l.strip() == "N" for l in p.splitlines())


def _has_w24(p):
    # 3+-arg WRITE whose final argument is non-literal (var/fn): compiles but drops it
    for line in p.splitlines():
        t = line.strip()
        if t.startswith("W ") and t.count(",") >= 2:
            last = t[2:].rsplit(",", 1)[1]
            if not last.startswith('"'):
                return True
    return False


def _has_multicmd_for(p):
    # `F [I=1:1:n] CMD ...` with 2+ commands after the loop head — the rest-of-line
    # body family (#19), including the dominant $O-walk idiom.
    for line in p.splitlines():
        t = line.strip()
        if not t.startswith("F "):
            continue
        if t.startswith("F  "):  # argumentless FOR, body = rest of line
            if " Q:" in t and (" S " in t or " W " in t):
                return True
            if t.count(" S ") >= 2 or (" S " in t and " W " in t):
                return True
        else:  # bounded FOR + 2+ commands
            if t.count(" S ") >= 2 or (" S " in t and " W " in t):
                return True
    return False


def _has_new_do(p):
    # NEW inside a tag that is DOed (heuristic: ` N ` appears after the first QUIT line)
    lines = p.splitlines()
    for i, line in enumerate(lines):
        if i > 0 and line.strip().startswith("N ") and any(
            l.strip().startswith("D ") for l in lines[: i]
        ):
            return True
    return False


def _has_w22(p):
    for line in p.splitlines():
        t = line.strip()
        if t.startswith("W ") and t.count(",") == 1:
            parts = t[2:].rsplit(",", 1)
            if not parts[1].startswith('"'):  # last arg non-literal (var or fn call)
                return True
    return False


def _has_comma_if(p):
    for line in p.splitlines():
        t = line.strip()
        if t.startswith("I ") and "," in t.split(" ")[1]:
            return True
    return False


def _has_neg_for(p):
    return ":-1:" in p or ":-2:" in p or ":-3:" in p


def _has_if_dotted(p):
    lines = p.splitlines()
    for i, line in enumerate(lines):
        if line.strip().startswith("I ") and i + 1 < len(lines) and lines[i + 1].startswith(" ."):
            return True
    return False


def run_gtms(programs, tmp):
    """Run all under GT.M on U01; return {name: output}."""
    cases_dir = f"{tmp}/cases"
    os.makedirs(cases_dir, exist_ok=True)
    names = []
    for name, text in programs.items():
        with open(f"{cases_dir}/{name}.m", "w") as f:
            f.write(text)
        names.append(name)
    subprocess.run(
        ["rsync", "-a", "--delete", cases_dir + "/", f"{REMOTE}:/tmp/fuzz-cases/"],
        check=True,
    )
    script = "cd /home/mark/gtm-ecosystem && source ./gtm-env.sh >/dev/null 2>&1 && unset gtm_chset\n"
    script += "mkdir -p /tmp/fuzz-out\n"
    for name in names:
        script += (
            f"cp /tmp/fuzz-cases/{name}.m {GTM_ROUTINES}/{name}.m\n"
            f'printf "D ^{name}\\nH\\n" | "$gtm_dist/mumps" -direct >/tmp/fuzz-out/{name}.out 2>&1\n'
            f"rm -f {GTM_ROUTINES}/{name}.m {GTM_ROUTINES}/{name}.o\n"
        )
    script += "exit 0\n"
    subprocess.run(["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=15", REMOTE, script],
                   check=True, capture_output=True, timeout=600)
    out_dir = f"{tmp}/out"
    os.makedirs(out_dir, exist_ok=True)
    subprocess.run(["rsync", "-a", f"{REMOTE}:/tmp/fuzz-out/", out_dir + "/"], check=True)
    got = {}
    for name in names:
        with open(f"{out_dir}/{name}.out") as f:
            text = f.read()
        lines = []
        for ln in text.splitlines():
            if ln == "GTM>" or ln == "":
                continue
            lines.append(ln.removeprefix("GTM> "))
        got[name] = "\n".join(lines)
    return got


def run_mjvm(programs, tmp):
    global MJVM_CP
    if MJVM_CP is None:
        root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        MJVM_CP = f"{root}/build:{root}/lib/*"
    java = os.environ.get(
        "JAVA_HOME", "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    ) + "/bin/java"
    cases_dir = f"{tmp}/cases"
    got = {}
    for name, text in programs.items():
        p = subprocess.run(
            [java, "-cp", MJVM_CP, "mjvm.cli.Mjvm", f"{cases_dir}/{name}.m"],
            capture_output=True, text=True, timeout=60,
        )
        got[name] = (p.stdout + p.stderr).strip()
    return got


def classify(name, prog, want, got):
    if got == want:
        return "PASS", None
    # both engines errored (GT.M M-error text vs mJVM exception text) — semantic
    # equivalence; the error FORMAT differs (GT.M names the variable, mJVM is
    # generic) which is an observability note, not a behavioral divergence
    if "%GTM-E-" in want and ("MError" in got or "Exception" in got):
        return "PASS (both errored; format differs)", None
    if got.replace("\n", "") == want.replace("\n", ""):
        return "UPSTREAM (WRITE line-break)", None
    for bug, sig in KNOWN_SIGNATURES:
        if sig(prog):
            return f"KNOWN {bug}", None
    # $STACK style divergences etc. land here
    return "NEW", (name, prog, want, got)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=100)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--keep-new", action="store_true")
    args = ap.parse_args()

    r = random.Random(args.seed)
    programs = {}
    for i in range(args.n):
        programs[f"FZ{i}"] = gen_program(i, r)

    with tempfile.TemporaryDirectory() as tmp:
        gtm_out = run_gtms(programs, tmp)
        mjava_out = run_mjvm(programs, tmp)

    counts = {}
    new_findings = []
    for name, prog in programs.items():
        kind, finding = classify(name, prog, gtm_out[name], mjava_out[name])
        counts[kind] = counts.get(kind, 0) + 1
        if finding:
            new_findings.append(finding)

    for kind in sorted(counts, key=lambda k: -counts[k]):
        print(f"{counts[kind]:5d}  {kind}")
    # JSONL ledger: one line per run for the nightly trend (workstream C exit
    # criterion: divergence rate over time). Committed by the nightly job.
    ledger = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fuzz-ledger.jsonl")
    entry = {
        "date": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "seed": args.seed,
        "n": args.n,
        "counts": counts,
        "new": len(new_findings),
    }
    with open(ledger, "a") as f:
        f.write(json.dumps(entry) + "\n")
    if new_findings:
        print(f"\n{len(new_findings)} NEW findings:")
        for name, prog, want, got in new_findings[:10]:
            print(f"--- {name} ---\n{prog}\n  want: {want[:120]}\n  got:  {got[:120]}")
        if args.keep_new:
            fdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fuzz-findings")
            os.makedirs(fdir, exist_ok=True)
            for name, prog, want, got in new_findings:
                with open(f"{fdir}/{name}.m", "w") as f:
                    f.write(prog)
                with open(f"{fdir}/{name}.want", "w") as f:
                    f.write(want)
                with open(f"{fdir}/{name}.got", "w") as f:
                    f.write(got)
            print(f"findings written to {fdir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
