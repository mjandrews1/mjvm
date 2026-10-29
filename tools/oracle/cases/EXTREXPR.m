EXTREXPR ; ORACLE: $EXTRACT with computed positions
 S S="abcdefgh"
 S P=2
 W $E(S,P,P+3),"|",$E(S,$L(S))
 Q
