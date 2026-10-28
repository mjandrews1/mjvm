#!/bin/bash
# gen-phases.sh — emit per-phase .m programs (for engines without $ZUT).
# Same bodies as BENCH.m; each prints a short tag + checksum where cheap.
set -e
D="$(cd "$(dirname "$0")" && pwd)"
cd "$D/phases"

cat > ph_base.m <<'M'
PHBASE ; baseline (startup cost only)
 q
M

cat > ph_intloop.m <<'M'
PHINTLOOP ; INTLOOP 500k adds
 n i,r s r=0 f i=1:1:500000 s r=r+i
 w r,!
 q
M

cat > ph_strcat.m <<'M'
PHSTRCAT ; STRCAT 30k appends
 n i,s s s="x" f i=1:1:30000 s s=s_$J(i,7)
 w $L(s),!
 q
M

cat > ph_aset.m <<'M'
PHASET ; ASET 100k local sets
 n i,a k a f i=1:1:100000 s a(i)=i
 w $G(a(100000)),!
 q
M

cat > ph_awalk.m <<'M'
PHAWALK ; AWALK 100k local $ORDER
 n i,j,c,a k a f i=1:1:100000 s a(i)=i
 s j="",c=0 f  s j=$O(a(j)) q:j=""  s c=c+1
 w c,!
 q
M

cat > ph_piece.m <<'M'
PHPIECE ; PIECE 200k $PIECE
 n i,p,str s str="" f i=1:1:100 s str=str_$J(i,10)
 f i=1:1:200000 s p=$P(str," ",i#100+1)
 w p,!
 q
M

cat > ph_gset.m <<'M'
PHGSET ; GSET 50k global sets
 n i k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 w $G(^BENCHTEST(50000)),!
 q
M

cat > ph_gread.m <<'M'
PHGREAD ; GREAD 500k global gets (+50k setup)
 n i,v k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 f i=1:1:500000 s v=$G(^BENCHTEST(i#50000+1))
 w v,!
 q
M

cat > ph_gwalk.m <<'M'
PHGWALK ; GWALK 50k global $ORDER
 n i,j,c k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 s j="",c=0 f  s j=$O(^BENCHTEST(j)) q:j=""  s c=c+1
 k ^BENCHTEST
 w c,!
 q
M

echo "phases written: $(ls ph_*.m | tr '\n' ' ')"
