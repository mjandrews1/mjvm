ORDERDIR ; ORACLE: $ORDER numeric direction on globals
 K ^OO
 S ^OO(1)=1,^OO(2)=2,^OO(3)=3
 S K="" F  S K=$O(^OO(K)) Q:K=""  W K
 W "|"
 S K="" F  S K=$O(^OO(K),-1) Q:K=""  W K
 K ^OO
 Q
