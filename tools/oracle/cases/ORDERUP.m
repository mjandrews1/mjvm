ORDERUP ; ORACLE: $ORDER both directions on locals
 S A("b")=1,A("a")=2,A("c")=3
 S K=$O(A("")) W K
 S K=$O(A("c"),-1) W "|",K
 S K=$O(A("zzz")) W "|",K
 Q
