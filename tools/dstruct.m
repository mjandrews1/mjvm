DSTRUCT ; tools/dstruct.m — the 8 fundamental data structures in M
 ; P1d workstream D. Like pemdasm.m: self-checking driver, one verdict
 ; line per structure, output must match GT.M byte-for-byte (differential
 ; oracle test). Storage split on purpose: array/stack/queue/heap use
 ; LOCAL sparse arrays (subscripted-local cache path), linked-list/hash/
 ; tree/graph use GLOBALS (store + $ORDER/$DATA path). Recursion (BST
 ; in-order) uses DO with per-frame NEW. GOTO-loop style avoids the
 ; known mJVM bug classes (QUIT-in-FOR, comma-IF, dotted-block IF).
 ;
 ; DS1 array (sparse local, $ORDER traversal)
 ; DS2 linked list (global nodes + NEXT chain, delete)
 ; DS3 stack (local, LIFO push/pop)
 ; DS4 queue (local, FIFO head/tail)
 ; DS5 hash table (global, $ASCII sum mod 11, key collisions handled)
 ; DS6 binary search tree (global, iterative insert, recursive in-order)
 ; DS7 binary min-heap (local, sift-up/sift-down, extract-min sorted)
 ; DS8 graph (global adjacency, BFS from node 1, cycle + unreachable node)
 ;
 W !
 D DS1
 D DS2
 D DS3
 D DS4
 D DS5
 D DS6
 D DS7
 D DS8
 Q
 ;
DS1 ; array: A(i)=i*i for 1..5, sparse A(10)=100; $O traversal sum=155
 K A
 F I=1:1:5 S A(I)=I*I
 S A(10)=100
 S K="" S S=0
DS1L ;
 S K=$O(A(K))
 I K="" G DS1C
 S S=S+A(K)
 G DS1L
DS1C ;
 I S=155 W "DS1 ok array"
 I S'=155 W "DS1 FAIL array"
 W !
 K A
 Q
 ;
DS2 ; linked list: ^DS2(id)=val, ^DS2(id,"X")=next; delete node 3
 K ^DS2
 F I=1:1:5 S ^DS2(I)=I*10
 F I=1:1:4 S ^DS2(I,"X")=I+1
 S ^DS2(5,"X")=0
 S ^DS2("H")=1
 S P=^DS2("H") S S=0 S C=""
DS2L ;
 I P=0 G DS2D
 S S=S+^DS2(P)
 S C=C_^DS2(P)_","
 S P=^DS2(P,"X")
 G DS2L
DS2D ;
 ; delete node 3: node 2 now points to node 4
 S ^DS2(2,"X")=^DS2(3,"X")
 K ^DS2(3)
 S P=^DS2("H") S S2=0 S C2=""
DS2L2 ;
 I P=0 G DS2C
 S S2=S2+^DS2(P)
 S C2=C2_^DS2(P)_","
 S P=^DS2(P,"X")
 G DS2L2
DS2C ;
 I S=150 I S2=120 W "DS2 ok linked-list"
 I S'=150 W "DS2 FAIL linked-list"
 I C2'="10,20,40,50," W "DS2 FAIL delete"
 W !
 K ^DS2
 Q
 ;
DS3 ; stack: push 1..5, pop all -> "5,4,3,2,1,"
 K S
 S S("T")=0
 F I=1:1:5 S V=I D DS3PUSH
 S C=""
DS3L ;
 I S("T")=0 G DS3C
 D DS3POP
 S C=C_V_","
 G DS3L
DS3C ;
 I C="5,4,3,2,1," W "DS3 ok stack-lifo"
 I C'="5,4,3,2,1," W "DS3 FAIL stack-lifo"
 W !
 K S
 Q
 ;
DS3PUSH ;
 S S("T")=S("T")+1
 S S(S("T"))=V
 Q
 ;
DS3POP ;
 S V=S(S("T"))
 K S(S("T"))
 S S("T")=S("T")-1
 Q
 ;
DS4 ; queue: enqueue 1..5, dequeue all -> "1,2,3,4,5,"
 K Q
 S Q("H")=1 S Q("T")=0
 F I=1:1:5 S V=I D DS4ENQ
 S C=""
DS4L ;
 I Q("H")>Q("T") G DS4C
 D DS4DEQ
 S C=C_V_","
 G DS4L
DS4C ;
 I C="1,2,3,4,5," W "DS4 ok queue-fifo"
 I C'="1,2,3,4,5," W "DS4 FAIL queue-fifo"
 W !
 K Q
 Q
 ;
DS4ENQ ;
 S Q("T")=Q("T")+1
 S Q(Q("T"))=V
 Q
 ;
DS4DEQ ;
 S V=Q(Q("H"))
 S Q("H")=Q("H")+1
 Q
 ;
DS5 ; hash table: ^DS5(bucket,key)=val; lookups + miss
 K ^DS5
 S MOD=11
 S K="ALPHA" S V=1 D DS5PUT
 S K="BETA" S V=2 D DS5PUT
 S K="GAMMA" S V=3 D DS5PUT
 S K="DELTA" S V=4 D DS5PUT
 S K="EPS" S V=5 D DS5PUT
 S C=""
 S K="ALPHA" D DS5GET S C=C_R_","
 S K="BETA" D DS5GET S C=C_R_","
 S K="GAMMA" D DS5GET S C=C_R_","
 S K="DELTA" D DS5GET S C=C_R_","
 S K="EPS" D DS5GET S C=C_R_","
 S K="ZETA" D DS5GET
 I C="1,2,3,4,5," I R="" W "DS5 ok hash-lookup+miss"
 I C'="1,2,3,4,5," W "DS5 FAIL hash-lookup"
 I R'="" W "DS5 FAIL hash-miss"
 W !
 K ^DS5
 Q
 ;
DS5PUT ;
 D DS5HASH
 S ^DS5(H,K)=V
 Q
 ;
DS5GET ;
 D DS5HASH
 S R=$G(^DS5(H,K))
 Q
 ;
DS5HASH ; H = (sum of $ASCII of K chars) # MOD
 S H=0
 S J=1
DS5HL ;
 I J>$L(K) G DS5HD
 S H=H+$A($E(K,J))
 S J=J+1
 G DS5HL
DS5HD ;
 S H=H#MOD
 Q
 ;
DS6 ; BST: insert 5,3,8,1,4,7,9; recursive in-order; searches
 ; Child pointers default to 0 via $G(x,0): undefined children are a
 ; numeric 0, never "" (null subscripts error in GT.M and loop in mJVM).
 K ^DS6
 S ^DS6("C")=0
 S ^DS6("R")=0
 F I=1:1:7 S V=$E("5381479",I) D DS6INS
 S C=""
 S N=^DS6("R")
 D DS6ORD
 S V=7 S FND=0 S N=^DS6("R") D DS6SRCH
 S F7=FND
 S V=6 S FND=0 S N=^DS6("R") D DS6SRCH
 S F6=FND
 I C="1,3,4,5,7,8,9," I F7=1 I F6=0 W "DS6 ok bst"
 I C'="1,3,4,5,7,8,9," W "DS6 FAIL bst-inorder"
 I F7'=1 W "DS6 FAIL bst-search7"
 I F6'=0 W "DS6 FAIL bst-search6"
 W !
 K ^DS6
 Q
 ;
DS6INS ; insert V into BST (^DS6(id)=val, id,"L"/"R"=child, "R"=root, "C"=count)
 I ^DS6("R")=0 G DS6NEW
 S N=^DS6("R")
DS6IL ;
 S VAL=^DS6(N)
 I V<VAL G DS6LEFT
 I $G(^DS6(N,"R"),0)=0 G DS6MKR
 S N=$G(^DS6(N,"R"),0)
 G DS6IL
DS6LEFT ;
 I $G(^DS6(N,"L"),0)=0 G DS6MKL
 S N=$G(^DS6(N,"L"),0)
 G DS6IL
DS6MKL ;
 S C=^DS6("C")+1
 S ^DS6("C")=C
 S ^DS6(C)=V
 S ^DS6(N,"L")=C
 Q
DS6MKR ;
 S C=^DS6("C")+1
 S ^DS6("C")=C
 S ^DS6(C)=V
 S ^DS6(N,"R")=C
 Q
DS6NEW ;
 S C=^DS6("C")+1
 S ^DS6("C")=C
 S ^DS6(C)=V
 S ^DS6("R")=C
 Q
 ;
DS6ORD ; in-order walk of node N, append to C (recursive, per-frame NEW)
 NEW VAL,L,R
 I N=0 G DS6OX
 S VAL=^DS6(N)
 S L=$G(^DS6(N,"L"),0)
 S R=$G(^DS6(N,"R"),0)
 I L'=0 G DS6OL
 G DS6OV
DS6OL ;
 S N=L
 D DS6ORD
DS6OV ;
 S C=C_VAL_","
 I R'=0 G DS6OR
 G DS6OX
DS6OR ;
 S N=R
 D DS6ORD
DS6OX ;
 Q
 ;
DS6SRCH ; find V from node N; sets FND 1/0
 S FND=0
DS6SL ;
 I N=0 G DS6SD
 S VAL=^DS6(N)
 I V=VAL G DS6SF
 I V<VAL G DS6SLT
 S N=$G(^DS6(N,"R"),0)
 G DS6SL
DS6SLT ;
 S N=$G(^DS6(N,"L"),0)
 G DS6SL
DS6SF ;
 S FND=1
DS6SD ;
 Q
 ;
DS7 ; min-heap: insert 5,3,8,1,4; extract-min x5 -> "1,3,4,5,8,"
 ; Heap internals use IX/CX/HN (never I or C): the insert loop's FOR
 ; controls I, and the driver builds its verdict string in C — M shares
 ; both across DO, so clobbering them inside the body breaks the loops.
 K H
 S H("N")=0
 F I=1:1:5 S V=$E("53814",I) D DS7INS
 S C=""
DS7L ;
 I H("N")=0 G DS7C
 D DS7EXT
 S C=C_V_","
 G DS7L
DS7C ;
 I C="1,3,4,5,8," W "DS7 ok min-heap"
 I C'="1,3,4,5,8," W "DS7 FAIL min-heap"
 W !
 K H
 Q
 ;
DS7INS ; insert V, sift up
 S H("N")=H("N")+1
 S IX=H("N")
 S H(IX)=V
DS7UL ;
 I IX<2 G DS7UD
 S P=IX\2
 I H(IX)<H(P) G DS7UW
 G DS7UD
DS7UW ;
 S T=H(IX) S H(IX)=H(P) S H(P)=T
 S IX=P
 G DS7UL
DS7UD ;
 Q
 ;
DS7EXT ; extract min into V, move last to root, sift down
 S V=H(1)
 S HN=H("N")
 I HN=1 G DS7E1
 S H(1)=H(HN)
 K H(HN)
 S H("N")=HN-1
 S IX=1
DS7DL ;
 S HN=H("N")
 I 2*IX>HN G DS7DD
 S CX=2*IX
 S R2=2*IX+1
 I R2>HN G DS7D2
 I H(R2)<H(CX) S CX=R2
DS7D2 ;
 I H(CX)<H(IX) G DS7DW
 G DS7DD
DS7DW ;
 S T=H(IX) S H(IX)=H(CX) S H(CX)=T
 S IX=CX
 G DS7DL
DS7DD ;
 Q
DS7E1 ;
 K H(1)
 S H("N")=0
 Q
 ;
DS8 ; graph: edges 1-2,1-3,2-4,3-4,4-5,5-2 (cycle); node 9 isolated
 K ^DS8
 S ^DS8("E",1,2)=""
 S ^DS8("E",1,3)=""
 S ^DS8("E",2,4)=""
 S ^DS8("E",3,4)=""
 S ^DS8("E",4,5)=""
 S ^DS8("E",5,2)=""
 ; BFS from 1
 S ^DS8("V",1)=1
 S ^DS8("Q",1)=1
 S QH=1 S QT=1
 S C=""
DS8L ;
 I QH>QT G DS8D
 S N=^DS8("Q",QH)
 S QH=QH+1
 S C=C_N_","
 S M=""
DS8L2 ;
 S M=$O(^DS8("E",N,M))
 I M="" G DS8L
 G DS8L3
DS8L3 ;
 I $D(^DS8("V",M)) G DS8L2
 S ^DS8("V",M)=1
 S QT=QT+1
 S ^DS8("Q",QT)=M
 G DS8L2
DS8D ;
 ; visited count: 1,2,3,4,5 (node 9 unreachable)
 S K="" S CNT=0
DS8CL ;
 S K=$O(^DS8("V",K))
 I K="" G DS8CC
 S CNT=CNT+1
 G DS8CL
DS8CC ;
 ; edge count via double $O
 S EA="" S ECNT=0
DS8EL ;
 S EA=$O(^DS8("E",EA))
 I EA="" G DS8ED
 S EB=""
DS8EL2 ;
 S EB=$O(^DS8("E",EA,EB))
 I EB="" G DS8EL
 S ECNT=ECNT+1
 G DS8EL2
DS8ED ;
 I C="1,2,3,4,5," I CNT=5 I ECNT=6 W "DS8 ok graph-bfs"
 I C'="1,2,3,4,5," W "DS8 FAIL graph-bfs"
 I CNT'=5 W "DS8 FAIL graph-reach"
 I ECNT'=6 W "DS8 FAIL graph-edges"
 W !
 K ^DS8
 Q
