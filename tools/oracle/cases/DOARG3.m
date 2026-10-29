DOARG3 ; ORACLE: DO params are call-by-value (caller unchanged)
 S A=10
 D CV(A)
 W A
 Q
CV(V)
 S V=99
 Q
