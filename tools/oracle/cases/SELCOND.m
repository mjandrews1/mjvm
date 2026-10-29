SELCOND ; ORACLE: $SELECT
 S X=2
 W $S(X=1:"one",X=2:"two",1:"other")
 Q
