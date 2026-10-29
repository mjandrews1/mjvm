DOEXPARG ; ORACLE: DO with expression arguments
 D EA(1+2,3*4)
 Q
 ;
EA(P,Q) ;
 S C=P_"|"_Q
 W C
 Q
