CFE ; tools/cfe.m — continued fraction of e in M
 ; P1d workstream D (algebra suite). e = [2; 1,2,1, 1,4,1, 1,6,1, ...]
 ; — a0=2 then repeating (1, 2m, 1). Generates 19 terms, builds the
 ; convergents p_k/q_k by the integer recurrence, and checks the
 ; unimodularity invariant |p_k*q_(k-1) - p_(k-1)*q_k| = 1 for every
 ; k — a purely algebraic self-check, no floating point. Final
 ; convergent is the known 14665106/5394991.
 ;
TEST ;
 S OUT=""
 K ^CF
 S ^CF(1)=2
 S K=2
 F M=1:1:6 D CFT
 S P2=0 S P1=1
 S Q2=1 S Q1=0
 S OKCNT=0
 F K=1:1:19 D CFC
 I OKCNT=19 S OUT=OUT_"CF1 ok unimodular x19 (|p_k q_(k-1) - p_(k-1) q_k|=1)"_$C(10)
 I OKCNT'=19 S OUT=OUT_"CF1 FAIL okcnt="_OKCNT_$C(10)
 I P1=14665106 I Q1=5394991 S OUT=OUT_"CF2 ok p19/q19=14665106/5394991"_$C(10)
 I P1'=14665106 S OUT=OUT_"CF2 FAIL p19="_P1_$C(10)
 I Q1'=5394991 S OUT=OUT_"CF2b FAIL q19="_Q1_$C(10)
 W OUT
 Q
 ;
CFT ; append the triple (1, 2*M, 1) at K, K+1, K+2
 S ^CF(K)=1
 S K=K+1
 S ^CF(K)=2*M
 S K=K+1
 S ^CF(K)=1
 S K=K+1
 Q
 ;
CFC ; fold term K into the convergents; check unimodularity
 S A=^CF(K)
 S PN=A*P1+P2
 S QN=A*Q1+Q2
 S D=(PN*Q1)-(P1*QN)
 I D=1 G CFOK
 I D=-1 G CFOK
 G CFDONE
CFOK ;
 S OKCNT=OKCNT+1
CFDONE ;
 S P2=P1
 S P1=PN
 S Q2=Q1
 S Q1=QN
 Q
