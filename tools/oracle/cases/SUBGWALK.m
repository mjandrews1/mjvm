SUBGWALK ; ORACLE case: subscripted globals store+walk loop (defect #15 regression)
 K ^P
 F I=1:1:3 S ^P(I)=I*11
 S J=""
 F  S J=$O(^P(J)) Q:J=""  W "v",J,"=",^P(J)," "
 Q
