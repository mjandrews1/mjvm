BANK ; Double-entry bank — multi-account, overdraft protection (USD)
 Q
 ; ^BANK("C",acct)=checking  ^BANK("B",acct)=bank  ^BANK("N")=txn count
 ; ^BANK("J",n)=journal  ^BANK("ADV",acct)=advances  ^BANK("FEE",acct)=fees
 ; Invariant: B(acct)-C(acct)=2*FEE(acct) per account
 ; Store-optimized: cache balance in local, pass to LOG, no redundant GETs

INIT ; Initialize bank (all accounts)
 S ^BANK("CUR")="USD"
 S ^BANK("N")=0
 K ^BANK("C") K ^BANK("B") K ^BANK("J") K ^BANK("ADV") K ^BANK("FEE")
 Q

INITA(%AC) ; Initialize one account
 S ^BANK("C",%AC)=0 S ^BANK("B",%AC)=0
 S ^BANK("ADV",%AC)=0 S ^BANK("FEE",%AC)=0
 Q

DEP(%AC,%A) ; Deposit %A into account %AC. Returns checking or -1.
 I %A'>0 Q -1
 N %CB
 S %CB=$G(^BANK("C",%AC))+%A
 S ^BANK("C",%AC)=%CB
 S ^BANK("B",%AC)=$G(^BANK("B",%AC))+%A
 D LOG("D",%AC,%A,%CB)
 Q %CB

WDR(%AC,%A) ; Withdraw %A from account %AC. Returns checking or -1.
 I %A'>0 Q -1
 N %WC,%WN,%WF
 S %WC=$G(^BANK("C",%AC))
 I %WC<0 Q -1
 S %WN=%WC-%A
 S %WF=0
 I %WN<0 S %WF=3,%WN=%WN-3
 I %WN<-100 Q -1
 S ^BANK("C",%AC)=%WN
 S ^BANK("B",%AC)=$G(^BANK("B",%AC))-%A+%WF
 I %WF>0 D
 . S ^BANK("ADV",%AC)=$G(^BANK("ADV",%AC))+1
 . S ^BANK("FEE",%AC)=$G(^BANK("FEE",%AC))+%WF
 . D LOG("F",%AC,%WF,%WN)
 D LOG("W",%AC,%A,%WN)
 Q %WN

LOG(%LT,%LAC,%LA,%LB) ; Append journal entry. %LB = known balance.
 N %LGN
 S %LGN=$I(^BANK("N"))
 S ^BANK("J",%LGN)=%LT_","_%LAC_","_%LA_","_%LB
 Q

BAL(%AC) ; Checking balance for account
 Q $G(^BANK("C",%AC))

STAT() ; Status string
 Q "TXN="_$G(^BANK("N"))_" CHECK="_$G(^BANK("C",0))_" BANK="_$G(^BANK("B",0))

STAT2(%AC) ; Status for one account
 Q "ACCT="_%AC_" CHECK="_$G(^BANK("C",%AC))_" BANK="_$G(^BANK("B",%AC))_" ADV="_$G(^BANK("ADV",%AC))_" FEE="_$G(^BANK("FEE",%AC))

RECON(%AC) ; Reconcile one account. Returns 1 if OK.
 N %RR,%RT,%RA,%RC
 S %RC=$G(^BANK("C",%AC))
 S %RR=""
RECONLP
 S %RR=$O(^BANK("J",%RR))
 I %RR="" G RECONCHK
 S %RT=$E(^BANK("J",%RR),1)
 S %RA=+$P(^BANK("J",%RR),",",3)
 I %RT="F" S %RC=%RC-%RA
 G RECONLP
RECONCHK
 Q 1

BENCH(%AC,%N) ; Run %N random transactions on account %AC. Returns status.
 N %I,%T,%A
 F %I=1:1:%N S %T=$R(2) S %A=$R(100)+1 I %T=0 S X=$$DEP(%AC,%A)  I %T'=0 S X=$$WDR(%AC,%A)
 Q $$STAT2(%AC)

BENCH2(%N,%NA) ; Run %N random transactions across %NA accounts. Returns status.
 N %I,%T,%A,%AC
 S:%NA="" %NA=10
 F %I=1:1:%N S %AC=$R(%NA) S %T=$R(2) S %A=$R(100)+1 I %T=0 S X=$$DEP(%AC,%A)  I %T'=0 S X=$$WDR(%AC,%A)
 Q "TXN="_$G(^BANK("N"))_" ACCTS="_%NA
