BENCH ; GT.M vs YottaDB micro-benchmark (portable; throwaway regions only)
 n t0,t1,i,j,r,s,a,v,p,str,c
 w !,"engine: ",$ZVERSION,!
 s t0=$ZUT,r=0 f i=1:1:500000 s r=r+i
 s t1=$ZUT w "INTLOOP   500k adds        : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT,s="x" f i=1:1:30000 s s=s_$J(i,7)
 s t1=$ZUT w "STRCAT    30k appends     : ",$J((t1-t0)/1000000,0,3),"s  (len ",$L(s),")",!
 s t0=$ZUT k a f i=1:1:100000 s a(i)=i
 s t1=$ZUT w "ASET      100k local sets : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT s j="",c=0 f  s j=$o(a(j)) q:j=""  s c=c+1
 s t1=$ZUT w "AWALK     100k $ORDER     : ",$J((t1-t0)/1000000,0,3),"s  (count ",c,")",!
 s t0=$ZUT s str="" f i=1:1:100 s str=str_$J(i,10)
 s t1=$ZUT w "STRBUILD  100 pieces      : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT f i=1:1:200000 s p=$p(str," ",i#100+1)
 s t1=$ZUT w "PIECE     200k $PIECE     : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT k ^BENCHTEST f i=1:1:50000 s ^BENCHTEST(i)=i
 s t1=$ZUT w "GSET      50k global sets : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT f i=1:1:500000 s v=$g(^BENCHTEST(i#50000+1))
 s t1=$ZUT w "GREAD     500k global get : ",$J((t1-t0)/1000000,0,3),"s",!
 s t0=$ZUT s j="",c=0 f  s j=$o(^BENCHTEST(j)) q:j=""  s c=c+1
 s t1=$ZUT w "GWALK     50k global $ORDER: ",$J((t1-t0)/1000000,0,3),"s  (count ",c,")",!
 k ^BENCHTEST
 q
