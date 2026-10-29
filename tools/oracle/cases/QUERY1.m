QUERY1 ; ORACLE: $QUERY walks subscripts in order
 K ^QQ
 S ^QQ(1,2)="a",^QQ(1,5)="b",^QQ(2)="c"
 S R=$Q(^QQ(1,2))
 W $Q(^QQ(1,2)),"|",$Q(^QQ(1,5))
 K ^QQ
 Q
