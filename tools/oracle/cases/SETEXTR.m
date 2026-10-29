SETEXTR ; ORACLE: SET $EXTRACT (substring replace)
 S S="abcdef"
 S $E(S,2,4)="XYZ"
 W S
 Q
