PHPIECE ; PIECE 200k $PIECE
 n i,p,str s str="" f i=1:1:100 s str=str_$J(i,10)
 f i=1:1:200000 s p=$P(str," ",i#100+1)
 w p,!
 q
