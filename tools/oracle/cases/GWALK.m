GWALK ; ORACLE case: global $ORDER walk loop
 K ^T
 F I=1:1:5 S ^T(I)=I*I
 S J=""
 F  S J=$O(^T(J)) Q:J=""  W ^T(J)," "
 Q
