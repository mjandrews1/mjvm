PHSTRCAT ; STRCAT 30k appends
 n i,s s s="x" f i=1:1:30000 s s=s_$J(i,7)
 w $L(s),!
 q
