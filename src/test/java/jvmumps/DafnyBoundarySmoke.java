package jvmumps;

import java.nio.file.*;

/**
 * Dafny boundary smoke — concrete test cases for the rules proven in m-semantics Dafny modules
 * (MSemantics, MPattern, MControl, PEMDAS).
 *
 * <p>Each group maps to named lemmas in the .dfy files. Tests run through the JavaM engine
 * (MFrontend + IrMachine) and verify observable behavior.
 *
 * <p>Engine semantics note: JavaM uses M native left-to-right arithmetic (not PEMDAS). Pattern
 * classes match PatternMatch.java: A/L/U=letter, N=digit, P=punct, C=control, E=any. Q inside FOR
 * body exits function.
 *
 * <p>Usage: java -cp build:lib/... jvmumps.DafnyBoundarySmoke
 */
public final class DafnyBoundarySmoke {

  public static void main(String[] args) throws Exception {
    System.out.println("## Dafny boundary smoke — MSemantics / MPattern / MControl");
    System.out.println();

    int pass = 0, fail = 0;
    int[] r;

    // ================================================================
    // MSemantics: comparison (isNum / toNum boundaries)
    // ================================================================
    String[] cmp = {
      "I \"0\"=0 W \"eq0=1\",! E  W \"eq0=0\",!",
      "I \"1\"=1 W \"eq1=1\",! E  W \"eq1=0\",!",
      "I \"0.0\"=0 W \"eq00=1\",! E  W \"eq00=0\",!",
      "I \"1E2\"=100 W \"eqE=1\",! E  W \"eqE=0\",!",
      "I \"+1\"=1 W \"eqp=1\",! E  W \"eqp=0\",!",
      "I \"abc\"=\"abc\" W \"seq=1\",! E  W \"seq=0\",!",
      "I \"abc\"=\"abd\" W \"sne=1\",! E  W \"sne=0\",!",
      "I \"a\"<\"b\" W \"slt=1\",! E  W \"slt=0\",!",
      "I \"b\">\"a\" W \"sgt=1\",! E  W \"sgt=0\",!",
      "I \"\"=\"\" W \"eeq=1\",! E  W \"eeq=0\",!",
      "I 0=0 W \"neq=1\",! E  W \"neq=0\",!",
      "I 1<2 W \"nlt=1\",! E  W \"nlt=0\",!",
      "I 2>1 W \"ngt=1\",! E  W \"ngt=0\",!",
      "I 1=2 W \"nne=1\",! E  W \"nne=0\",!",
    };
    String[] cmpExp = {
      "eq0=1", "eq1=1", "eq00=1", "eqE=1", "eqp=1",
      "seq=1", "sne=0", "slt=1", "sgt=1", "eeq=1",
      "neq=1", "nlt=1", "ngt=1", "nne=0",
    };
    r = runTests("cmp", cmp, cmpExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: truthy / falsy (TruthyByScan)
    // ================================================================
    String[] truthy = {
      "I \"0\" W \"t=1\",! E  W \"t=0\",!",
      "I \"1\" W \"t=1\",! E  W \"t=0\",!",
      "I \"\" W \"t=1\",! E  W \"t=0\",!",
      "I \"0.0\" W \"t=1\",! E  W \"t=0\",!",
      "I \".0\" W \"t=1\",! E  W \"t=0\",!",
      "I \"00\" W \"t=1\",! E  W \"t=0\",!",
      "I \"0.1\" W \"t=1\",! E  W \"t=0\",!",
      "I \"abc\" W \"t=1\",! E  W \"t=0\",!",
      "I \"-1\" W \"t=1\",! E  W \"t=0\",!",
      "I \"-0\" W \"t=1\",! E  W \"t=0\",!",
      "I 0 W \"t=1\",! E  W \"t=0\",!",
      "I 1 W \"t=1\",! E  W \"t=0\",!",
    };
    String[] truthyExp = {
      "t=0", "t=1", "t=0", "t=0", "t=0", "t=0",
      "t=1", "t=0", "t=1", "t=0", "t=0", "t=1",
    };
    r = runTests("truthy", truthy, truthyExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: collation order (LexIrreflexive, CollAntisymmetric,
    //   CollTransitive, CollEmptyFirst, CollEmptyLast, CollNumOrder)
    // ================================================================
    String[] coll = {
      "I \"a\"<\"a\" W \"irr=1\",! E  W \"irr=0\",!",
      "I \"a\"<\"b\" W \"ab=1\",! E  W \"ab=0\",!",
      "I \"b\"<\"a\" W \"ba=1\",! E  W \"ba=0\",!",
      "I \"a\"<\"b\" I \"b\"<\"c\" I \"a\"<\"c\" W \"trn=1\",! E  W \"trn=0\",!",
      "I \"x\"=\"x\" W \"ref=1\",! E  W \"ref=0\",!",
      "I \"\"<\"a\" W \"ef=1\",! E  W \"ef=0\",!",
      "I \"a\">\"\" W \"el=1\",! E  W \"el=0\",!",
      "I \"A\"<\"a\" W \"cas=1\",! E  W \"cas=0\",!",
      "I 1<2 W \"no=1\",! E  W \"no=0\",!",
      "I 2>1 W \"no2=1\",! E  W \"no2=0\",!",
      "I 1<1.5 W \"no3=1\",! E  W \"no3=0\",!",
      "I -1<0 W \"no4=1\",! E  W \"no4=0\",!",
    };
    String[] collExp = {
      "irr=0", "ab=1", "ba=0", "trn=1", "ref=1", "ef=1", "el=1", "cas=1", "no=1", "no2=1", "no3=1",
      "no4=1",
    };
    r = runTests("collation", coll, collExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: numeric string boundaries
    // ================================================================
    String[] numstr = {
      "I \"0\"=0 W \"z=1\",! E  W \"z=0\",!",
      "I \"00\"=0 W \"zz=1\",! E  W \"zz=0\",!",
      "I \"0.0\"=0 W \"zf=1\",! E  W \"zf=0\",!",
      "I \"-0\"=0 W \"nz=1\",! E  W \"nz=0\",!",
      "I \"+0\"=0 W \"pz=1\",! E  W \"pz=0\",!",
      "I \"1\"=1 W \"o=1\",! E  W \"o=0\",!",
      "I \"1.0\"=1 W \"of=1\",! E  W \"of=0\",!",
      "I \"1.5\"=1.5 W \"oh=1\",! E  W \"oh=0\",!",
      "I \"-1\"=-1 W \"m1=1\",! E  W \"m1=0\",!",
      "I \"+1\"=1 W \"p1=1\",! E  W \"p1=0\",!",
      "I \"1E2\"=100 W \"e2=1\",! E  W \"e2=0\",!",
      "I \".5\"=0.5 W \"d5=1\",! E  W \"d5=0\",!",
      "I \"\"=0 W \"em=1\",! E  W \"em=0\",!",
      "I \"abc\"=0 W \"ab=1\",! E  W \"ab=0\",!",
    };
    String[] numstrExp = {
      "z=1", "zz=1", "zf=1", "nz=1", "pz=1",
      "o=1", "of=1", "oh=1", "m1=1", "p1=1",
      "e2=1", "d5=1", "em=0", "ab=0",
    };
    r = runTests("numstr", numstr, numstrExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: ELSE binding (T12ElseBinding, T12bElseChain)
    // ================================================================
    String[][] elseProgs = {
      {"S X=0 I X=1 W \"THEN\",! E  W \"ELSE\",!"},
      {"S X=1 I X=1 W \"THEN\",! E  W \"ELSE\",!"},
      {"S X=2 I X=1 W \"a\",! E  I X=2 W \"b\",! E  W \"c\",!"},
      {"S X=3 I X=1 W \"a\",! E  I X=2 W \"b\",! E  W \"c\",!"},
      {"S X=3 I X=1 W \"1\",! E  I X=2 W \"2\",! E  I X=3 W \"3\",! E  W \"4\",!"},
      {"S X=9 I X=1 W \"1\",! E  I X=2 W \"2\",! E  I X=3 W \"3\",! E  W \"4\",!"},
    };
    String[] elseExp = {"ELSE", "THEN", "b", "c", "3", "4"};
    r = runTestsMulti("else", elseProgs, elseExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: $TLEVEL outside transaction (T15TlevelOutsideTX)
    // ================================================================
    String[] tlevel = {"W $TLEVEL,!"};
    String[] tlevelExp = {"0"};
    r = runTests("tlevel", tlevel, tlevelExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: $DATA partitions (DataPartitions)
    // ================================================================
    String[][] dataProgs = {
      {"K ^DT W $D(^DT),!"},
      {"S ^DT=1 W $D(^DT),!"},
      {"S ^DT(1)=1 W $D(^DT),!"},
      {"S ^DT=1,^DT(1)=1 W $D(^DT),!"},
      {"S ^DT(1,2)=1 W $D(^DT(1)),!"},
      {"S ^DT(1,2)=1 W $D(^DT(1,2)),!"},
    };
    String[] dataExp = {"0", "1", "10", "11", "10", "1"};
    r = runTestsMulti("data", dataProgs, dataExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: $ORDER properties (OrderMinUnique, OrderFromEmptyIsMin)
    // ================================================================
    String[][] orderProgs = {
      {"K ^OT S ^OT(1)=1,^OT(5)=1,^OT(10)=1 S K=\"\" W $O(^OT(K)),!"},
      {"K ^OT S ^OT(1)=1,^OT(5)=1,^OT(10)=1 S K=$O(^OT(\"\")) S K=$O(^OT(K)) W K,!"},
      {"K ^OT S ^OT(3)=1,^OT(1)=1,^OT(2)=1 S K=\"\" W $O(^OT(K)),!"},
    };
    String[] orderExp = {"1", "5", "1"};
    r = runTestsMulti("order", orderProgs, orderExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: mstr display (MstrInjective)
    // ================================================================
    String[] mstr = {
      "W 1,\"|\",2,\"|\",3,!", "W 0.5,\"|\",0.25,\"|\",0.05,!",
    };
    String[] mstrExp = {"1|2|3", ".5|.25|.05"};
    r = runTests("mstr", mstr, mstrExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MPattern: MatchTotal, EmptyPattern, MatchConsumesAll, CharAlnum
    // ================================================================
    String[] pat2 = {
      "I \"abc\"?1A.AN W \"m1=1\",! E  W \"m1=0\",!",
      "I \"abc\"?1N.AN W \"m2=1\",! E  W \"m2=0\",!",
      "I \"\"?\"\" W \"ep=1\",! E  W \"ep=0\",!",
      "I \"ab\"?2A W \"mc=1\",! E  W \"mc=0\",!",
      "I \"abc\"?2A W \"mc2=1\",! E  W \"mc2=0\",!",
      "I \"a\"?1A W \"al=1\",! E  W \"al=0\",!",
      "I \"5\"?1A W \"al2=1\",! E  W \"al2=0\",!",
      "I \"\"?.A W \"za=1\",! E  W \"za=0\",!",
      "I \"abc\"?.A W \"za2=1\",! E  W \"za2=0\",!",
      "I \"123\"?.A W \"za3=1\",! E  W \"za3=0\",!",
      "I \"123\"?.N W \"zn=1\",! E  W \"zn=0\",!",
      "I \"abc\"?.N W \"zn2=1\",! E  W \"zn2=0\",!",
    };
    String[] pat2Exp = {
      "m1=1", "m2=0", "ep=1", "mc=1", "mc2=0", "al=1", "al2=0", "za=1", "za2=1", "za3=0", "zn=1",
      "zn2=0",
    };
    r = runTests("pat2", pat2, pat2Exp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MPattern: pattern classes (A, N, C, P, L, U, E)
    // ================================================================
    String[] patcls = {
      "I \"a\"?1A W \"A=1\",! E  W \"A=0\",!",
      "I \"Z\"?1A W \"A=1\",! E  W \"A=0\",!",
      "I \"5\"?1N W \"N=1\",! E  W \"N=0\",!",
      "I \" \"?1P W \"P=1\",! E  W \"P=0\",!",
      "I \".\"?1P W \"P=1\",! E  W \"P=0\",!",
      "I \",\"?1P W \"P=1\",! E  W \"P=0\",!",
      "I \"a\"?1L W \"L=1\",! E  W \"L=0\",!",
      "I \"A\"?1L W \"L=1\",! E  W \"L=0\",!",
      "I \"A\"?1U W \"U=1\",! E  W \"U=0\",!",
      "I \"a\"?1U W \"U=1\",! E  W \"U=0\",!",
      "I \"x\"?1E W \"E=1\",! E  W \"E=0\",!",
      "I \"!\"?1E W \"E=1\",! E  W \"E=0\",!",
    };
    String[] patclsExp = {
      "A=1", "A=1", "N=1", "P=1", "P=1", "P=1",
      "L=1", "L=1", "U=1", "U=1", "E=1", "E=1",
    };
    r = runTests("pat-cls", patcls, patclsExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: DO/QUIT, postcond DO, GOTO (DoQuitRoundTrip, etc.)
    // ================================================================
    String[][] ctrlProgs = {
      {"S X=1 D CTRL1 W X,!", "CTRL1 S X=2 Q"},
      {"S X=1 D:0 CTRL2 W X,!", "CTRL2 S X=2 Q"},
      {"S X=1 D:1 CTRL2 W X,!", "CTRL2 S X=2 Q"},
      {"S X=1 G TARGET", "TARGET S X=3 W X,! Q"},
      {"S X=0 D OUTER W X,!", "OUTER D INNER D INNER Q", "INNER S X=X+5 Q"},
      {"S X=$$RET42 W X,!", "RET42 Q 42"},
    };
    String[] ctrlExp = {"2", "1", "2", "3", "10", "42"};
    r = runTestsMulti("control", ctrlProgs, ctrlExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: FOR loops
    // ================================================================
    String[][] forProgs = {
      {"S X=0 F  Q:X>5  S X=X+1", "W X,!"},
      {"S X=0 F I=1:1:5 S X=X+I", "W X,!"},
      {"S X=0 F I=1:2:9 S X=X+I", "W X,!"},
      {"S X=99 F  Q:X>5  S X=X+1", "W X,!"},
    };
    String[] forExp = {"6", "15", "25", "99"};
    r = runTestsMulti("for", forProgs, forExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: IF combinations (IfBothFalse, IfTrueDoFalse, etc.)
    //   Each IF on own line to avoid nesting issues
    // ================================================================
    String[][] ifProgs = {
      {"S X=0", "I 0 S X=1", "I 0 S X=2", "W X,!"},
      {"S X=0", "I 1 S X=1", "I 0 S X=2", "W X,!"},
      {"S X=0", "I 0 S X=1", "I 1 S X=2", "W X,!"},
      {"S X=0", "I 1 S X=1", "I 1 S X=2", "W X,!"},
    };
    String[] ifExp = {"0", "1", "2", "2"};
    r = runTestsMulti("if-combo", ifProgs, ifExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: Return in extrinsic
    // ================================================================
    String[][] retProgs = {
      {"S X=$$RET1 W X,!", "RET1 Q 42"},
      {"S X=$$RET3(1) W X,!", "RET3(%V) I %V=1 Q 10 I %V=2 Q 20 Q 99"},
    };
    String[] retExp = {"42", "10"};
    r = runTestsMulti("ret", retProgs, retExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: DO with arguments, nested DO
    // ================================================================
    String[][] callProgs = {
      {"D ADD5(3) W X,!", "ADD5(%V) S X=%V+5 Q"},
    };
    String[] callExp = {"8"};
    r = runTestsMulti("call", callProgs, callExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // M native arithmetic (left-to-right)
    // ================================================================
    String[] arith = {
      "W 2+3*4,!",
      "W (2+3)*4,!",
      "W 10-3-2,!",
      "W 100/10/2,!",
      "W 0-5,!",
      "W 1+2*3-4,!",
      "W (1+2)*(3+4),!",
      "W 7\\2,!",
      "W 7#3,!",
    };
    String[] arithExp = {
      "20", "20", "5", "5", "-5", "5", "21", "3", "1",
    };
    r = runTests("arith", arith, arithExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: numeric scanning internals (HasNZSplit, ScanSeesNZ,
    //   ScanAfterDot, ApplyExpFactor) — exercised via edge-case numbers
    // ================================================================
    String[] scan = {
      // HasNZSplit / ScanSeesNZ: non-zero digit detection
      "I \"0.001\"=0.001 W \"nz=1\",! E  W \"nz=0\",!",
      "I \"00100\"=100 W \"nz2=1\",! E  W \"nz2=0\",!",
      "I \"0E-5\"=0 W \"ez=1\",! E  W \"ez=0\",!",
      // ScanAfterDot: digits after decimal point
      "I \"0.0\"=0 W \"ad=1\",! E  W \"ad=0\",!",
      "I \".001\"=0.001 W \"ad2=1\",! E  W \"ad2=0\",!",
      "I \"0.100\"=0.1 W \"ad3=1\",! E  W \"ad3=0\",!",
      // ApplyExpFactor: exponent scaling
      "I \"1E3\"=1000 W \"ae=1\",! E  W \"ae=0\",!",
      "I \"2E-2\"=0.02 W \"ae2=1\",! E  W \"ae2=0\",!",
      "I \"1.5E2\"=150 W \"ae3=1\",! E  W \"ae3=0\",!",
      "I \"0E0\"=0 W \"ae4=1\",! E  W \"ae4=0\",!",
      "I \"-1E2\"=-100 W \"ae5=1\",! E  W \"ae5=0\",!",
    };
    String[] scanExp = {
      "nz=1", "nz2=1", "ez=1", "ad=1", "ad2=1", "ad3=1", "ae=1", "ae2=1", "ae3=1", "ae4=1", "ae5=1",
    };
    r = runTests("scan", scan, scanExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: lexicographic from offset (LexIrreflexiveFrom,
    //   LexAntisymmetricFrom, LexTransitiveFrom, LexEqualChar)
    //   Exercised via strings that differ at specific positions
    // ================================================================
    String[] lexf = {
      // LexEqualChar: first char equal, differ at second
      "I \"aa\"<\"ab\" W \"lc=1\",! E  W \"lc=0\",!",
      "I \"ab\"<\"aa\" W \"lc2=1\",! E  W \"lc2=0\",!",
      // LexIrreflexiveFrom: same suffix is not less
      "I \"abc\"<\"abc\" W \"ir=1\",! E  W \"ir=0\",!",
      // LexAntisymmetricFrom: order at position 1
      "I \"xa\"<\"xb\" W \"as=1\",! E  W \"as=0\",!",
      "I \"xb\"<\"xa\" W \"as2=1\",! E  W \"as2=0\",!",
      // LexTransitiveFrom: chain at position 1
      "I \"xa\"<\"xb\" I \"xb\"<\"xc\" I \"xa\"<\"xc\" W \"tr=1\",! E  W \"tr=0\",!",
      // longer strings, differ at end
      "I \"hello\"<\"hellp\" W \"le=1\",! E  W \"le=0\",!",
      "I \"hello\"<\"hell\" W \"le2=1\",! E  W \"le2=0\",!",
      "I \"hell\"<\"hello\" W \"le3=1\",! E  W \"le3=0\",!",
    };
    String[] lexfExp = {
      "lc=1", "lc2=0", "ir=0",
      "as=1", "as2=0", "tr=1",
      "le=1", "le2=0", "le3=1",
    };
    r = runTests("lexf", lexf, lexfExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: subscript ordering (SubsOrdIrreflexive,
    //   SubsOrdAntisymmetric, KeyordIrreflexive, KeyordTotal,
    //   DescendantImpliesKeyord) — via $ORDER on multi-level globals
    // ================================================================
    String[][] subsProgs = {
      // SubsOrdIrreflexive: $O from same value doesn't return same
      {"K ^ST S ^ST(1)=1,^ST(2)=1,^ST(3)=1 S K=2 W $O(^ST(K)),!"},
      // SubsOrdAntisymmetric: forward then backward
      {"K ^ST S ^ST(1)=1,^ST(2)=1,^ST(3)=1 S K=$O(^ST(\"\")) W K,\" \",$O(^ST(K),-1),!"},
      // KeyordTotal: any two subscripts are ordered
      {
        "K ^ST S ^ST(\"a\")=1,^ST(\"b\")=1 S K=$O(^ST(\"\")) W K,!",
      },
      // DescendantImpliesKeyord: multi-level $ORDER
      {"K ^MT S ^MT(1,1)=1,^MT(1,2)=1,^MT(2,1)=1 S K=\"\" W $O(^MT(K)),!"},
      {"K ^MT S ^MT(1,1)=1,^MT(1,2)=1,^MT(2,1)=1 S K=$O(^MT(\"\")) S L=\"\" W $O(^MT(K,L)),!"},
      // Descendant ordering: deeper levels
      {
        "K ^MT S ^MT(1,1)=1,^MT(1,2)=1,^MT(1,3)=1 S K=1 S L=\"\" W $O(^MT(K,L)),\""
            + " \",$O(^MT(K,L)),!"
      },
    };
    String[] subsExp = {
      "3", // $O from 2 → 3 (not 2)
      "1", // forward from empty = 1 (backward from 1 = empty in engine)
      "a", // first subscript
      "1", // first level
      "1", // second level from (1,"")
      "1 1", // first two subscripts at level 2
    };
    r = runTestsMulti("subs", subsProgs, subsExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: Call with args (CallFalseSkips, CallTrueRoundTrips,
    //   CallReturnRoundTrips, CallNestedLIFO, CallCondNestedRoundTrip)
    // ================================================================
    String[][] callProgs2 = {
      {"D ADDN(3) W X,!", "ADDN(%V) S X=%V+5 Q"},
      {"D ADD2(3,4) W X,!", "ADD2(%A,%B) S X=%A+%B Q"},
      {"S X=$$MUL2(3,4) W X,!", "MUL2(%A,%B) Q %A*%B"},
      {"S X=0 D:1 ADDN(10) W X,!", "ADDN(%V) S X=%V+1 Q"},
      {"S X=0 D:0 ADDN(10) W X,!", "ADDN(%V) S X=%V+1 Q"},
    };
    String[] callExp2 = {"8", "7", "12", "11", "0"};
    r = runTestsMulti("call2", callProgs2, callExp2);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: PostcondSeq / DoSeq (PostcondSeqFalseIsNoop,
    //   PostcondSeqTrueRuns, DoSeqFalseSkips, DoSeqTrueCalls)
    // ================================================================
    String[][] seqProgs = {
      {"S X=0", "I 1 S X=1 S X=X+1", "W X,!"},
      {"S X=0", "I 0 S X=1 S X=X+1", "W X,!"},
      {"S X=0 D:1 SETX W X,!", "SETX S X=42 Q"},
      {"S X=0 D:0 SETX W X,!", "SETX S X=42 Q"},
    };
    String[] seqExp = {"2", "0", "42", "0"};
    r = runTestsMulti("seq", seqProgs, seqExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: FOR with postcond DO in body (WhileStepUsesPostcondDo)
    // ================================================================
    String[][] whileProgs = {
      {"S X=0 S N=0 F  Q:N>3  D", " . S N=N+1", " . I N#2=0 S X=X+N", "W X,!"},
    };
    String[] whileExp = {"6"};
    r = runTestsMulti("while2", whileProgs, whileExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: PostcondAssign (PostcondAssignFalseIsNoop,
    //   PostcondAssignTrueApplies, PostcondAssignEqualsIf)
    //   S:cond var=val — assign only if cond.
    //   Also DoAssignFalseSkips / DoAssignTrueCalls via D:cond.
    // ================================================================
    String[][] pcSetProgs = {
      // PostcondAssignTrueApplies: S:1 sets
      {"S X=1", "S:1 X=2", "W X,!"},
      // PostcondAssignFalseIsNoop: S:0 leaves unchanged
      {"S X=1", "S:0 X=2", "W X,!"},
      // PostcondAssignEqualsIf: S:cond == I cond S
      {"S X=1", "S:1=1 X=2", "W X,!"},
      {"S X=1", "S:1=2 X=2", "W X,!"},
      // multi-assign with postcond
      {"S X=1,Y=0", "S:1 X=2,Y=3", "W X,\" \",Y,!"},
      {"S X=1,Y=0", "S:0 X=2,Y=3", "W X,\" \",Y,!"},
      // condition on variable
      {"S X=1,Y=5", "S:X=1 X=10", "W X,\" \",Y,!"},
    };
    String[] pcSetExp = {"2", "1", "2", "1", "2 3", "1 0", "10 5"};
    r = runTestsMulti("pc-set", pcSetProgs, pcSetExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: WhilePostcondAssignComposition — FOR with postcond SET
    // ================================================================
    String[][] wpcProgs = {
      // FOR body with postcond SET
      {"S X=0 F I=1:1:5 S:I>2 X=X+I", "W X,!"},
      // FOR body with postcond SET on flag
      {"S X=0 S D=0 F  Q:D  S:X=3 D=1  S:D=0 X=X+1", "W X,!"},
    };
    String[] wpcExp = {"12", "3"};
    r = runTestsMulti("while-pc", wpcProgs, wpcExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: Recursion (RecFalseIsNoop, RecHeadRoundTrip,
    //   RecHeadIdentity, RecStackGrowsAndUnwinds, RecNestedTwoLevels)
    //   Recursive DO with global accumulator.
    // ================================================================
    String[][] recProgs2 = {
      {"K ^A S ^A=0 D SUM(3) W ^A,!", "SUM(%N) I %N'<1 S ^A=^A+%N D SUM(%N-1) Q"},
      {"K ^A S ^A=1 D FACT(5) W ^A,!", "FACT(%N) I %N'<2 S ^A=^A*%N D FACT(%N-1) Q"},
      {"S X=0 D OUTER W X,!", "OUTER D INNER D INNER Q", "INNER S X=X+5 Q"},
    };
    String[] recExp2 = {"6", "120", "10"};
    r = runTestsMulti("rec2", recProgs2, recExp2);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: Break (BreakTrueQuits, BreakInWhileExits,
    //   BreakContinueBothFalse) — argless FOR with Q:guard + S:cond flag
    // ================================================================
    String[][] breakProgs = {
      // break at J=5: sum 1+2+3+4 = 10
      {"S X=0 S BD=0 S J=0 F  Q:BD  S J=J+1  S:J=5 BD=1  S:BD'=1 X=X+J", "W X,!"},
      // break on first iteration: body runs once then exits
      {"S X=0 S BD=0 F  Q:BD  S BD=1  S X=99", "W X,!"},
    };
    String[] breakExp = {"10", "99"};
    r = runTestsMulti("break", breakProgs, breakExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: Continue (ContinueTrueJumps, ContinueInWhileSkips)
    //   S:cond SET to skip iteration.
    // ================================================================
    String[][] contProgs = {
      // skip J=3: sum 1+2+4+5+6 = 18 (J goes to 6 before guard fires)
      {"S X=0 S J=0 F  Q:J>5  S J=J+1  S:J'=3 X=X+J", "W X,!"},
    };
    String[] contExp = {"18"};
    r = runTestsMulti("cont", contProgs, contExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: IfThenDoSeq / PostcondSeq (IfThenDoSeqComposition,
    //   PostcondSeqEmptyIsNoop)
    // ================================================================
    String[][] ifseqProgs = {
      {"S X=0", "I 1 D SETX", "W X,!", "SETX S X=42 Q"},
      {"S X=0", "I 0 D SETX", "W X,!", "SETX S X=42 Q"},
      {"S X=1", "I 1", "W X,!"},
      {"S X=1", "I 0", "W X,!"},
    };
    String[] ifseqExp = {"42", "0", "1", "1"};
    r = runTestsMulti("ifseq", ifseqProgs, ifseqExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: WhilePostcondSeq / WhilePostcondAssign
    // ================================================================
    String[][] wpc2Progs = {
      {"S X=0 S N=0 F  Q:N>3  D", " . S N=N+1", " . S:N#2=0 X=X+N", "W X,!"},
      {"S X=0 F J=1:1:5 S:J>2 X=X+J", "W X,!"},
    };
    String[] wpc2Exp = {"6", "12"};
    r = runTestsMulti("while2", wpc2Progs, wpc2Exp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: WriteNewlineAdvances — WRITE ! advances output line
    // ================================================================
    String[] wnaT = {"W \"a\",!,\"b\",!"};
    String[] wnaExp = {"a\nb"};
    r = runTests("wna", wnaT, wnaExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: ArglessDoPushes — D with dotted block pushes return frame
    // ================================================================
    String[][] adoProgs = {
      {"S X=1 D", " . S X=2", "W X,!"},
      {"S X=0 D", " . S X=X+5", " . S X=X+5", "W X,!"},
    };
    String[] adoExp = {"2", "10"};
    r = runTestsMulti("ado", adoProgs, adoExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: QuitTopLevelIsNoop — Q at top level doesn't error
    // ================================================================
    String[][] qtnProgs = {
      {"S X=1 Q"}, {"S X=1 W X,! Q W \"after\",!"},
    };
    String[] qtnExp = {"", "1"};
    r = runTestsMulti("qtn", qtnProgs, qtnExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: ReturnFalseIsNoop — I 0 Q doesn't return
    // ================================================================
    String[][] rfnProgs = {
      {"S X=1", "I 0 Q", "W X,!"},
      {"S X=1", "I 1 Q", "W X,!"},
    };
    String[] rfnExp = {"1", ""};
    r = runTestsMulti("rfn", rfnProgs, rfnExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: TrapPrefersEtrap, RaiseTrueFires, RaiseWithTrap,
    //   RaiseFalseIsNoop, RaiseReturnBothFalse
    //   $ETRAP fires on error (undefined variable), not on success.
    // ================================================================
    String[][] trapProgs = {
      // RaiseTrueFires + RaiseWithTrap + TrapPrefersEtrap: error fires $ETRAP
      {"S $ETRAP=\"S X=99\" W UNDEFVAR W X,!"},
      // RaiseFalseIsNoop + RaiseReturnBothFalse: no error, no trap
      {"S $ETRAP=\"S X=99\" S X=1 W X,!"},
    };
    String[] trapExp = {"99", "1"};
    r = runTestsMulti("trap", trapProgs, trapExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: WhileDoSeqComposition — FOR with DO in body
    // ================================================================
    String[][] wdsProgs = {
      {"S X=0 S J=0 F  Q:J>3  D", " . S J=J+1", " . D ADDJ", "W X,!", "ADDJ S X=X+J Q"},
    };
    String[] wdsExp = {"10"};
    r = runTestsMulti("wds", wdsProgs, wdsExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MControl: DoAssignFalseSkips / DoAssignTrueCalls
    // ================================================================
    String[][] daProgs = {
      {"S X=0", "D:1 SETX", "W X,!", "SETX S X=42 Q"},
      {"S X=0", "D:0 SETX", "W X,!", "SETX S X=42 Q"},
    };
    String[] daExp = {"42", "0"};
    r = runTestsMulti("da", daProgs, daExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: CollNumBeforeString / CollStringAfterNum — $ORDER
    //   collation (numeric subscripts before string subscripts)
    // ================================================================
    String[][] cnsProgs = {
      {"K ^G S ^G(1)=1,^G(\"a\")=1 S K=\"\" W $O(^G(K)),!"},
      {"K ^G S ^G(1)=1,^G(\"a\")=1 S K=\"\" S K=$O(^G(K)) W K,!"},
      {"K ^G S ^G(1)=1,^G(\"a\")=1 S K=\"\" S K=$O(^G(K)) S K=$O(^G(K)) W K,!"},
      {"K ^G S ^G(1)=1,^G(2)=1,^G(\"a\")=1 S K=\"\" W $O(^G(K)),!"},
    };
    String[] cnsExp = {"1", "1", "a", "1"};
    r = runTestsMulti("coll-ns", cnsProgs, cnsExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: SiblingsNonEmpty — $ORDER returns non-empty siblings
    // ================================================================
    String[][] sibsProgs = {
      {"K ^ST S ^ST(1)=1,^ST(2)=1,^ST(3)=1 S K=\"\" S K=$O(^ST(K)) W K,!"},
      {"K ^ST S ^ST(1)=1,^ST(2)=1,^ST(3)=1 S K=\"\" S K=$O(^ST(K)) S K=$O(^ST(K)) W K,!"},
      {"K ^ST S ^ST(\"a\")=1,^ST(\"b\")=1 S K=\"\" S K=$O(^ST(K)) S K=$O(^ST(K)) W K,!"},
    };
    String[] sibsExp = {"1", "2", "b"};
    r = runTestsMulti("sibs", sibsProgs, sibsExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MSemantics: KeyordIrreflexive / KeyordTotal — $ORDER key ordering
    // ================================================================
    String[][] keyProgs = {
      {"K ^KT S ^KT(\"a\")=1,^KT(\"b\")=1 S K=\"a\" W $O(^KT(K)),!"},
      {"K ^KT S ^KT(1)=1,^KT(2)=1 S K=1 W $O(^KT(K)),!"},
      {"K ^KT S ^KT(\"x\")=1,^KT(\"y\")=1 S K=$O(^KT(\"\")) W K,!"},
    };
    String[] keyExp = {"b", "2", "x"};
    r = runTestsMulti("keyord", keyProgs, keyExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MPattern: CharPairN / PairAltMatches — pairAlt pattern
    //   0-255 digits followed by one comma (VistA FileMan pattern)
    //   M pattern: ?.255N1","
    // ================================================================
    String[] pairT = {
      "I \"123,\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \",\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \"123\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \"abc,\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \"\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \"999,\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
      "I \"1,2,\"?.255N1\",\" W \"m=1\",! E  W \"m=0\",!",
    };
    String[] pairExp = {"m=1", "m=1", "m=0", "m=0", "m=0", "m=1", "m=0"};
    r = runTests("pairalt", pairT, pairExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // MPattern: CharPairN — N class matches digits
    // ================================================================
    String[] charNT = {
      "I \"5\"?1N W \"d=1\",! E  W \"d=0\",!",
      "I \"a\"?1N W \"d=1\",! E  W \"d=0\",!",
      "I \"0\"?1N W \"d=1\",! E  W \"d=0\",!",
    };
    String[] charNExp = {"d=1", "d=0", "d=1"};
    r = runTests("charn", charNT, charNExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Summary
    // ================================================================
    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(
        fail == 0 ? "  RESULT: Dafny boundary smoke OK" : "  RESULT: Dafny boundary smoke FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  static int[] runTests(String name, String[] tests, String[] expected) {
    int pass = 0, fail = 0;
    for (int i = 0; i < tests.length; i++) {
      try {
        var rt = new mjvm.runtime.MRuntime(new MStore());
        var fe = new mjvm.compiler.MFrontend();
        var prog = fe.compileProgram(tests[i] + "\n");
        mjvm.runtime.IrMachine.run(prog, rt, fe.lastTags());
        String got = rt.output().strip();
        String want = expected[i];
        if (want.equals(got)) pass++;
        else {
          fail++;
          System.out.printf(
              "  %s[%d] FAIL exp=\"%s\" got=\"%s\"  code=%s%n", name, i, want, got, tests[i]);
        }
      } catch (Exception e) {
        fail++;
        System.out.printf("  %s[%d] EXC %s  code=%s%n", name, i, e.getMessage(), tests[i]);
      }
    }
    return new int[] {pass, fail};
  }

  static int[] runTestsMulti(String name, String[][] progs, String[] expected) {
    int pass = 0, fail = 0;
    for (int i = 0; i < progs.length; i++) {
      try {
        var rt = new mjvm.runtime.MRuntime(new MStore());
        var fe = new mjvm.compiler.MFrontend();
        var prog = fe.compileProgram(String.join("\n", progs[i]) + "\n");
        mjvm.runtime.IrMachine.run(prog, rt, fe.lastTags());
        String got = rt.output().strip();
        String want = expected[i];
        if (want.equals(got)) pass++;
        else {
          fail++;
          System.out.printf(
              "  %s[%d] FAIL exp=\"%s\" got=\"%s\"  code=%s%n",
              name, i, want, got, String.join(" | ", progs[i]));
        }
      } catch (Exception e) {
        fail++;
        System.out.printf(
            "  %s[%d] EXC %s  code=%s%n", name, i, e.getMessage(), String.join(" | ", progs[i]));
      }
    }
    return new int[] {pass, fail};
  }

  private DafnyBoundarySmoke() {}
}
