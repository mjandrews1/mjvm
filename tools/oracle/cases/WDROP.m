WDROP ; ORACLE case: multi-arg WRITE keeps its final non-literal argument (defect #24)
 S C=1,D=2,X=3
 W "a=",C,D
 W "b=",C,D,X
 Q
