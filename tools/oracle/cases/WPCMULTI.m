WPCMULTI ; ORACLE case mjvm#4: postconditional WRITE with multiple args never fires inside the $O-walk (silent no-op)
 n i,a,j,c k a f i=1:1:1000 s a(i)=i s j="",c=0 f  s j=$O(a(j)) q:j=""  s c=c+1 w:c=1000 c,!
 q
