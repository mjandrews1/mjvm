E ; tools/e.m — Euler's constant e = sum_{k>=0} 1/k!
 ; P1d workstream D (known-answer math suite), modeled on tools/pi.m.
 ; Fixed point: S = 10^N (the k=0 term is 1); P = 10^N. Then for
 ; k=1,2,...: P = P/k (integer division), S = S+P. Terms vanish once
 ; k! > 10^N, so K=1:1:N is a safe loop bound (k! > 10^N well before
 ; k=N for every N >= 30; guard digits absorb series truncation).
 ; Prints floor(e*10^N): digit 1 is "2"; digits 2..N+1 are the first N
 ; decimal digits of e — digit N+1 is the millionth digit at N=1000000
 ; (ignore the last G+~1 printed digits).
 ;
 ; Big integers: base-10^9 limbs, little-endian (same as pi.m). Division
 ; only by small integers — no general bignum division.
 ;
 ; mJVM workarounds (issues #13/#16/#17/#19): no QUIT inside FOR; no
 ; negative FOR increments; no IF inside dotted blocks; no multi-command
 ; same-line FOR bodies. Empty P makes DIVPP and ADDPS cheap no-ops, so
 ; the K=1:1:N bound costs almost nothing past the last real term.
 S N=30
 S G=25
 S N=N+G
 S B=1000000000
 D BUILD
 D COPYPS
 F K=1:1:N D TERM
 D PRINT
 W C
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
TERM ; P = P/K ; S = S+P. Both steps no-op once P is empty.
 I K#1000=0 W "."
 S DV=K
 D DIVPP
 D ADDPS
 Q
 ;
DIVPP ; P = P / DV (in place; DV >= 2). Quotient of a normalized number
 ; by a small int loses at most one leading limb.
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
ADDPS ; S = S + P (P read only up to P(0)). Skips the add when P empty
 ; so the tail of the K loop is nearly free.
 S CT=P(0)
 I CT>0 D ADDP2
 Q
 ;
ADDP2 ; S = S + P, both nonempty
 S MX=$G(S(0))
 S CARRY=0
 F J=1:1:CT D
 . S V=$G(S(J))+P(J)+CARRY
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
COPYPS ; S = P (copy)
 K S
 S CU=$G(P(0))
 F J=1:1:CU S S(J)=P(J)
 S S(0)=CU
 Q
 ;
PRINT ; decimal digits of S (limbs little-endian, 9 digits per limb)
 S CU=S(0)
 S C=S(CU)
 F J=1:1:CU-1 D
 . S I=CU-J
 . S V=S(I)
 . S C=C_$E("00000000",1,9-$L(V))_V
 Q
