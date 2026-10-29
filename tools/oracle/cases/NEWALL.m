NEWALL ; ORACLE: argumentless NEW news everything in scope
 S A=1,B=2
 D NA2
 W A,"|",B
 Q
NA2
 N
 S A=99
 W $G(A),"|",$G(B,"unset")
 Q
