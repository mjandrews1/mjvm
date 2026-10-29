ORDERDIR1 ; ORACLE: $ORDER reverse from value
 K ^OO
 S ^OO(1)=1,^OO(2)=2,^OO(3)=3,^OO(5)=5
 W $O(^OO(""),-1),"|",$O(^OO(4),-1),"|",$O(^OO(5),-1)
 K ^OO
 Q
