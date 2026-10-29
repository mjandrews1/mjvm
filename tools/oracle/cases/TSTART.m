TSTART ; ORACLE: transaction rollback restores globals
 K ^TT
 S ^TT="before"
 TSTART
 S ^TT="during"
 TROLLBACK
 W ^TT
 K ^TT
 Q
