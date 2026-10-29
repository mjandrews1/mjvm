PEMDASM ; tools/pemdasm.m — PEMDAS precedence evaluator written in M
 ; P1d workstream D. M itself has NO operator precedence (strictly
 ; left-to-right); this routine implements conventional PEMDAS with a
 ; recursive-descent parser to stress string ops ($E/$L), DO recursion,
 ; NEW scoping, and GOTO loops (no QUIT-in-FOR, no dotted-block IF, no
 ; multi-command lines — known mJVM bug classes avoided).
 ;
 ; Grammar (PEMDAS conventions, matching the Java PemdasEval semantics):
 ;   EXPR  := ADD, must consume all of X
 ;   ADD   := MUL ( (+|-) MUL )*          left-assoc
 ;   MUL   := UN   ( (*|/)  UN  )*         left-assoc, 1/0=0 (M rule)
 ;   UN    := (-|+)* POW                   unary binds looser than **
 ;   POW   := PRIM ( '**' UN )?            right-assoc: 2**3**2=512
 ;   PRIM  := number | '(' ADD ')'
 ; so -2**2 = -(2**2) = -4. Integer arithmetic only (M numeric).
 ;
 ; Entry: S X="<expr>" D EXPR  ->  V = value ("ERR" on parse failure).
 ; Driver below self-checks a dozen expressions, one line per verdict.
 ;
TEST W !
 S X="2+3*4" D EXPR
 I V=14 W "T01 ok 2+3*4=14"
 I V'=14 W "T01 FAIL 2+3*4"
 W !
 S X="(2+3)*4" D EXPR
 I V=20 W "T02 ok (2+3)*4=20"
 I V'=20 W "T02 FAIL (2+3)*4"
 W !
 S X="2**3**2" D EXPR
 I V=512 W "T03 ok 2**3**2=512 (right-assoc)"
 I V'=512 W "T03 FAIL 2**3**2"
 W !
 S X="-2**2" D EXPR
 I V=-4 W "T04 ok -2**2=-4"
 I V'=-4 W "T04 FAIL -2**2"
 W !
 S X="8/4/2" D EXPR
 I V=1 W "T05 ok 8/4/2=1 (left-assoc)"
 I V'=1 W "T05 FAIL 8/4/2"
 W !
 S X="1/0" D EXPR
 I V=0 W "T06 ok 1/0=0 (M rule)"
 I V'=0 W "T06 FAIL 1/0"
 W !
 S X=" ( 2 + 3 ) * 4 " D EXPR
 I V=20 W "T07 ok spaces/parens=20"
 I V'=20 W "T07 FAIL spaces/parens"
 W !
 S X="10-4-3" D EXPR
 I V=3 W "T08 ok 10-4-3=3"
 I V'=3 W "T08 FAIL 10-4-3"
 W !
 S X="2**0" D EXPR
 I V=1 W "T09 ok 2**0=1"
 I V'=1 W "T09 FAIL 2**0"
 W !
 S X="-3--2" D EXPR
 I V=-1 W "T10 ok -3--2=-1"
 I V'=-1 W "T10 FAIL -3--2"
 W !
 S X="((1+2)*(3+4))**2" D EXPR
 I V=441 W "T11 ok ((1+2)*(3+4))**2=441"
 I V'=441 W "T11 FAIL nested parens"
 W !
 S X="3*(2+3**2)-5" D EXPR
 I V=28 W "T12 ok 3*(2+3**2)-5=28"
 I V'=28 W "T12 FAIL mixed"
 W !
 I (2+3*4)=20 W "T13 ok default M left-to-right 2+3*4=20"
 I (2+3*4)'=20 W "T13 FAIL default M precedence changed"
 W !
 Q
 ;
EXPR ; V = value of whole X; ERR if trailing garbage
 S POS=1
 D SKIP
 D ADD
 D SKIP
 I POS'>$L(X) S V="ERR"
 Q
 ;
ADD ; V = MUL ( (+|-) MUL )*
 NEW L,CH
 D MUL
 S L=V
ADDL D SKIP
 S CH=$E(X,POS,POS)
 I CH="+" G ADDPLUS
 I CH="-" G ADDMIN
 S V=L
 Q
ADDPLUS ;
 S POS=POS+1
 D MUL
 S L=L+V
 G ADDL
ADDMIN ;
 S POS=POS+1
 D MUL
 S L=L-V
 G ADDL
 ;
MUL ; V = UN ( (*|/) UN )*
 NEW L,CH
 D UN
 S L=V
MULL D SKIP
 S CH=$E(X,POS,POS)
 I CH="*" G MULSTAR
 I CH="/" G MULSLASH
 S V=L
 Q
MULSTAR ;
 S POS=POS+1
 D UN
 S L=L*V
 G MULL
MULSLASH ;
 S POS=POS+1
 D UN
 I V=0 S L=0
 I V'=0 S L=L\V
 G MULL
 ;
UN ; V = (-|+)* POW
 NEW SG,CH
 S SG=1
UNL D SKIP
 S CH=$E(X,POS,POS)
 I CH="-" G UNMIN
 I CH="+" G UNPLUS
 D POW
 S V=SG*V
 Q
UNMIN ;
 S SG=-SG
 S POS=POS+1
 G UNL
UNPLUS ;
 S POS=POS+1
 G UNL
 ;
POW ; V = PRIM ('**' UN)?   right-assoc, integer exponent >= 0
 NEW L,CH,R,J
 D PRIM
 S L=V
 D SKIP
 I $E(X,POS,POS+1)'="**" G POWDONE
 S POS=POS+2
 D UN
 S R=V
 S V=1
 F J=1:1:R S V=V*L
POWDONE
 Q
 ;
PRIM ; V = number | '(' ADD ')'
 NEW CH
 D SKIP
 S CH=$E(X,POS,POS)
 I CH="(" G PRIMPAR
 I "0123456789"'[CH G PRIMERR
PRIMNUM ;
 S V=""
PRIMNL I POS>$L(X) G PRIMND
 I "0123456789"'[$E(X,POS,POS) G PRIMND
 S V=V_$E(X,POS,POS)
 S POS=POS+1
 G PRIMNL
PRIMPAR ;
 S POS=POS+1
 D ADD
 D SKIP
 I $E(X,POS,POS)'=")" G PRIMERR
 S POS=POS+1
 G PRIMND
PRIMERR ;
 S V="ERR"
PRIMND
 Q
 ;
SKIP ; advance POS past blanks
SKIPL I POS>$L(X) G SKIPD
 I $E(X,POS,POS)'=" " G SKIPD
 S POS=POS+1
 G SKIPL
SKIPD
 Q
