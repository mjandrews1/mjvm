STDEPTH ; ORACLE: $STACK depth inside DO tags
 S C=$ST_"|"
 W C
 D SB2
 S C=$ST_"|"
 W C
 Q
 ;
SB2 ;
 S C=$ST_"|"_$ST(1,"MCODE")
 W C
 Q
