SUBGLOB ; ORACLE case: subscripted globals in loops (defect #15)
 K ^P
 F I=1:1:3 S ^P(I)=I*11
 F I=1:1:3 W ^P(I)," "
 Q
