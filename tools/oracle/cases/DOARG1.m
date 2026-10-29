DOARG1 ; ORACLE: DO with value parameters
 S A=1
 D DP(42)
 W A,"|"
 Q
DP(X)
 S A=X*2
 Q
 Q
