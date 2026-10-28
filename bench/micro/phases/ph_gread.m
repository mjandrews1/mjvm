PHGREAD ; GREAD 500k global gets (+50k setup)
 n i,v k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 f i=1:1:500000 s v=$G(^BENCHTEST(i#50000+1))
 w v,!
 q
