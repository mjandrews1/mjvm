MERGEREV ; ORACLE: MERGE global from local
 K A,^MM
 S A(1)="x",A(2,3)="y"
 MERGE ^MM=A
 W ^MM(1),"|",^MM(2,3)
 K A,^MM
 Q
