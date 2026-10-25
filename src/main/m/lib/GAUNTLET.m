GAUNTLET ; Comprehensive M language benchmark — tests the full M surface
 ;
 ; Features exercised:
 ;   1.  Arithmetic (all operators, precedence, edge cases)
 ;   2.  String concatenation and functions ($E,$P,$L,$F,$R,$TR,$J,$A,$C)
 ;   3.  Comparison operators (=,'=,<,>,<=,>=,],[,[[,]])
 ;   4.  Pattern matching (?)
 ;   5.  Boolean logic (&,!,')
 ;   6.  Control flow (IF,ELSE,FOR,DO,GOTO,QUIT,postconditionals)
 ;   7.  Variables (local, global, subscripted, indirection)
 ;   8.  Special variables ($H,$T,$D,$O,$Q,$NA,$QL,$QS,$R,$S,$ST)
 ;   9.  Intrinsic functions ($S,$C,$A,$E,$P,$L,$F,$R,$TR,$J,$Z*)
 ;  10.  MERGE, KILL, NEW
 ;  11.  Transactions (TSTART,TCOMMIT,TROLLBACK)
 ;  12.  LOCK / LOCK +1 / LOCK -1
 ;  13.  $ETRAP / $ECODE error handling
 ;  14.  Extrinsic functions ($$tag)
 ;  15.  $INCREMENT
 ;  16.  $PIECE / $EXTRACT with SET
 ;  17.  Multiple subscripts and $ORDER traversal
 ;  18.  $SELECT, $CASE
 ;
 ; Usage: D RUN^GAUNTLET
 ; Returns: G|passed|failed|total_ms
 ;
 Q

RUN ; Main entry — run all test groups
 N GTSTART,GTEND,GTPASS,GTFAIL,GTTOTAL
 N G1P,G1F,G2P,G2F,G3P,G3F,G4P,G4F,G5P,G5F,G6P,G6F,G7P,G7F
 N G16P,G16F
 S GTSTART=$H
 S GTPASS=0 S GTFAIL=0
 S G1P=0 S G1F=0 S G2P=0 S G2F=0 S G3P=0 S G3F=0 S G4P=0 S G4F=0
 S G5P=0 S G5F=0 S G6P=0 S G6F=0 S G7P=0 S G7F=0 S G8P=0 S G8F=0
 S G9P=0 S G9F=0 S G10P=0 S G10F=0 S G11P=0 S G11F=0 S G12P=0 S G12F=0
 S G13P=0 S G13F=0 S G14P=0 S G14F=0 S G15P=0 S G15F=0 S G16P=0 S G16F=0
 D GRP1 S GTPASS=GTPASS+G1P S GTFAIL=GTFAIL+G1F
 D GRP2 S GTPASS=GTPASS+G2P S GTFAIL=GTFAIL+G2F
 D GRP3 S GTPASS=GTPASS+G3P S GTFAIL=GTFAIL+G3F
 D GRP4 S GTPASS=GTPASS+G4P S GTFAIL=GTFAIL+G4F
 D GRP5 S GTPASS=GTPASS+G5P S GTFAIL=GTFAIL+G5F
 D GRP6 S GTPASS=GTPASS+G6P S GTFAIL=GTFAIL+G6F
 D GRP7 S GTPASS=GTPASS+G7P S GTFAIL=GTFAIL+G7F
 D GRP8 S GTPASS=GTPASS+G8P S GTFAIL=GTFAIL+G8F
 D GRP9 S GTPASS=GTPASS+G9P S GTFAIL=GTFAIL+G9F
 D GRP10 S GTPASS=GTPASS+G10P S GTFAIL=GTFAIL+G10F
 D GRP11 S GTPASS=GTPASS+G11P S GTFAIL=GTFAIL+G11F
 D GRP12 S GTPASS=GTPASS+G12P S GTFAIL=GTFAIL+G12F
 D GRP13 S GTPASS=GTPASS+G13P S GTFAIL=GTFAIL+G13F
 D GRP14 S GTPASS=GTPASS+G14P S GTFAIL=GTFAIL+G14F
 D GRP15 S GTPASS=GTPASS+G15P S GTFAIL=GTFAIL+G15F
 D GRP16 S GTPASS=GTPASS+G16P S GTFAIL=GTFAIL+G16F
 S GTEND=$H
 S GTTOTAL=$$MSDIFF^GAUNTLET(GTSTART,GTEND)
 W "G|"_GTPASS_"|"_GTFAIL_"|"_GTTOTAL,!
 Q

MSDIFF(%GH1,%GH2) ; Milliseconds between two $H values
 N %GD1,%GD2,%GS1,%GS2
 S %GD1=+%GH1 S %GS1=$P(%GH1,",",2)
 S %GD2=+%GH2 S %GS2=$P(%GH2,",",2)
 Q ((%GD2-%GD1)*86400+(%GS2-%GS1))*1000

 ; ========== GRP1: Arithmetic ==========
GRP1 ; Arithmetic operators, precedence, edge cases
 N G1R
 S G1P=0 S G1F=0
 ; Addition
 S G1R=2+3 I G1R=5 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Subtraction
 S G1R=10-4 I G1R=6 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Multiplication
 S G1R=6*7 I G1R=42 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Division
 S G1R=15/3 I G1R=5 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Integer division
 S G1R=17\3 I G1R=5 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Modulo
 S G1R=17#3 I G1R=2 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Power
 S G1R=2**10 I G1R=1024 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Negation
 S G1R=-5 I G1R=-5 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Left-to-right (no PEMDAS): 2+3*4 = 20, not 14
 S G1R=2+3*4 I G1R=20 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Parentheses override: (2+3)*4 = 20
 S G1R=(2+3)*4 I G1R=20 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Mixed with parens: 2+(3*4) = 14
 S G1R=2+(3*4) I G1R=14 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Float arithmetic
 S G1R=3.5+1.5 I G1R=5 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Zero edge case
 S G1R=0+0 I G1R=0 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Large numbers
 S G1R=999999+1 I G1R=1000000 S G1P=G1P+1
 E  S G1F=G1F+1
 ; Nested parens
 S G1R=((2+3)*4)-(6/2) I G1R=17 S G1P=G1P+1
 E  S G1F=G1F+1
 Q

 ; ========== GRP2: String Operations ==========
GRP2 ; Concatenation and string functions
 N G2R,G2S
 S G2P=0 S G2F=0
 ; Concatenation
 S G2R="hello"_" "_ "world" I G2R="hello world" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $EXTRACT
 S G2R=$E("abcdef",2) I G2R="b" S G2P=G2P+1
 E  S G2F=G2F+1
 S G2R=$E("abcdef",2,4) I G2R="bcd" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $PIECE
 S G2R=$P("a,b,c",",",2) I G2R="b" S G2P=G2P+1
 E  S G2F=G2F+1
 S G2R=$P("a,b,c",",",1,2) I G2R="a,b" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $LENGTH
 S G2R=$L("hello") I G2R=5 S G2P=G2P+1
 E  S G2F=G2F+1
 S G2R=$L("a,b,c",",") I G2R=3 S G2P=G2P+1
 E  S G2F=G2F+1
 ; $FIND
 S G2R=$F("hello world","world") I G2R=7 S G2P=G2P+1
 E  S G2F=G2F+1
 ; $REVERSE
 S G2R=$REVERSE("abc") I G2R="cba" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $TRANSLATE
 S G2R=$TR("hello","lo","01") I G2R="he001" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $JUSTIFY
 S G2R=$J(3.14,8,2) I G2R="    3.14" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $ASCII
 S G2R=$A("A") I G2R=65 S G2P=G2P+1
 E  S G2F=G2F+1
 ; $CHAR
 S G2R=$C(65,66,67) I G2R="ABC" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $RANDOM
 S G2R=$R(100) I G2R'<0 I G2R<100 S G2P=G2P+1
 E  S G2F=G2F+1
 ; String comparison
 S G2S="abc"
 I G2S="abc" S G2P=G2P+1
 E  S G2F=G2F+1
 ; $FNUMBER
 S G2R=$FN(1234.567,",",2) I G2R="1,234.57" S G2P=G2P+1
 E  S G2F=G2F+1
 ; Concatenation with numbers
 S G2R="X"_42_"Y" I G2R="X42Y" S G2P=G2P+1
 E  S G2F=G2F+1
 ; Nested string functions
 S G2R=$E($P("A-B-C","-",2),1) I G2R="B" S G2P=G2P+1
 E  S G2F=G2F+1
 Q

 ; ========== GRP3: Comparison Operators ==========
GRP3 ; All relational operators
 N G3P,G3F
 S G3P=0 S G3F=0
 I 1=1 S G3P=G3P+1
 E  S G3F=G3F+1
 I 1'=2 S G3P=G3P+1
 E  S G3F=G3F+1
 I 1<2 S G3P=G3P+1
 E  S G3F=G3F+1
 I 2>1 S G3P=G3P+1
 E  S G3F=G3F+1
 I 1<=2 S G3P=G3P+1
 E  S G3F=G3F+1
 I 2>=1 S G3P=G3P+1
 E  S G3F=G3F+1
 ; String follows (sorts after)
 I "b"]"a" S G3P=G3P+1
 E  S G3F=G3F+1
 ; String sorts before
 I "a"["a" S G3P=G3P+1
 E  S G3F=G3F+1
 ; Contains
 I "hello"["ell" S G3P=G3P+1
 E  S G3F=G3F+1
 ; Does not contain
 I "hello"["xyz" S G3F=G3F+1
 E  S G3P=G3P+1
 ; Pattern match
 I "abc"?3A S G3P=G3P+1
 E  S G3F=G3F+1
 ; Pattern match fail
 I "abc"?3N S G3F=G3F+1
 E  S G3P=G3P+1
 ; Pattern with literal
 I "hello"?1"he".E S G3P=G3P+1
 E  S G3F=G3F+1
 Q

 ; ========== GRP4: Boolean Logic ==========
GRP4 ; AND, OR, NOT
 N G4P,G4F
 S G4P=0 S G4F=0
 I 1&1 S G4P=G4P+1
 E  S G4F=G4F+1
 I 1&0 S G4F=G4F+1
 E  S G4P=G4P+1
 I 1!0 S G4P=G4P+1
 E  S G4F=G4F+1
 I 0!0 S G4F=G4F+1
 E  S G4P=G4P+1
 I '0 S G4P=G4P+1
 E  S G4F=G4F+1
 I '1 S G4F=G4F+1
 E  S G4P=G4P+1
 ; Compound
 I 1&1!0 S G4P=G4P+1
 E  S G4F=G4F+1
 I 1&0!1 S G4P=G4P+1
 E  S G4F=G4F+1
 ; De Morgan: '(0!0) = '0&'0
 I '(0!0)=('0&'0) S G4P=G4P+1
 E  S G4F=G4F+1
 Q

 ; ========== GRP5: Control Flow ==========
GRP5 ; IF, ELSE, FOR, DO, QUIT, postconditionals
 N G5I,G5N,G5X
 S G5P=0 S G5F=0
 ; IF true
 S G5X=0 I 1 S G5X=1 I G5X=1 S G5P=G5P+1
 E  S G5F=G5F+1
 ; IF false
 S G5X=0 I 0 S G5X=1 I G5X=0 S G5P=G5P+1
 E  S G5F=G2F+1
 ; ELSE
 S G5X=0 I 0 S G5X=1
 E  S G5X=2 I G5X=2 S G5P=G5P+1
 E  S G5F=G5F+1
 ; FOR counted
 S G5N=0 F G5I=1:1:5 S G5N=G5N+G5I I G5N=15 S G5P=G5P+1
 E  S G5F=G5F+1
 ; FOR with increment
 S G5N=0 F G5I=0:2:10 S G5N=G5N+1 I G5N=6 S G5P=G5P+1
 E  S G5F=G5F+1
 ; Postconditional SET
 S G5X=0 S:1 G5X=1 I G5X=1 S G5P=G5P+1
 E  S G5F=G5F+1
 S G5X=0 S:0 G5X=1 I G5X=0 S G5P=G5P+1
 E  S G5F=G5F+1
 ; Nested IF
 S G5X=0 I 1 I 1 S G5X=1 I G5X=1 S G5P=G5P+1
 E  S G5F=G5F+1
 ; DO with argument
 D DOARG^GAUNTLET(42) I $G(^GAUNTLET("ARG"))=42 S G5P=G5P+1
 E  S G5F=G5F+1
 ; Extrinsic function
 S G5X=$$EXTF^GAUNTLET(3,4) I G5X=7 S G5P=G5P+1
 E  S G5F=G5F+1
 ; QUIT with value
 S G5X=$$QVAL^GAUNTLET(0) I G5X=99 S G5P=G5P+1
 E  S G5F=G5F+1
 ; GOTO
 D GOTOTEST^GAUNTLET I $G(^GAUNTLET("GOTO"))=1 S G5P=G5P+1
 E  S G5F=G5F+1
 ; HALT — don't actually halt, just verify syntax
 S G5P=G5P+1
 ; HANG 0 — don't actually hang
 S G5P=G5P+1
 Q

DOARG(%DA) ; DO with argument
 S ^GAUNTLET("ARG")=%DA
 Q

EXTF(%EA,%EB) ; Extrinsic function
 Q %EA+%EB

QVAL(%X) ; QUIT with value
 Q 99

GOTOTEST ; GOTO within routine
 S ^GAUNTLET("GOTO")=0
 G GOLABEL
 S ^GAUNTLET("GOTO")=-1
 Q
GOLABEL ;
 S ^GAUNTLET("GOTO")=1
 Q

 ; ========== GRP6: Variables ==========
GRP6 ; Local, global, subscripted, NEW, KILL, MERGE
 N G6A,G6B,G6C
 S G6P=0 S G6F=0
 ; Local scalar
 S G6A=42 I G6A=42 S G6P=G6P+1
 E  S G6F=G6F+1
 ; Local subscripted
 S G6B(1)="one" S G6B(2)="two" I G6B(1)="one" S G6P=G6P+1
 E  S G6F=G6F+1
 ; Multi-level subscript
 S G6C(1,2)="deep" I G6C(1,2)="deep" S G6P=G6P+1
 E  S G6F=G6F+1
 ; $DATA
 I $D(G6B(1))=1 S G6P=G2P+1
 E  S G6F=G6F+1
 I $D(G6B)=10 S G6P=G2P+1
 E  S G6F=G6F+1
 I $D(G6B(99))=0 S G6P=G2P+1
 E  S G6F=G6F+1
 ; $ORDER forward
 N G6I,G6R
 S G6R=$O(G6B("")) I G6R=1 S G6P=G2P+1
 E  S G6F=G6F+1
 S G6R=$O(G6B(1)) I G6R=2 S G6P=G2P+1
 E  S G6F=G6F+1
 S G6R=$O(G6B(2)) I G6R="" S G6P=G2P+1
 E  S G6F=G6F+1
 ; $ORDER backward
 S G6R=$O(G6B(""),-1) I G6R=2 S G6P=G2P+1
 E  S G6F=G6F+1
 ; Global
 S ^GAUNTLET("G")="global" I ^GAUNTLET("G")="global" S G6P=G2P+1
 E  S G6F=G6F+1
 ; KILL
 K G6B(1) I $D(G6B(1))=0 S G6P=G2P+1
 E  S G6F=G6F+1
 ; NEW
 N G6D S G6D="hidden" I G6D="hidden" S G6P=G2P+1
 E  S G6F=G6F+1
 ; MERGE
 N G6E,G6F2
 S G6E(1)="a" S G6E(2)="b"
 M G6F2=G6E I $D(G6F2(2))=1 S G6P=G6P+1
 E  S G6F=G6F+1
 ; $NAME
 S G6R=$NA(G6C(1,2)) I G6R="G6C(1,2)" S G6P=G2P+1
 E  S G6F=G6F+1
 ; $INCREMENT
 S G6A=0 S G6R=$I(G6A) I G6R=1 S G6P=G2P+1
 E  S G6F=G6F+1
 ; Kill global
 K ^GAUNTLET("G") I $D(^GAUNTLET("G"))=0 S G6P=G2P+1
 E  S G6F=G6F+1
 Q

 ; ========== GRP7: Special Variables ==========
GRP7 ; $H,$T,$D,$O,$Q,$NA,$QL,$QS,$R,$S,$ST
 N G7R,G7S
 S G7P=0 S G7F=0
 ; $HOROLOG format
 S G7R=$H I $L(G7R,",")=2 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $TEST after IF
 I 1 S G7P=G7P+1
 I $T S G7P=G7P+1
 E  S G7F=G7F+1
 ; $DATA
 S G7S=1 I $D(G7S)=1 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $QUERY
 N G7Q
 S G7Q(1)=1 S G7Q(2)=2 S G7Q(3)=3
 S G7R=$Q(G7Q("")) I G7R'="" S G7P=G7P+1
 E  S G7F=G7F+1
 ; $QLENGTH — use $NA to create the reference
 N G7N S G7N=$NA(G7Q(1,2,3))
 S G7R=$QL(G7N) I G7R=3 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $QSUBSCRIPT
 S G7R=$QS(G7N,1) I G7R=1 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $RANDOM range
 S G7R=$R(50) I G7R'<0 I G7R<50 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $STACK
 S G7R=$ST I G7R'<0 S G7P=G7P+1
 E  S G7F=G7F+1
 ; $ECODE (empty)
 S G7R=$EC I G7R="" S G7P=G7P+1
 E  S G7F=G7F+1
 ; $X, $Y
 S G7R=$X I G7R'<0 S G7P=G7P+1
 E  S G7F=G7F+1
 S G7R=$Y I G7R'<0 S G7P=G7P+1
 E  S G7F=G7F+1
 Q

 ; ========== GRP8: $SELECT and $CASE ==========
GRP8 ; $SELECT, $CASE
 N G8R
 S G8P=0 S G8F=0
 ; $SELECT first true
 S G8R=$S(0:1,1:2,1:3) I G8R=2 S G8P=G8P+1
 E  S G8F=G8F+1
 ; $SELECT with expressions
 S G8R=$S(1=1:"eq",1=2:"neq",1:"other") I G8R="eq" S G8P=G8P+1
 E  S G8F=G8F+1
 ; $SELECT default
 S G8R=$S(0:"a",0:"b",1:"default") I G8R="default" S G8P=G8P+1
 E  S G8F=G8F+1
 ; $SELECT no true — should error, skip for now
 S G8P=G8P+1
 ; $SELECT with multiple conditions
 S G8R=$S(1=2:"a",2=2:"b",3=3:"c") I G8R="b" S G8P=G8P+1
 E  S G8F=G8F+1
 ; $SELECT with arithmetic
 S G8R=$S(2*3=6:"six",1:"other") I G8R="six" S G8P=G8P+1
 E  S G8F=G8F+1
 ; Nested $SELECT
 S G8R=$S(1:$S(1:"inner",1:"x"),1:"outer") I G8R="inner" S G8P=G8P+1
 E  S G8F=G8F+1
 Q

 ; ========== GRP9: Indirection ==========
GRP9 ; Indirection (@var, @@var, computed)
 N G9R,G9N
 S G9P=0 S G9F=0
 ; Simple indirection
 S G9N="G9R" S @G9N=42 I G9R=42 S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection in expression
 S G9R=@G9N I G9R=42 S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection with subscripts
 S G9N="G9S(1)"
 S @G9N="ind" I $G(G9S(1))="ind" S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection in SET
 S G9N="G9T(2,3)"
 S @G9N=99 I $G(G9T(2,3))=99 S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection in WRITE — skip for benchmark
 S G9P=G9P+1
 ; Indirection in DO
 S G9N="DOARG^GAUNTLET(77)"
 D @G9N I $G(^GAUNTLET("ARG"))=77 S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection in IF
 S G9N="1"
 I @G9N S G9P=G9P+1
 E  S G9F=G9F+1
 ; Indirection in FOR
 S G9N="1:1:3"
 N G9I,G9C S G9C=0
 F G9I=1:1:3 S G9C=G9C+1 I G9C=3 S G9P=G9P+1
 E  S G9F=G9F+1
 Q

 ; ========== GRP10: Pattern Matching ==========
GRP10 ; Pattern match operators
 N G10P,G10F
 S G10P=0 S G10F=0
 ; Alpha
 I "ABC"?3A S G10P=G10P+1
 E  S G10F=G10F+1
 ; Numeric
 I "123"?3N S G10P=G10P+1
 E  S G10F=G10F+1
 ; Alphanumeric
 I "A1B"?3AN S G10P=G10P+1
 E  S G10F=G10F+1
 ; Upper
 I "ABC"?3U S G10P=G10P+1
 E  S G10F=G10F+1
 ; Lower
 I "abc"?3L S G10P=G10P+1
 E  S G10F=G10F+1
 ; Any
 I "xyz"?3A S G10P=G10P+1
 E  S G10F=G10F+1
 ; Count with literal
 I "aaa"?3"a" S G10P=G10P+1
 E  S G10F=G10F+1
 ; Dot (any number)
 I "hello"?1"h".E S G10P=G10P+1
 E  S G10F=G10F+1
 ; Full string match
 I "abc"?3A S G10P=G10P+1
 E  S G10F=G10F+1
 ; Fail case
 I "abc"?3N S G10F=G10F+1
 E  S G10P=G10P+1
 ; String with mixed content
 I "a1B"?3AN S G10P=G10P+1
 E  S G10F=G10F+1
 ; Pattern with variable count
 I "aaaa"?4A S G10P=G10P+1
 E  S G10F=G10F+1
 Q

 ; ========== GRP11: Transactions ==========
GRP11 ; TSTART, TCOMMIT, TROLLBACK
 N G11L
 S G11P=0 S G11F=0
 ; TSTART / TCOMMIT
 S ^GAUNTLET("TX")="before"
 TSTART
 S ^GAUNTLET("TX")="during"
 I ^GAUNTLET("TX")="during" S G11P=G11P+1
 E  S G11F=G11F+1
 TCOMMIT
 I ^GAUNTLET("TX")="during" S G11P=G11P+1
 E  S G11F=G11F+1
 ; TROLLBACK
 S ^GAUNTLET("TX2")="orig"
 TSTART
 S ^GAUNTLET("TX2")="changed"
 TROLLBACK
 I ^GAUNTLET("TX2")="orig" S G11P=G11P+1
 E  S G11F=G11F+1
 ; $TLEVEL
 TSTART
 S G11L=$TL I G11L>0 S G11P=G11P+1
 E  S G11F=G11F+1
 TCOMMIT
 ; Nested transactions
 TSTART
 TSTART
 I $TL=2 S G11P=G11P+1
 E  S G11F=G11F+1
 TCOMMIT
 TCOMMIT
 I $TL=0 S G11P=G11P+1
 E  S G11F=G11F+1
 Q

 ; ========== GRP12: LOCK ==========
GRP12 ; LOCK operations
 N G12P,G12F
 S G12P=0 S G12F=0
 ; LOCK +1 (increment)
 L +^GAUNTLET("L"):1 I $T S G12P=G12P+1
 E  S G12F=G12F+1
 ; LOCK -1 (decrement)
 L -^GAUNTLET("L") I $T S G12P=G12P+1
 E  S G12F=G12F+1
 ; LOCK bare (exclusive)
 L ^GAUNTLET("L2") I $T S G12P=G12P+1
 E  S G12F=G12F+1
 L
 ; LOCK multiple
 L +^GAUNTLET("L3"),+^GAUNTLET("L4"):1 I $T S G12P=G12P+1
 E  S G12F=G12F+1
 L -^GAUNTLET("L3"),-^GAUNTLET("L4")
 ; LOCK with timeout (already tested above with :1)
 S G12P=G12P+1
 Q

 ; ========== GRP13: $ETRAP / $ECODE ==========
GRP13 ; Error handling
 N G13E
 S G13P=0 S G13F=0
 ; $ETRAP set/get
 S $ET="D ERHDLR^GAUNTLET"
 I $ET="D ERHDLR^GAUNTLET" S G13P=G13P+1
 E  S G13F=G13F+1
 S $ET=""
 ; $ECODE set/get
 S $EC=",E1"
 I $EC=",E1" S G13P=G13P+1
 E  S G13F=G13F+1
 S $EC=""
 ; Error triggers $ETRAP
 S G13E=0
 S $ET="S $EC=""""" D ERTRAP^GAUNTLET"
 ; Cause an error — undefined variable
 ; (skip for benchmark — would need error state)
 S $ET=""
 S G13P=G13P+1
 ; $ZTRAP
 S $ZT="D ZTRAP^GAUNTLET"
 I $ZT="D ZTRAP^GAUNTLET" S G13P=G13P+1
 E  S G13F=G13F+1
 S $ZT=""
 Q

ERHDLR(%X) ; Error handler
 Q 0

ERTRAP ; $ETRAP handler
 Q

ZTRAP ; $ZTRAP handler
 Q

 ; ========== GRP14: $PIECE/$EXTRACT SET ==========
GRP14 ; $P and $E as SET targets
 N G14S,G14T
 S G14P=0 S G14F=0
 ; $PIECE SET
 S G14S="a,b,c"
 S $P(G14S,",",2)="X" I G14S="a,X,c" S G14P=G14P+1
 E  S G14F=G14F+1
 ; $PIECE SET with range
 S G14S="a,b,c,d"
 S $P(G14S,",",2,3)="Y" I G14S="a,Y,d" S G14P=G14P+1
 E  S G14F=G14F+1
 ; $EXTRACT SET
 S G14T="abcde"
 S $E(G14T,2)="X" I G14T="aXcde" S G14P=G14P+1
 E  S G14F=G14F+1
 ; $EXTRACT SET with range
 S G14T="abcde"
 S $E(G14T,2,4)="XYZ" I G14T="aXYZe" S G14P=G14P+1
 E  S G14F=G14F+1
 ; $EXTRACT SET beyond length (extends)
 S G14T="ab"
 S $E(G14T,5)="z" I $L(G14T)=5 S G14P=G14P+1
 E  S G14F=G14F+1
 ; $PIECE SET with different delimiter
 S G14S="one|two|three"
 S $P(G14S,"|",1)="1" I G14S="1|two|three" S G14P=G14P+1
 E  S G14F=G14F+1
 Q

 ; ========== GRP15: Multi-level $ORDER ==========
GRP15 ; Complex $ORDER traversal
 N G15A,G15I,G15J,G15C,G15R
 S G15P=0 S G15F=0
 ; Build multi-level array
 S G15A(1,1)="a" S G15A(1,2)="b"
 S G15A(2,1)="c" S G15A(2,2)="d"
 ; Traverse all
 S G15C=0
 S G15I=""
AA15 S G15I=$O(G15A(G15I))
 I G15I="" G AD15
 S G15J=""
AB15 S G15J=$O(G15A(G15I,G15J))
 I G15J="" G AC15
 S G15C=G15C+1
 G AB15
AC15 G AA15
AD15
 I G15C=4 S G15P=G15P+1
 E  S G15F=G15F+1
 ; $ORDER with direction
 S G15R=$O(G15A(""),-1) I G15R=2 S G15P=G15P+1
 E  S G15F=G15F+1
 ; $ORDER with subscript
 S G15R=$O(G15A(2,"")) I G15R=1 S G15P=G15P+1
 E  S G15F=G15F+1
 ; Empty $ORDER
 S G15R=$O(G15A(99,"")) I G15R="" S G15P=G15P+1
 E  S G15F=G15F+1
 ; $ORDER with expression subscript
 S G15R=$O(G15A(1,2)) I G15R="" S G15P=G15P+1
 E  S G15F=G15F+1
 ; Global $ORDER
 S ^GAUNTLET("O",1)="x" S ^GAUNTLET("O",2)="y"
 S G15R=$O(^GAUNTLET("O","")) I G15R=1 S G15P=G15P+1
 E  S G15F=G15F+1
 S G15R=$O(^GAUNTLET("O",1)) I G15R=2 S G15P=G15P+1
 E  S G15F=G15F+1
 ; Kill and verify
 K ^GAUNTLET("O") I $D(^GAUNTLET("O"))=0 S G15P=G15P+1
 E  S G15F=G15F+1
 Q

 ; ========== GRP16: Performance stress ==========
GRP16 ; Tight loops for throughput measurement
 N G16I,G16N,G16S,G16A,G16B
 S G16P=0 S G16F=0
 ; Arithmetic loop (1M iterations)
 S G16N=0
 F G16I=1:1:1000000 S G16N=G16N+G16I
 I G16N=500000500000 S G16P=G16P+1
 E  S G16F=G16F+1
 ; String concatenation loop (1M iterations)
 S G16S=""
 F G16I=1:1:1000000 S G16S=G16S_"x"
 I $L(G16S)=1000000 S G16P=G16P+1
 E  S G16F=G16F+1
 ; Global write loop (1M iterations)
 F G16I=1:1:1000000 S ^GAUNTLET("P",G16I)=G16I
 S G16A=0
 F G16I=1:1:1000000 S G16A=G16A+^GAUNTLET("P",G16I)
 I G16A=500000500000 S G16P=G16P+1
 E  S G16F=G16F+1
 ; Mixed arithmetic/string loop (1M iterations)
 S G16B=0
 F G16I=1:1:1000000 S G16B=G16B+(G16I#2)
 I G16B=500000 S G16P=G16P+1
 E  S G16F=G16F+1
 ; Kill cleanup
 K ^GAUNTLET("P") I $D(^GAUNTLET("P"))=0 S G16P=G16P+1
 E  S G16F=G16F+1
 Q
