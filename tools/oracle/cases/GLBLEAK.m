GLBLEAK ; ORACLE: S ^G=$G(^G)+expr in a loop must not overflow the value stack (#38)
 S i=0
 S ^ZZGLB=0
GL1 ;
 S i=(i)+1
 I i>300 G GLD
 S ^ZZGLB=$G(^ZZGLB)+1
 G GL1
GLD ;
 W $G(^ZZGLB)
 W !
 K ^ZZGLB
 Q
