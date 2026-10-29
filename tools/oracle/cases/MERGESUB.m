MERGESUB ; ORACLE: MERGE a subtree
 K ^S,^T
 S ^S(1,1)="a",^S(1,2)="b",^S(2)="c"
 MERGE ^T(9)=^S(1)
 W ^T(9,1),"|",^T(9,2),"|",$D(^T(9))
 K ^S,^T
 Q
