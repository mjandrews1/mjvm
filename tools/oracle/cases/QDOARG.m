QDOARG ; ORACLE case: argumentless FOR with DO inside, QUIT in DO
 S C=0
 F J=1:1:6 D STEP
 W "c=",C
 Q
STEP
 I J#2=0 Q
 S C=C+1
 Q
