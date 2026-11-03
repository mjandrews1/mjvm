ARGFORQ ; ORACLE case mjvm#13: QUIT inside argumentless FOR must exit the loop, not the routine
 k a f i=1:1:3 s a(i)=i
 s j="",c=0 f  s j=$O(a(j)) q:j=""  s c=c+1
 w "after=",c,!
 q
