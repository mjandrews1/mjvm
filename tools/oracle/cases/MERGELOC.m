MERGELOC ; ORACLE: MERGE local to local
 S A(1)="x",A(2,3)="y"
 MERGE B=A
 W B(1),"|",B(2,3)
 K A,B
 Q
