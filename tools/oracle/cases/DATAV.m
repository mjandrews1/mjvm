DATAV ; ORACLE: $DATA variants on globals
 K ^D
 W $D(^D)
 S ^D="v"
 W ",",$D(^D)
 S ^D(1,2)="w"
 W ",",$D(^D(1))
 K ^D
 Q
