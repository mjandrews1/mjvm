KILLIND ; ORACLE: KILL via indirection
 S B="KVAR"
 S KVAR=1
 K @B
 W $G(KVAR,"killed")
 Q
