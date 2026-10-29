NEWMULTI ; ORACLE: NEW multiple variables in a tag
 S A=1,B=2
 D NM
 W A,"|",B,"|",C
 Q
NM
 N A,B
 S A=99,B=99,C="new"
 Q
