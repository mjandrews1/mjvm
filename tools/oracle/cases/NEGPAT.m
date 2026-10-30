NEGPAT ; ORACLE: negated pattern match ('?1.N)
 S a="ab"
 I a'?1.N W "a-nonnum"
 I a?1.N W "a-num"
 W !
 S b="12"
 I b'?1.N W "b-nonnum"
 I b?1.N W "b-num"
 W !
 Q
