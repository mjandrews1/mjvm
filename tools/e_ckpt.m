ECKPT ; tools/e_ckpt.m — checkpointable variant of tools/e.m (P1d workstream D)
 ; e = sum_{k>=0} 1/k!, base-10^9 limbs little-endian (same math as e.m).
 ; Adds crash-resume: every CKI terms an append-only checkpoint block is
 ; written to file CK:
 ;   E1|K|SC|PC
 ;   S(1) ... S(SC)        (one limb per line)
 ;   P(1) ... P(PC)
 ;   END
 ; On startup the LAST COMPLETE block is restored (a torn final block from
 ; a kill mid-write is ignored), so a killed/rebooted run resumes at K
 ; instead of restarting from k=1.
 ; RES (optional): final digits go to that file via device 8, so the wrapper
 ; can rm it between attempts and stdout only ever carries progress dots.
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
 S RK=1
 S K=1
 S GOT=0
 D LOADCK
 I GOT=0 D BUILD
 I GOT=0 D COPYPS
 I GOT=0 D SAVE
 F K=RK:1:N D TERM
 D PRINT
 Q
 ;
LOADCK ; restore checkpoint block from CK. The wrapper trims CK to the
 ; LAST COMPLETE block between attempts, so the file holds exactly one
 ; block: header + SC S-limbs + PC P-limbs + END (each W to a file device
 ; is followed by a blank line, hence the paired reads). Sets GOT=1, RK,
 ; P, S when a valid block is found.
 K P,S
 S RK=1
 S GOT=0
 I CK="" Q
 O 7:CK
 U 7
 R LINE
 I $E(LINE,1,3)="E1|" D PARSE
 U 0
 Q
 ;
PARSE
 S PK=$P(LINE,"|",2)
 S PSC=$P(LINE,"|",4)
 S PPC=$P(LINE,"|",6)
 R BL
 F J=1:1:PSC D
 . R L2
 . R BL
 . S SS(J)=L2+0
 F J=1:1:PPC D
 . R L2
 . R BL
 . S SP(J)=L2+0
 R LINE
 I LINE="END" D COMMIT
 U 0
 Q
 ;
COMMIT
 S GOT=1
 S RK=PK
 K S,P
 S S(0)=PSC
 S P(0)=PPC
 F J=1:1:PSC S S(J)=SS(J)
 F J=1:1:PPC S P(J)=SP(J)
 Q
 ;
SAVE ; append checkpoint block for current K,P,S to CK
 I CK="" Q
 U 7
 W "E1|"_K_"|SC|"_S(0)_"|PC|"_P(0)
 F J=1:1:S(0) W S(J)
 F J=1:1:P(0) W P(J)
 W "END"
 U 0
 Q
 ;
TERM ; P = P/K ; S = S+P. Both steps no-op once P is empty.
 I K#CKI=0 D SAVE
 I K#1000=0 W "."
 S DV=K
 D DIVPP
 D ADDPS
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
PRINT ; decimal digits of S — to RES file (device 8) if set, else stdout
 I RES'="" O 8:RES
 I RES'="" U 8
 W !
 S CU=S(0)
 W S(CU)
 F J=1:1:CU-1 D
 . S I=CU-J
 . S V=S(I)
 . W $E("00000000",1,9-$L(V)),V
 W !
 I RES'="" U 0
 Q
