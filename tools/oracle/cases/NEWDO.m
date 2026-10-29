NEWDO ; ORACLE: NEW inside DO tag restores on QUIT
 S X="outer"
 D ND2
 W X,"|",Y
 Q
ND2
 N X
 S X="inner",Y="kept"
 Q
