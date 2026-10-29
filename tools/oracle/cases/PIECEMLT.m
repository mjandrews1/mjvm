PIECEMLT ; ORACLE: multi-char delimiter $PIECE
 S S="a::b::c"
 W $P(S,"::",2),"|",$P(S,"::",1,2)
 Q
