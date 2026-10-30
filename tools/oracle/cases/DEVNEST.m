DEVNEST ; ORACLE: nested DO/extrinsic frames must not clobber the caller's return
 D OUTER
 W "after"
 W !
 Q
 ;
OUTER ;
 S r=$$MID("x")
 Q
 ;
MID(t) ;
 D LEAF
 Q $L(t)
 ;
LEAF ;
 S lv=1
 Q
