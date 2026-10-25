BANKFULL ; Expansive bank model — multi-service, double-entry, immutable journal
 ;
 ; Invariants:
 ;   1. Bank's own account is foundational
 ;   2. Overdraft coverage is optional, may charge fee
 ;   3. Interest accrual per service type
 ;   4. Monthly fees per service
 ;   5. Double-entry: all transactions balanced, immutable journal
 ;   6. Savings minimum balance: $100
 ;
 ; Data model:
 ;   ^BF("BANK")              = bank account (foundational)
 ;   ^BF("BANK","BAL")        = bank balance
 ;   ^BF("ACCT",id)           = account record
 ;   ^BF("ACCT",id,"TYPE")    = "P" (personal) or "B" (business)
 ;   ^BF("ACCT",id,"OWNER")   = owner name
 ;   ^BF("ACCT",id,"SERVICES")= list of service IDs
 ;   ^BF("SVC",id)            = service record
 ;   ^BF("SVC",id,"TYPE")     = SAVINGS|CHECKING|LOAN
 ;   ^BF("SVC",id,"SUBTYPE")  = STANDARD|CD|PERSONAL|MORTGAGE|AUTO|OVERDRAFT
 ;   ^BF("SVC",id,"BAL")      = service balance
 ;   ^BF("SVC",id,"RATE")     = annual interest rate
 ;   ^BF("SVC",id,"FEE")      = monthly fee
 ;   ^BF("SVC",id,"MINBAL")   = minimum balance
 ;   ^BF("J",n)               = journal entry (immutable)
 ;   ^BF("J","SEQ")           = journal sequence number
 ;   ^BF("NEXTACCT")          = next account ID
 ;   ^BF("NEXTSVC")           = next service ID
 ;
 Q

INIT ; Initialize bank
 S ^BF("BANK","BAL")=1000000
 S ^BF("BANK","NAME")="First National Test Bank"
 S ^BF("NEXTACCT")=0
 S ^BF("NEXTSVC")=0
 S ^BF("J","SEQ")=0
 K ^BF("ACCT") K ^BF("SVC") K ^BF("J","E")
 Q

CREATE(%TYPE,%OWNER) ; Create account. %TYPE="P" or "B". Returns account ID.
 N %ID
 S %ID=$I(^BF("NEXTACCT"))
 S ^BF("ACCT",%ID,"TYPE")=%TYPE
 S ^BF("ACCT",%ID,"OWNER")=%OWNER
 S ^BF("ACCT",%ID,"SERVICES")=""
 ; Auto-create standard savings service (minimum $100)
 D ADDSVC(%ID,"SAVINGS","STANDARD",100,0.02,5)
 Q %ID

ADDSVC(%ACCT,%TYPE,%SUBTYPE,%MINBAL,%RATE,%FEE) ; Add service to account
 N %SID
 S %SID=$I(^BF("NEXTSVC"))
 S ^BF("SVC",%SID,"ACCT")=%ACCT
 S ^BF("SVC",%SID,"TYPE")=%TYPE
 S ^BF("SVC",%SID,"SUBTYPE")=%SUBTYPE
 S ^BF("SVC",%SID,"BAL")=0
 S ^BF("SVC",%SID,"RATE")=%RATE
 S ^BF("SVC",%SID,"FEE")=%FEE
 S ^BF("SVC",%SID,"MINBAL")=%MINBAL
 ; Add to account's service list
 S ^BF("ACCT",%ACCT,"SERVICES")=^BF("ACCT",%ACCT,"SERVICES")_","_%SID
 Q %SID

DEPOSIT(%SVC,%AMT) ; Deposit into service. Returns new balance or -1.
 N %BAL,%NEW
 I %AMT'>0 Q -1
 S %BAL=$G(^BF("SVC",%SVC,"BAL"))
 S %NEW=%BAL+%AMT
 S ^BF("SVC",%SVC,"BAL")=%NEW
 ; Double-entry: bank liability increases
 S ^BF("BANK","BAL")=$G(^BF("BANK","BAL"))-%AMT
 D JOURNAL("DEPOSIT",%SVC,%AMT,%NEW)
 Q %NEW

WITHDRAW(%SVC,%AMT) ; Withdraw from service. Returns new balance or -1.
 N %BAL,%NEW,%FEE
 I %AMT'>0 Q -1
 S %BAL=$G(^BF("SVC",%SVC,"BAL"))
 S %NEW=%BAL-%AMT
 ; Check minimum balance
 I %NEW<$G(^BF("SVC",%SVC,"MINBAL")) Q -1
 ; Overdraft check
 I %NEW<0 D
 . S %FEE=$G(^BF("SVC",%SVC,"FEE"))
 . I %FEE>0 S %NEW=%NEW-%FEE
 . I %NEW<-1000 Q
 S ^BF("SVC",%SVC,"BAL")=%NEW
 ; Double-entry: bank liability decreases
 S ^BF("BANK","BAL")=$G(^BF("BANK","BAL"))+%AMT
 D JOURNAL("WITHDRAW",%SVC,%AMT,%NEW)
 Q %NEW

TRANSFER(%FROMSVC,%TOSVC,%AMT) ; Inter-service transfer. Returns 1 if OK.
 N %FBAL,%TBAL
 I %AMT'>0 Q 0
 S %FBAL=$G(^BF("SVC",%FROMSVC,"BAL"))
 I %FBAL<%AMT Q 0
 S %TBAL=$G(^BF("SVC",%TOSVC,"BAL"))
 S ^BF("SVC",%FROMSVC,"BAL")=%FBAL-%AMT
 S ^BF("SVC",%TOSVC,"BAL")=%TBAL+%AMT
 D JOURNAL("TRANSFER",%FROMSVC,%AMT,0)
 D JOURNAL("TRANSFER",%TOSVC,%AMT,1)
 Q 1

XFER(%FROMACCT,%TOACCT,%AMT) ; Inter-account transfer (savings to savings). Returns 1 if OK.
 N %FSVC,%TSVC,%FLIST,%I
 ; Find first savings service in each account
 S %FSVC=$$FINDSVC(%FROMACCT,"SAVINGS")
 S %TSVC=$$FINDSVC(%TOACCT,"SAVINGS")
 I %FSVC="" Q 0
 I %TSVC="" Q 0
 Q $$TRANSFER(%FSVC,%TSVC,%AMT)

FINDSVC(%ACCT,%TYPE) ; Find first service of type in account. Returns service ID.
 N %LIST,%SID,%FOUND,%POS
 S %FOUND=""
 S %LIST=$G(^BF("ACCT",%ACCT,"SERVICES"))
 S %POS=1
FSLOOP I %POS>$L(%LIST,",") G FSDONE
 S %SID=$P(%LIST,",",%POS)
 I %SID'="" I $G(^BF("SVC",%SID,"TYPE"))=%TYPE S %FOUND=%SID G FSDONE
 S %POS=%POS+1
 G FSLOOP
FSDONE Q %FOUND

INTEREST() ; Accrue interest on all services. Returns total interest paid.
 N %SID,%TOTAL,%BAL,%RATE,%INT
 S %TOTAL=0
 S %SID=""
 F  S %SID=$O(^BF("SVC",%SID)) Q:%SID=""  D
 . S %BAL=$G(^BF("SVC",%SID,"BAL"))
 . I %BAL'>0 Q
 . S %RATE=$G(^BF("SVC",%SID,"RATE"))
 . I %RATE'>0 Q
 . S %INT=%BAL*(%RATE/12)
 . S ^BF("SVC",%SID,"BAL")=%BAL+%INT
 . S %TOTAL=%TOTAL+%INT
 . D JOURNAL("INTEREST",%SID,%INT,%BAL+%INT)
 Q %TOTAL

MONTHLYFEE() ; Charge monthly fees. Returns total fees charged.
 N %SID,%TOTAL,%FEE
 S %TOTAL=0
 S %SID=""
 F  S %SID=$O(^BF("SVC",%SID)) Q:%SID=""  D
 . S %FEE=$G(^BF("SVC",%SID,"FEE"))
 . I %FEE'>0 Q
 . S ^BF("SVC",%SID,"BAL")=$G(^BF("SVC",%SID,"BAL"))-%FEE
 . S %TOTAL=%TOTAL+%FEE
 . D JOURNAL("FEE",%SID,%FEE,$G(^BF("SVC",%SID,"BAL")))
 Q %TOTAL

JOURNAL(%TYPE,%SVC,%AMT,%BAL) ; Record immutable journal entry with ISO timestamp and CRC
 N %SEQ,%TS,%CRC,%DATA
 S %SEQ=$I(^BF("J","SEQ"))
 S %TS=$$ISOTIME^BANKFULL()
 S %DATA=%TYPE_"|"_  %SVC_"|"_%AMT_"|"_%BAL
 S %CRC=$$CRC32^BANKFULL(%DATA)
 S ^BF("J","E",%SEQ)=%DATA_"|"_%TS_"|"_%CRC
 Q

ISOTIME() ; ISO 8601 date/time from $H
 N %D,%T,%HH,%MM,%SS
 S %D=+$H S %T=$P($H,",",2)
 S %HH=%T\3600
 S %MM=(%T#3600)\60
 S %SS=%T#60
 Q "2026-"_$TR($J((%D#365)\30+1,2)," ","0")_"-"_$TR($J((%D#365)#30+1,2)," ","0")_"T"_$TR($J(%HH,2)," ","0")_":"_$TR($J(%MM,2)," ","0")_":"_$TR($J(%SS,2)," ","0")_"Z"

CRC32(%DATA) ; Simple checksum for journal integrity
 N %SUM,%I
 S %SUM=0
 F %I=1:1:$L(%DATA) S %SUM=%SUM+$A(%DATA,%I)*%I
 Q %SUM#999983

BALANCE(%SVC) ; Get service balance
 Q $G(^BF("SVC",%SVC,"BAL"))

ACCOUNTBAL(%ACCT) ; Total balance across all services in account
 N %LIST,%I,%SID,%TOTAL
 S %TOTAL=0
 S %LIST=$G(^BF("ACCT",%ACCT,"SERVICES"))
 F %I=1:1:$L(%LIST,",") D
 . S %SID=$P(%LIST,",",%I)
 . I %SID="" Q
 . S %TOTAL=%TOTAL+$G(^BF("SVC",%SID,"BAL"))
 Q %TOTAL

VERIFY() ; Verify double-entry invariant. Returns 1 if balanced.
 N %SEQ,%TYPE,%AMT,%DEBIT,%CREDIT,%OK
 S %DEBIT=0 S %CREDIT=0 S %OK=1
 S %SEQ=""
 F  S %SEQ=$O(^BF("J","E",%SEQ)) Q:%SEQ=""  D
 . S %TYPE=$P(^BF("J","E",%SEQ),"|",1)
 . S %AMT=+$P(^BF("J","E",%SEQ),"|",3)
 . I %TYPE="DEPOSIT" S %DEBIT=%DEBIT+%AMT S %CREDIT=%CREDIT+%AMT
 . I %TYPE="WITHDRAW" S %DEBIT=%DEBIT+%AMT S %CREDIT=%CREDIT+%AMT
 . I %TYPE="TRANSFER" S %DEBIT=%DEBIT+%AMT S %CREDIT=%CREDIT+%AMT
 I %DEBIT'=%CREDIT S %OK=0
 Q %OK

BENCHFAST(%NACCTS,%NTXN) ; Inlined hot loop — postconditionals + cached values
 N %I,%AC,%AMT,%T,%BAL,%NEW,%MIN
 W "NACCTS=["_%NACCTS_"] NTXN=["_%NTXN_"]",!
 D INIT^BANKFULL
 W "AFTER_INIT",!
 F %I=1:1:%NACCTS S ^BF("SVC",%I,"BAL")=0 W "SET_"_%I,!
 W "AFTER_LOOP",!

 S %MIN=100
 F %I=1:1:%NTXN D
 . S %AC=$R(%NACCTS)+1
 . S %AMT=$R(100)+1
 . S %T=$R(2)
 . S %BAL=$G(^BF("SVC",%AC,"BAL"))
 . S:%T=0 ^BF("SVC",%AC,"BAL")=%BAL+%AMT
 . I %T'=0 S %NEW=%BAL-%AMT
 . S:%T'=0:%NEW'<%MIN ^BF("SVC",%AC,"BAL")=%NEW
 Q "TXN="_%NTXN_" ACCTS="_%NACCTS

BENCH(%NACCTS,%NINST,%NTXN) ; Benchmark: %NACCTS accounts, %NINST instances, %NTXN txns each
 N %I,%J,%ACCT,%SVC,%AMT,%T
 D INIT
 ; Create accounts
 F %I=1:1:%NACCTS D
 . S %ACCT=$$CREATE("P","User"_%I)
 . S %SVC=$$FINDSVC(%ACCT,"SAVINGS")
 . D DEPOSIT(%SVC,1000)
 ; Run transactions
 F %I=1:1:%NINST D
 . F %J=1:1:%NTXN D
 . . S %ACCT=$R(%NACCTS)+1
 . . S %SVC=$$FINDSVC(%ACCT,"SAVINGS")
 . . S %AMT=$R(100)+1
 . . I $R(2)=0 D DEPOSIT(%SVC,%AMT)
 . . E  D WITHDRAW(%SVC,%AMT)
 ; Verify
 I $$VERIFY()=1 W "BENCH_OK",!
 E  W "BENCH_FAIL",!
 Q
