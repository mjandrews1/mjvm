DOQUIT ; ORACLE case: DO tag from FOR loop, QUIT in tag
 S C=0
 F J=1:1:3 D BUMP
 W "count=",C
 Q
BUMP
 S C=C+J
 Q
