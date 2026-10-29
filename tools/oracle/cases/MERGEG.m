MERGEG ; ORACLE case: MERGE globals
 K ^A,^B
 S ^A(1)="x",^A(2,3)="y"
 MERGE ^B=^A
 W ^B(1),^B(2,3)," d=",$D(^B(2,3))
 Q
