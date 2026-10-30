COLLATE ; ORACLE: $O collation — canonical-numeric subscripts sort numerically, before strings
 K ^ZZCOL
 S ^ZZCOL("9")=""
 S ^ZZCOL("0011012")=""
 S ^ZZCOL("1264327")=""
 S ^ZZCOL("7502500")=""
 S k=""
C1 ;
 S k=$O(^ZZCOL(k))
 I k="" G CD
 W k
 W !
 G C1
CD ;
 K ^ZZCOL
 Q
