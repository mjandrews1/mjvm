TCOMMIT ; ORACLE: transaction commit keeps changes
 K ^TT
 S ^TT="before"
 TSTART
 S ^TT="during"
 TCOMMIT
 W ^TT
 K ^TT
 Q
