ROOTS ; tools/roots.m — gcd, integer sqrt, integer cube root in M
 ; P1d workstream D (arithmetic suite). Euclid gcd via # ; integer
 ; roots via the integer Newton iteration y <- (y + n\y)\2 (resp.
 ; (2y + n\y^2)\3), stopped when the iterate stops decreasing —
 ; the standard floor-root termination. All checks are known-answer.
 ;
TEST ;
 S OUT=""
 S A=1071 S B=462 D GCD
 I G=21 S OUT=OUT_"G1 ok gcd(1071,462)=21"_$C(10)
 I G'=21 S OUT=OUT_"G1 FAIL got "_G_$C(10)
 S A=270 S B=192 D GCD
 I G=6 S OUT=OUT_"G2 ok gcd(270,192)=6"_$C(10)
 I G'=6 S OUT=OUT_"G2 FAIL got "_G_$C(10)
 S A=17 S B=13 D GCD
 I G=1 S OUT=OUT_"G3 ok gcd(17,13)=1"_$C(10)
 I G'=1 S OUT=OUT_"G3 FAIL got "_G_$C(10)
 S N=1000000 D ISQRT
 I G=1000 S OUT=OUT_"S1 ok isqrt(1000000)=1000"_$C(10)
 I G'=1000 S OUT=OUT_"S1 FAIL got "_G_$C(10)
 S N=999999 D ISQRT
 I G=999 S OUT=OUT_"S2 ok isqrt(999999)=999"_$C(10)
 I G'=999 S OUT=OUT_"S2 FAIL got "_G_$C(10)
 S N=2 D ISQRT
 I G=1 S OUT=OUT_"S3 ok isqrt(2)=1"_$C(10)
 I G'=1 S OUT=OUT_"S3 FAIL got "_G_$C(10)
 S N=1000014000049 D ISQRT
 I G=1000007 S OUT=OUT_"S4 ok isqrt(1000014000049)=1000007"_$C(10)
 I G'=1000007 S OUT=OUT_"S4 FAIL got "_G_$C(10)
 S N=1073741824 D ICBRT
 I G=1024 S OUT=OUT_"C1 ok icbrt(2^30)=1024"_$C(10)
 I G'=1024 S OUT=OUT_"C1 FAIL got "_G_$C(10)
 S N=1000000000 D ICBRT
 I G=1000 S OUT=OUT_"C2 ok icbrt(10^9)=1000"_$C(10)
 I G'=1000 S OUT=OUT_"C2 FAIL got "_G_$C(10)
 S N=8 D ICBRT
 I G=2 S OUT=OUT_"C3 ok icbrt(8)=2"_$C(10)
 I G'=2 S OUT=OUT_"C3 FAIL got "_G_$C(10)
 W OUT
 Q
 ;
GCD ; G = gcd(A,B) — Euclid
 I B=0 G GCDD
 S T=B
 S B=A#B
 S A=T
 G GCD
GCDD ;
 S G=A
 Q
 ;
ISQRT ; G = floor(sqrt(N)), N >= 1 — Newton y <- (y + N\y)\2
 S G=N
ISQL ;
 S G2=(G+(N\G))\2
 I G2'<G G ISD
 S G=G2
 G ISQL
ISD ;
 Q
 ;
ICBRT ; G = floor(cbrt(N)), N >= 1 — Newton y <- (2y + N\y^2)\3
 S G=N
ICBL ;
 S G2=((2*G+(N\(G*G)))\3)
 I G2'<G G ICD
 S G=G2
 G ICBL
ICD ;
 Q
