PICKPT ; tools/pi_ckpt.m — checkpointable Machin pi (variant of tools/pi.m)
 ; pi = 16*atan(1/5) - 4*atan(1/239), base-10^9 limbs little-endian (same
 ; math as pi.m). Adds crash-resume: every CKI pair-steps an append-only
 ; checkpoint block is written to file CK:
 ;   P1|PH|K|SC|PC|SCval|PCval
 ;   S(1) ... S(SC)        (one limb per line)
 ;   P(1) ... P(PC)
 ;   END
 ; PH=1 means mid atan(1/5), PH=2 mid atan(1/239); K is the NEXT pair-step
 ; to execute (SAVE runs at the top of STEP, before PAIRSTEP). T is not
 ; saved — DIVPT recomputes it every PAIRSTEP. The final combine (4 U-doublings
 ; + 2 S-doublings + 1 sub) is seconds of work and is not checkpointed; a
 ; kill there resumes from the last PH=2 block.
 ; RES (optional): final digits go to that file via device 8.
 ; mJVM workarounds (#13/#16/#17/#18/#19): no QUIT inside FOR; no negative
 ; FOR; no IF inside dotted blocks; no multi-command same-line FOR body;
 ; one command per IF line; FOR exit by assigning the loop variable.
 S N=1000
 S G=25
 S N=N+G
 S B=1000000000
 S CK=""
 S RES=""
 S CKI=10000
 S PH=1
 S RK=0
 S GOT=0
 D LOADCK
 I PH=1 D PHASE1
 I PH=2 D PHASE2
 D COMBINE
 D PRINT
 Q
 ;
LOADCK ; restore checkpoint block from CK. The wrapper trims CK to the
 ; LAST COMPLETE block between attempts, so the file holds exactly one
 ; block: header + SC S-limbs + PC P-limbs + END (each W to a file device
 ; is followed by a blank line, hence the paired reads). Sets GOT=1, PH,
 ; RK, P, S when a valid block is found.
 K P,S
 S PH=1
 S RK=0
 S GOT=0
 I CK="" Q
 O 7:CK
 U 7
 R LINE
 I $E(LINE,1,3)="P1|" D PARSE
 U 0
 Q
 ;
PARSE
 S PP=$P(LINE,"|",2)
 S PK=$P(LINE,"|",3)
 S PSC=$P(LINE,"|",5)
 S PPC=$P(LINE,"|",7)
 S PUC=$P(LINE,"|",9)
 R BL
 F J=1:1:PSC D
 . R L2
 . R BL
 . S SS(J)=L2+0
 F J=1:1:PPC D
 . R L2
 . R BL
 . S SP(J)=L2+0
 F J=1:1:PUC D
 . R L2
 . R BL
 . S SU(J)=L2+0
 R LINE
 I LINE="END" D COMMIT
 U 0
 Q
 ;
COMMIT
 S GOT=1
 S PH=PP
 S RK=PK
 K S,P,U
 S S(0)=PSC
 S P(0)=PPC
 S U(0)=PUC
 F J=1:1:PSC S S(J)=SS(J)
 F J=1:1:PPC S P(J)=SP(J)
 F J=1:1:PUC S U(J)=SU(J)
 Q
 ;
SAVE ; append checkpoint block for current PH,K,P,S,U to CK.
 ; Header: P1|PH|K|SC|sc|PC|pc|UC|uc — U is saved only when PH=2 (U is
 ; undefined during phase 1; UC=0 then).
 I CK="" Q
 U 7
 W "P1|"_PH_"|"_K_"|SC|"_S(0)_"|PC|"_P(0)_"|UC|"_$G(U(0))
 F J=1:1:S(0) W S(J)
 F J=1:1:P(0) W P(J)
 I PH=2 D SAVEU
 W "END"
 U 0
 Q
 ;
SAVEU
 F J=1:1:U(0) W U(J)
 Q
 ;
PHASE1 ; atan(1/5) into U
 S X=5
 I RK=0 D INIT1
 S KM=N\2+2
 F K=RK:1:KM D STEP
 D COPYUS
 S PH=2
 S K=0
 S RK=0
 D INIT2
 D SAVE
 Q
 ;
PHASE2 ; atan(1/239)
 S X=239
 I RK=0 D INIT2
 S KM=N\2+2
 F K=RK:1:KM D STEP
 Q
 ;
INIT1 ; phase-1 fresh start: P=10^N/X (m=0 term), S=0, K=0, checkpoint
 S K=0
 D BUILD
 S DV=X
 D DIVPP
 K T
 S S=0
 S S(0)=0
 D SAVE
 Q
 ;
INIT2 ; phase-2 fresh start: P=10^N/X, S=0, X=239
 K P,T,S
 S X=239
 D BUILD
 S DV=X
 D DIVPP
 K T
 S S=0
 S S(0)=0
 Q
 ;
STEP
 I K#CKI=0 D SAVE
 D PAIRSTEP
 Q
 ;
COMBINE ; pi*10^N = 16*U - 4*S
 D ADDUU
 D ADDUU
 D ADDUU
 D ADDUU
 D ADDSS
 D ADDSS
 D SUBUS
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
PRINT ; decimal digits of U — to RES file (device 8) if set, else stdout
 I RES'="" O 8:RES
 I RES'="" U 8
 W !
 S CU=U(0)
 W U(CU)
 F J=1:1:CU-1 D
 . S I=CU-J
 . S V=U(I)
 . W $E("00000000",1,9-$L(V)),V
 W !
 I RES'="" U 0
 Q
