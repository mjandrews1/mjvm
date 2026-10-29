EXTRNEST ; ORACLE: nested extrinsic call ($$ calling $$)
 W $$OUTER(3)
 Q
OUTER(N)
 Q $$INNER(N)*2
INNER(M)
 Q M+1
 Q
