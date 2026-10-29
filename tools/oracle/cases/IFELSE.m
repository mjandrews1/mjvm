IFELSE ; ORACLE case: IF ELSE (known upstream divergence T12)
 S A=0
 I A=1 W "yes"
 E  W "no"
 Q
