EXTRACT ; ORACLE: $EXTRACT forms
 S S="Hello, World"
 W $E(S,1,5),"|",$E(S,8,12),"|",$E(S,0,0)
 Q
