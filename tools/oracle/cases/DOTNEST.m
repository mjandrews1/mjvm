DOTNEST ; ORACLE: two-level dotted block under FOR
 S C=0
 F I=1:1:2 D
 . F J=1:1:2 D
 . . S C=C+I*J
 W C
 Q
