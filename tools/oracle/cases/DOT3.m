DOT3 ; ORACLE: three-level dotted nesting
 S R=0
 F I=1:1:2 D
 . F J=1:1:2 D
 . . F K=1:1:2 D
 . . . S R=R+I+J+K
 W R
 Q
