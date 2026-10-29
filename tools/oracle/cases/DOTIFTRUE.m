DOTIFTRUE ; ORACLE case: false IF mid dotted block (skipped tail, #18 f1)
 S Z(1)=100
 F J=1:1:1 D
 . S V=J*50
 . I V<0 S V=V+1
 . S Z(J)=V
 W $G(Z(1))
 Q
