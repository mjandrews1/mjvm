SIEVE ; tools/sieve.m — Eratosthenes to 10000 in M
 ; P1d workstream D (arithmetic suite). Dense global subscript
 ; marking, KILL of composites, $O walk. Known answers: 1229 primes
 ; below 10000, largest 9973, 100th prime 541, sum of primes < 100
 ; = 1060.
 ;
TEST ;
 S OUT=""
 K ^SV
 F I=2:1:10000 S ^SV(I)=""
 F I=2:1:316 D SIEVEM
 S CNT=0
 S SUM100=0
 S LAST=0
 S P100=0
 S KY=""
SVL ;
 S KY=$O(^SV(KY))
 I KY="" G SVD
 S CNT=CNT+1
 S LAST=KY
 I KY<100 S SUM100=SUM100+KY
 I CNT=100 S P100=KY
 G SVL
SVD ;
 K ^SV
 I CNT=1229 S OUT=OUT_"V1 ok pi(10000)=1229"_$C(10)
 I CNT'=1229 S OUT=OUT_"V1 FAIL got "_CNT_$C(10)
 I LAST=9973 S OUT=OUT_"V2 ok largest=9973"_$C(10)
 I LAST'=9973 S OUT=OUT_"V2 FAIL got "_LAST_$C(10)
 I P100=541 S OUT=OUT_"V3 ok p(100)=541"_$C(10)
 I P100'=541 S OUT=OUT_"V3 FAIL got "_P100_$C(10)
 I SUM100=1060 S OUT=OUT_"V4 ok sum-primes-below-100=1060"_$C(10)
 I SUM100'=1060 S OUT=OUT_"V4 FAIL got "_SUM100_$C(10)
 W OUT
 Q
 ;
SIEVEM ; strike multiples of prime I (I*I..10000 step I)
 I $D(^SV(I))=0 G SMEND
 S J=I*I
SML ;
 I J>10000 G SMEND
 K ^SV(J)
 S J=J+I
 G SML
SMEND ;
 Q
