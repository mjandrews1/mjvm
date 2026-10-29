MULTILINE ; ORACLE case: multi-command same-line FOR body (defect #19)
 S R=0
 F J=1:1:3 S I=4-J S R=R+I
 W R
 Q
