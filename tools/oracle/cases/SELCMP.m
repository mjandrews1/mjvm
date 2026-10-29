SELCMP ; ORACLE: $SELECT with computed conditions
 S X=5
 W $S(X*2>8:"big",1:"small")
 Q
