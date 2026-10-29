DOTIFMID ; ORACLE case: IF mid dotted block under FOR (defect #18 f1)
 S Z=0
 F J=1:1:2 D
 . S V=J*10
 . I V<5 S Z=Z+V
 . S Z=Z+1
 W "end ",Z
 Q
