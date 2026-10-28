PHGSET ; GSET 50k global sets
 n i k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 w $G(^BENCHTEST(50000)),!
 q
