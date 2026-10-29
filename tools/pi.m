PI ; tools/pi.m — Machin's formula: pi = 16*atan(1/5) - 4*atan(1/239)
 ; P1d workstream D (known-answer math suite). Computes floor(pi*10^(N+G))
 ; as a big integer (G guard digits absorb series truncation) and prints
 ; its decimal digits. Digit 1 is "3"; digits 2..N+1 are the first N
 ; decimal digits of pi — digit N+1 is the millionth digit when N=1000000
 ; (ignore the last G+~1 printed digits).
 ;
 ; Big integers: base-10^9 limbs, little-endian. A(0)=limb count,
 ; A(1..count)=limbs, limb 1 least significant. Division only by small
 ; integers (O(limbs) per term) — no general bignum division.
 ;
 ; mJVM workarounds (issues #13/#16/#17/#19): no QUIT inside FOR; no
 ; negative FOR increments (descending index computed as I=CC+1-J); no IF
 ; inside dotted blocks; no multi-command same-line FOR bodies. The series
 ; runs in unconditional ADD+SUB pairs so the hot path has no IF; adds and
 ; subtracts read T only up to T(0). Comparisons are arithmetic (1/0);
 ; every mixed expression is parenthesized (M is left-to-right).
 ;
 ; Set N below, then: java -cp build:lib/* mjvm.cli.Mjvm tools/pi.m
 S N=1000
 S G=25
 S N=N+G
 S B=1000000000
 ;
 ; ===== atan(1/5): S = 10^N * atan(1/5), copied to U =====
 S X=5
 D BUILD
 D ATAN
 D COPYUS
 ;
 ; ===== atan(1/239): S = 10^N * atan(1/239) =====
 S X=239
 K P,T,S
 D BUILD
 D ATAN
 ;
 ; ===== pi*10^N = 16*U - 4*S =====
 D ADDUU
 D ADDUU
 D ADDUU
 D ADDUU
 D ADDSS
 D ADDSS
 D SUBUS
 ;
 D PRINT
 Q
 ;
BUILD ; P = 10^N
 S CNT=N\9+1
 F J=1:1:CNT S P(J)=0
 S R9=N#9
 S TOP=1
 F J=1:1:R9 S TOP=TOP*10
 S P(CNT)=TOP
 S P(0)=CNT
 Q
 ;
ATAN ; S = 10^N * atan(1/X). Requires P = 10^N. Uses T.
 ; Series in pairs: S += P/(4m+1); P/=X^2; S -= P/(4m+3); P/=X^2
 S DV=X
 D DIVPP
 S S=0
 S S(0)=0
 S KM=N\2+2
 F K=0:1:KM D PAIRSTEP
 Q
 ;
PAIRSTEP ; two series terms; zero P self-noops through empty T
 I K#10000=0 W "."
 S DV=4*K+1
 D DIVPT
 D ADDST
 S DV=X*X
 D DIVPP
 S DV=4*K+3
 D DIVPT
 D SUBST
 S DV=X*X
 D DIVPP
 Q
 ;
DIVPP ; P = P / DV (in place; DV small >= 2). Quotient of a normalized
 ; number by a small int loses at most one leading limb.
 S CC=P(0)
 S R=0
 F J=1:1:CC D
 . S I=CC+1-J
 . S V=R*B+P(I)
 . S P(I)=V\DV
 . S R=V#DV
 S P(0)=CC-((CC>1)*(P(CC)=0))
 Q
 ;
DIVPT ; T = P / DV (separate array; cleared when P is empty)
 S CC=P(0)
 I CC=0 K T
 S R=0
 F J=1:1:CC D
 . S I=CC+1-J
 . S V=R*B+P(I)
 . S T(I)=V\DV
 . S R=V#DV
 S T(0)=CC-((CC>1)*($G(T(CC))=0))
 Q
 ;
ADDST ; S = S + T (T read only up to T(0) — no stale limbs)
 S MX=$G(S(0))
 S CT=T(0)
 I CT>MX S MX=CT
 S CARRY=0
 F J=1:1:CT D
 . S V=$G(S(J))+T(J)+CARRY
 . S S(J)=V#B
 . S CARRY=(V>(B-1))
 F J=CT+1:1:MX D
 . S V=$G(S(J))+CARRY
 . S S(J)=V#B
 . S CARRY=(V>(B-1))
 S S(0)=MX+(CARRY>0)
 I CARRY>0 S S(MX+1)=CARRY
 Q
 ;
SUBST ; S = S - T (S >= T by alternating-series structure)
 S CT=T(0)
 S CARRY=0
 S MX=S(0)
 F J=1:1:CT D
 . S V=$G(S(J))-T(J)-CARRY
 . S CARRY=(0>V)
 . S S(J)=V+(B*CARRY)
 F J=CT+1:1:MX D
 . S V=$G(S(J))-CARRY
 . S CARRY=(0>V)
 . S S(J)=V+(B*CARRY)
 F J=1:1:MX S S(0)=S(0)-((S(0)>0)*(S(S(0))=0))
 Q
 ;
ADDSS ; S = S + S (doubling)
 S MX=S(0)
 S CARRY=0
 F J=1:1:MX D
 . S V=S(J)+S(J)+CARRY
 . S S(J)=V#B
 . S CARRY=(V>(B-1))
 S S(0)=MX+(CARRY>0)
 I CARRY>0 S S(MX+1)=CARRY
 Q
 ;
ADDUU ; U = U + U (doubling)
 S MX=U(0)
 S CARRY=0
 F J=1:1:MX D
 . S V=U(J)+U(J)+CARRY
 . S U(J)=V#B
 . S CARRY=(V>(B-1))
 S U(0)=MX+(CARRY>0)
 I CARRY>0 S U(MX+1)=CARRY
 Q
 ;
SUBUS ; U = U - S (U >= S; S read only up to S(0))
 S CT=S(0)
 S CARRY=0
 S MX=U(0)
 F J=1:1:CT D
 . S V=$G(U(J))-S(J)-CARRY
 . S CARRY=(0>V)
 . S U(J)=V+(B*CARRY)
 F J=CT+1:1:MX D
 . S V=$G(U(J))-CARRY
 . S CARRY=(0>V)
 . S U(J)=V+(B*CARRY)
 F J=1:1:MX S U(0)=U(0)-((U(0)>0)*(U(U(0))=0))
 Q
 ;
COPYUS ; U = S (copy)
 K U
 S CU=$G(S(0))
 F J=1:1:CU S U(J)=S(J)
 S U(0)=CU
 Q
 ;
PRINT ; decimal digits of U (limbs little-endian, 9 digits per limb)
 W !
 S CU=U(0)
 W U(CU)
 F J=1:1:CU-1 D
 . S I=CU-J
 . S V=U(I)
 . W $E("00000000",1,9-$L(V)),V
 W !
 Q
