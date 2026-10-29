SYMDIFF ; tools/symdiff.m — symbolic differentiation in M
 ; P1d workstream D (algebra suite). Parses a polynomial in x
 ; (integer coefficients, nonneg integer exponents, forms like
 ; "3*x^2", "x^3", "-4*x", "7"), differentiates it, and prints the
 ; canonical result. Terms are stored exponent-keyed in globals with
 ; NEGATED exponent keys, so ascending $ORDER yields descending
 ; exponents — canonical print needs no sort.
 ;
 ; Engine surfaces stressed: char-scan parse (GOTO state machine,
 ; no known-bug constructs), numeric-subscript global merges, $O
 ; traversal, numeric/string coercion of digit chars, sign handling.
 ; Driver self-checks first+second derivatives and round-trips
 ; (reparse the printed derivative, differentiate again).
 ;
TEST ; (driver — output accumulated into one WRITE for ORACLE use)
 S OUT=""
 S X="3*x^2+5*x+7" D PARSE D DIFF D OUTP
 I R="6*x+5" S OUT=OUT_"T1 ok d(3*x^2+5*x+7)=6*x+5"_$C(10)
 I R'="6*x+5" S OUT=OUT_"T1 FAIL got "_R_$C(10)
 S X="x^4-x^3+x" D PARSE D DIFF D OUTP
 I R="4*x^3-3*x^2+1" S OUT=OUT_"T2 ok d(x^4-x^3+x)=4*x^3-3*x^2+1"_$C(10)
 I R'="4*x^3-3*x^2+1" S OUT=OUT_"T2 FAIL got "_R_$C(10)
 S X="7" D PARSE D DIFF D OUTP
 I R="0" S OUT=OUT_"T3 ok d(7)=0"_$C(10)
 I R'="0" S OUT=OUT_"T3 FAIL got "_R_$C(10)
 S X="x^3-6*x^2+8*x-1" D PARSE D DIFF D OUTP
 I R="3*x^2-12*x+8" S OUT=OUT_"T4 ok d(x^3-6*x^2+8*x-1)=3*x^2-12*x+8"_$C(10)
 I R'="3*x^2-12*x+8" S OUT=OUT_"T4 FAIL got "_R_$C(10)
 S X=R D PARSE D DIFF D OUTP
 I R="6*x-12" S OUT=OUT_"T5 ok round-trip d2=6*x-12"_$C(10)
 I R'="6*x-12" S OUT=OUT_"T5 FAIL got "_R_$C(10)
 S X="-x^2+3*x" D PARSE D DIFF D OUTP
 I R="-2*x+3" S OUT=OUT_"T6 ok d(-x^2+3*x)=-2*x+3"_$C(10)
 I R'="-2*x+3" S OUT=OUT_"T6 FAIL got "_R_$C(10)
 S X="x^2+x^2" D PARSE D DIFF D OUTP
 I R="4*x" S OUT=OUT_"T7 ok d(x^2+x^2)=4*x (term merge)"_$C(10)
 I R'="4*x" S OUT=OUT_"T7 FAIL got "_R_$C(10)
 S X="5*x^3-2*x^2+x-9" D PARSE D DIFF D OUTP
 I R="15*x^2-4*x+1" S OUT=OUT_"T8 ok d(5*x^3-2*x^2+x-9)=15*x^2-4*x+1"_$C(10)
 I R'="15*x^2-4*x+1" S OUT=OUT_"T8 FAIL got "_R_$C(10)
 W OUT
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
DIFF ; ^SDT(-E)=C  ->  ^SDD(-(E-1))=C*E ; constants dropped
 K ^SDD
 S KY=""
DFL ;
 S KY=$O(^SDT(KY))
 I KY="" G DFD
 S E=-KY
 I E=0 G DFL
 S C=^SDT(KY)
 S ^SDD(KY+1)=C*E
 G DFL
DFD ;
 Q
 ;
OUTP ; canonical print of ^SDD(-E)=C into R (descending E via ascending key)
 S R=""
 S FRST=1
 S KY=""
OPL ;
 S KY=$O(^SDD(KY))
 I KY="" G OPD
 S C=^SDD(KY)
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
