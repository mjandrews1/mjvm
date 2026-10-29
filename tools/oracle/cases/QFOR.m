QFOR ; ORACLE case: argumentless FOR with postconditional QUIT exit
 S J=""
 F  S J=J_"x" Q:$L(J)=3  W J," "
 Q
