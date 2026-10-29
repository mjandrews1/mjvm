LOCKGLB ; ORACLE: LOCK a global then unlock
 L +^GLBTEST
 W "locked"
 L -^GLBTEST
 W "|unlocked"
 Q
