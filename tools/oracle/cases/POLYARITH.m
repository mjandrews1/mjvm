POLYARITH ; tools/polyarith.m — polynomial add and multiply in M
 ; P1d workstream D (algebra suite). Reuses the symdiff.m parser;
 ; terms are exponent-keyed ^P?(-exp)=coef, so the product of terms
 ; at keys KA,KB lands at key KA+KB — no sign juggling. ADD walks
 ; both tables with $O; MUL is a nested $O double loop. Self-checks
 ; against the binomial and difference-of-squares identities.
 ;
TEST ;
 S OUT=""
 S X="x+1" S Y="x+2" D PMUL
 I R="x^2+3*x+2" S OUT=OUT_"M1 ok (x+1)*(x+2)=x^2+3*x+2"_$C(10)
 I R'="x^2+3*x+2" S OUT=OUT_"M1 FAIL got "_R_$C(10)
 S X="x-1" S Y="x+1" D PMUL
 I R="x^2-1" S OUT=OUT_"M2 ok (x-1)*(x+1)=x^2-1"_$C(10)
 I R'="x^2-1" S OUT=OUT_"M2 FAIL got "_R_$C(10)
 S X="3*x^2+2*x" S Y="x" D PMUL
 I R="3*x^3+2*x^2" S OUT=OUT_"M3 ok (3*x^2+2*x)*x=3*x^3+2*x^2"_$C(10)
 I R'="3*x^3+2*x^2" S OUT=OUT_"M3 FAIL got "_R_$C(10)
 S X="2*x+1" S Y="2*x+1" D PMUL
 I R="4*x^2+4*x+1" S OUT=OUT_"M4 ok (2*x+1)^2=4*x^2+4*x+1"_$C(10)
 I R'="4*x^2+4*x+1" S OUT=OUT_"M4 FAIL got "_R_$C(10)
 S X="3*x^2+2*x" S Y="5*x+1" D PADD
 I R="3*x^2+7*x+1" S OUT=OUT_"A1 ok (3*x^2+2*x)+(5*x+1)=3*x^2+7*x+1"_$C(10)
 I R'="3*x^2+7*x+1" S OUT=OUT_"A1 FAIL got "_R_$C(10)
 S X="x^2" S Y="-x^2+x" D PADD
 I R="x" S OUT=OUT_"A2 ok (x^2)+(-x^2+x)=x (cancellation)"_$C(10)
 I R'="x" S OUT=OUT_"A2 FAIL got "_R_$C(10)
 W OUT
 Q
 ;
PMUL ; R = X * Y: X -> ^PXT, Y -> ^PYT, product -> ^SDT, print
 K ^PXT K ^PYT
 D PARSE
 D MVPXT
 S X=Y
 D PARSE
 D MVPYT
 K ^SDT
 S KA=""
MULL ;
 S KA=$O(^PXT(KA))
 I KA="" G MULD
 S CA=^PXT(KA)
 S KB=""
MULL2 ;
 S KB=$O(^PYT(KB))
 I KB="" G MULL
 S CB=^PYT(KB)
 S ^SDT(KA+KB)=$G(^SDT(KA+KB))+(CA*CB)
 G MULL2
MULD ;
 D OUTP
 Q
 ;
PADD ; R = X + Y
 K ^PXT K ^PYT
 D PARSE
 D MVPXT
 S X=Y
 D PARSE
 D MVPYT
 K ^SDT
 S KA=""
ADDL ;
 S KA=$O(^PXT(KA))
 I KA="" G ADDL2
 S ^SDT(KA)=$G(^SDT(KA))+^PXT(KA)
 G ADDL
ADDL2 ;
 S KA=$O(^PYT(KA))
 I KA="" G ADDD
 S ^SDT(KA)=$G(^SDT(KA))+^PYT(KA)
 G ADDL2
ADDD ;
 D OUTP
 Q
 ;
MVPXT ; move ^SDT -> ^PXT
 S KY=""
MVPXTL ;
 S KY=$O(^SDT(KY))
 I KY="" G MVPXTD
 S ^PXT(KY)=^SDT(KY)
 K ^SDT(KY)
 G MVPXTL
MVPXTD ;
 Q
 ;
MVPYT ; move ^SDT -> ^PYT
 S KY=""
MVPYTL ;
 S KY=$O(^SDT(KY))
 I KY="" G MVPYTD
 S ^PYT(KY)=^SDT(KY)
 K ^SDT(KY)
 G MVPYTL
MVPYTD ;
 Q
 ;
PARSE ; parse X into ^SDT(-exp)=coef (merged). Terms: [+|-]* coef [*x[^exp]] | x[^exp]
 K ^SDT
 S POS=1
PRTERM ;
 I POS>$L(X) G PRDONE
 S SGN=1
 S CFD=0
 S EXPV=0
 S HASX=0
 S BX=0
PRSGN ;
 I POS>$L(X) G PRCOMMIT
 S CH=$E(X,POS,POS)
 I CH="+" G PRPLUS
 I CH="-" G PRMINUS
 G PRCOEF
PRPLUS ;
 S POS=POS+1
 G PRSGN
PRMINUS ;
 S SGN=-SGN
 S POS=POS+1
 G PRSGN
PRCOEF ;
 I POS>$L(X) G PRCOMMIT
 S CH=$E(X,POS,POS)
 I "0123456789"'[CH G PRXA
 S CFD=CFD*10+CH
 S POS=POS+1
 G PRCOEF
PRXA ;
 I POS>$L(X) G PRCOMMIT
 S CH=$E(X,POS,POS)
 I CH="x" G PRBXSET
 I CH'="*" G PRCOMMIT
 S POS=POS+1
 S BX=0
PRXB ;
 I $E(X,POS,POS)'="x" G PRERR
 S POS=POS+1
 S HASX=1
 I BX=1 S CFD=1
 I $E(X,POS,POS)'="^" G PRCOMMIT
 S POS=POS+1
PREXP ;
 I POS>$L(X) G PRERR
 S CH=$E(X,POS,POS)
 I "0123456789"'[CH G PRERR
PREXPL ;
 S EXPV=EXPV*10+CH
 S POS=POS+1
 I POS>$L(X) G PRCOMMIT
 I "0123456789"[$E(X,POS,POS) G PREXPC
 G PRCOMMIT
PREXPC ;
 S CH=$E(X,POS,POS)
 G PREXPL
PRBXSET ;
 S BX=1
 G PRXB
PRCOMMIT ;
 I HASX=0 G PRCONST
 I EXPV=0 S EXPV=1
 G PRINS
PRCONST ;
 S EXPV=0
PRINS ;
 S CF=SGN*CFD
 S ^SDT(-EXPV)=$G(^SDT(-EXPV))+CF
 G PRTERM
PRERR ;
 S OUT=OUT_"PARSE-ERR at "_POS_$C(10)
PRDONE ;
 Q
 ;
OUTP ; canonical print of ^SDT(-E)=C into R (descending E via ascending key)
 S R=""
 S FRST=1
 S KY=""
OPL ;
 S KY=$O(^SDT(KY))
 I KY="" G OPD
 S C=^SDT(KY)
 I C=0 G OPL
 S E=-KY
 I FRST=1 G OPNF
 I C<0 G OPNEG
 S R=R_"+"
 G OPAP
OPNEG ;
 S R=R_"-"
 S C=-C
 G OPAP
OPNF ;
 S FRST=0
 I C<0 G OPNFN
 G OPAP
OPNFN ;
 S R=R_"-"
 S C=-C
OPAP ;
 I C'=1 G OPCF
 I E=0 G OPONE
 S R=R_"x"
 G OPXE
OPONE ;
 S R=R_"1"
 G OPNXT
OPCF ;
 S R=R_C
 I E=0 G OPNXT
 S R=R_"*x"
OPXE ;
 I E=1 G OPNXT
 S R=R_"^"_E
OPNXT ;
 G OPL
OPD ;
 I R="" S R="0"
 Q
