SELDEF ; ORACLE: $SELECT with default arm
 S X=99
 W $S(X=1:"one",X=2:"two",1:"fallback")
 Q
