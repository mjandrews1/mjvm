SUBLOC ; ORACLE case: subscripted locals, changing subscript
 S I=1
 S A(I)="one"
 S I=2
 S A(I)="two"
 W A(1),A(2)," ",A(I)
 Q
