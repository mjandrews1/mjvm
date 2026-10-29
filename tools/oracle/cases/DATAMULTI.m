DATAMULTI ; ORACLE: $DATA with deeper subscripts
 K ^DD
 W $D(^DD(1,2,3))
 S ^DD(1,2,3)="v"
 W "|",$D(^DD(1,2,3)),"|",$D(^DD(1)),"|",$D(^DD(1,2))
 K ^DD
 Q
