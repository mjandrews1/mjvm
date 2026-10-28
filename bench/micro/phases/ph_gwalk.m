PHGWALK ; GWALK 50k global $ORDER
 n i,j,c k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 s j="",c=0 f  s j=$O(^BENCHTEST(j)) q:j=""  s c=c+1
 k ^BENCHTEST
 w c,!
 q
