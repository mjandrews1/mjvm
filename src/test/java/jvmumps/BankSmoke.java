package jvmumps;

import java.nio.file.*;

/**
 * Bank simulator smoke (T81) — concrete boundary cases for the double-entry bank with overdraft
 * protection. Covers rules 5–11 from BANK.m.
 *
 * <p>Fee boundary rules tested: Rule 7: when negative, only deposits allowed Rule 8: overdraft
 * floor at exactly -100 (denied past it) Rule 9: every advance charges exactly $3
 *
 * <p>Usage: java -cp build:lib/... jvmumps.BankSmoke [lib_dir]
 */
public final class BankSmoke {

  @SuppressWarnings("RedundantThrows")


  static String libDir()  {
    String env = System.getenv("DS_LIB");
    return env != null && !env.isEmpty() ? env : "src/main/m/lib";
  }

  public static void main(String[] args) throws Exception {
    String dir = args.length > 0 ? args[0] : libDir();
    Path bankPath = Path.of(dir, "BANK.m");
    if (!Files.isReadable(bankPath)) {
      System.out.println("## Bank smoke: SKIP (no BANK.m in " + dir + ")");
      return;
    }
    System.out.println("## Bank smoke — fee boundary + double-entry");
    System.out.println("### lib=" + dir);
    System.out.println();

    String bank = Files.readString(bankPath);
    int pass = 0, fail = 0;
    int[] r;

    // ================================================================
    // Rule 5: basic deposits and withdrawals
    // ================================================================
    String[] basic = {
      "D INIT^BANK W $$DEP^BANK(100),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(30),!",
      "D INIT^BANK D DEP^BANK(100) W $$BAL^BANK(),!",
      "D INIT^BANK D DEP^BANK(50) D DEP^BANK(25) W $$BAL^BANK(),!",
    };
    String[] basicExp = {"100", "70", "100", "75"};
    r = runTests("basic", bank, basic, basicExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Invalid amounts: zero and negative rejected
    // ================================================================
    String[] invalid = {
      "D INIT^BANK W $$DEP^BANK(0),!",
      "D INIT^BANK W $$DEP^BANK(-5),!",
      "D INIT^BANK W $$WDR^BANK(0),!",
      "D INIT^BANK W $$WDR^BANK(-5),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(0),!",
    };
    String[] invalidExp = {"-1", "-1", "-1", "-1", "-1"};
    r = runTests("invalid", bank, invalid, invalidExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 8 boundary: overdraft floor at exactly -100
    //
    //   WDR(97) from 0:  0-97=-97, fee 3 → -100  (exactly at floor)
    //   WDR(98) from 0:  0-98=-98, fee 3 → -101  (past floor, denied)
    // ================================================================
    String[] floor = {
      "D INIT^BANK W $$WDR^BANK(97),!",
      "D INIT^BANK W $$WDR^BANK(98),!",
      "D INIT^BANK W $$WDR^BANK(99),!",
      "D INIT^BANK W $$WDR^BANK(100),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(197),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(198),!",
      "D INIT^BANK D DEP^BANK(50) W $$WDR^BANK(147),!",
      "D INIT^BANK D DEP^BANK(50) W $$WDR^BANK(148),!",
      "D INIT^BANK D DEP^BANK(200) W $$WDR^BANK(297),!",
      "D INIT^BANK D DEP^BANK(200) W $$WDR^BANK(298),!",
    };
    String[] floorExp = {
      "-100", "-1", "-1", "-1", "-100", "-1", "-100", "-1", "-100", "-1",
    };
    r = runTests("floor", bank, floor, floorExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 9: fee is exactly $3 per advance
    // ================================================================
    String[] fee = {
      // no fee: withdrawal stays >= 0
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(100),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(99),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(1),!",
      // first overdraft: fee = 3
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(101),!",
      "D INIT^BANK D DEP^BANK(100) W $$WDR^BANK(150),!",
      // check FEE and ADV via $P on STAT (cut at space)
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) W $P($P($$STAT^BANK(),\"FEE=\",2),\" \",1),!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) W $P($P($$STAT^BANK(),\"ADV=\",2),\" \",1),!",
    };
    String[] feeExp = {
      "0", "1", "99", "-4", "-53", "3", // FEE=3 after one advance
      "1", // ADV=1 after one advance
    };
    r = runTests("fee", bank, fee, feeExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 7: when negative, only deposits allowed
    //
    //   Use single W with comma-separated extrinsic results to avoid
    //   newline issues from multiple WRITE commands.
    // ================================================================
    String[] rule7 = {
      // withdrawal while negative → denied (-1)
      "D INIT^BANK S A=$$WDR^BANK(50) W A,\" \",$$WDR^BANK(1),!",
      // deposit while negative → works
      "D INIT^BANK S A=$$WDR^BANK(50) W A,\" \",$$DEP^BANK(60),!",
      // deposit back to positive, then withdrawal works (with fee)
      "D INIT^BANK S A=$$WDR^BANK(50) S B=$$DEP^BANK(60) W A,\" \",B,\" \",$$WDR^BANK(10),!",
    };
    String[] rule7Exp = {
      "-53 -1", "-53 7", "-53 7 -6",
    };
    r = runTests("rule7", bank, rule7, rule7Exp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 9: multiple advances accumulate fees
    // ================================================================
    String[] multi = {
      // two advances: fee = 6, ADV = 2
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) D WDR^BANK(20) W"
          + " $P($P($$STAT^BANK(),\"FEE=\",2),\" \",1),!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) D WDR^BANK(20) W"
          + " $P($P($$STAT^BANK(),\"ADV=\",2),\" \",1),!",
      // three advances: fee = 9
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) D WDR^BANK(20) D DEP^BANK(30) D"
          + " WDR^BANK(50) W $P($P($$STAT^BANK(),\"FEE=\",2),\" \",1),!",
    };
    String[] multiExp = {"6", "2", "9"};
    r = runTests("multi-advance", bank, multi, multiExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 10: double-entry — BANK - CHECK = 2 * FEE
    //
    //   DEP(100): B=100 C=100 FEE=0  → 100-100=0=2*0
    //   WDR(101): B=2 C=-4 FEE=3    → 2-(-4)=6=2*3
    // ================================================================
    String[] dbl = {
      "D INIT^BANK D DEP^BANK(100) S X=$P($P($$STAT^BANK(),\"BANK=\",2),\" \",1) S"
          + " Y=$P($P($$STAT^BANK(),\"CHECK=\",2),\" \",1) S Z=$P($P($$STAT^BANK(),\"FEE=\",2),\""
          + " \",1) W X-Y,\"=\",2*Z,!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) S X=$P($P($$STAT^BANK(),\"BANK=\",2),\" \",1) S"
          + " Y=$P($P($$STAT^BANK(),\"CHECK=\",2),\" \",1) S Z=$P($P($$STAT^BANK(),\"FEE=\",2),\""
          + " \",1) W X-Y,\"=\",2*Z,!",
    };
    String[] dblExp = {"0=0", "6=6"};
    r = runTests("double-entry", bank, dbl, dblExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Rule 11: reconciliation after transactions
    // ================================================================
    String[] recon = {
      "D INIT^BANK W $$RECON^BANK(),!",
      "D INIT^BANK D DEP^BANK(100) W $$RECON^BANK(),!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(50) W $$RECON^BANK(),!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) W $$RECON^BANK(),!",
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) D WDR^BANK(20) W"
          + " $$RECON^BANK(),!",
    };
    String[] reconExp = {"1", "1", "1", "1", "1"};
    r = runTests("recon", bank, recon, reconExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // DepositGrowsJournal: journal grows by exactly 1 per deposit
    // WithdrawGrowsJournal: grows by 1 (no fee) or 2 (with fee)
    // ================================================================
    String[] grow = {
      // 1 deposit → TXN=1
      "D INIT^BANK D DEP^BANK(50) W $P($P($$STAT^BANK(),\"TXN=\",2),\" \",1),!",
      // 2 deposits → TXN=2
      "D INIT^BANK D DEP^BANK(50) D DEP^BANK(30) W $P($P($$STAT^BANK(),\"TXN=\",2),\" \",1),!",
      // 1 deposit + 1 withdrawal (no fee) → TXN=2
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(50) W $P($P($$STAT^BANK(),\"TXN=\",2),\" \",1),!",
      // 1 deposit + 1 withdrawal (with fee) → TXN=3 (W + F)
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) W $P($P($$STAT^BANK(),\"TXN=\",2),\" \",1),!",
      // 2 deposits + 1 withdrawal (no fee) → TXN=3
      "D INIT^BANK D DEP^BANK(50) D DEP^BANK(50) D WDR^BANK(100) W"
          + " $P($P($$STAT^BANK(),\"TXN=\",2),\" \",1),!",
    };
    String[] growExp = {"1", "2", "2", "3", "3"};
    r = runTests("journal-grow", bank, grow, growExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // DepositPreservesFees: deposit does not change fees or advances
    // WithdrawNoOverdraftPreservesFees: no-overdraft WDR doesn't change fees
    // ================================================================
    String[] preserve = {
      // deposit: FEE stays 0, ADV stays 0
      "D INIT^BANK D DEP^BANK(100) W $P($P($$STAT^BANK(),\"FEE=\",2),\""
          + " \",1),\"/\",$P($P($$STAT^BANK(),\"ADV=\",2),\" \",1),!",
      // deposit after advance: FEE stays 3, ADV stays 1
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) W"
          + " $P($P($$STAT^BANK(),\"FEE=\",2),\" \",1),\"/\",$P($P($$STAT^BANK(),\"ADV=\",2),\""
          + " \",1),!",
      // no-overdraft WDR: FEE stays 0, ADV stays 0
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(50) W $P($P($$STAT^BANK(),\"FEE=\",2),\""
          + " \",1),\"/\",$P($P($$STAT^BANK(),\"ADV=\",2),\" \",1),!",
      // no-overdraft WDR after advance: FEE stays 3, ADV stays 1
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(200) D WDR^BANK(50) W"
          + " $P($P($$STAT^BANK(),\"FEE=\",2),\" \",1),\"/\",$P($P($$STAT^BANK(),\"ADV=\",2),\""
          + " \",1),!",
    };
    String[] preserveExp = {"0/0", "3/1", "0/0", "3/1"};
    r = runTests("preserve-fee", bank, preserve, preserveExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // BankIsCounterpart: BANK = CHECKING + 2 * FEE
    // (equivalent to BANK - CHECKING = 2 * FEE, stated additively)
    // ================================================================
    String[] counterpart = {
      // initial: BANK=0 CHECK=0 FEE=0 → 0 = 0 + 0
      "D INIT^BANK S ST=$$STAT^BANK() S X=$P($P(ST,\"BANK=\",2),\" \",1) S"
          + " Y=$P($P(ST,\"CHECK=\",2),\" \",1) S Z=$P($P(ST,\"FEE=\",2),\" \",1) W"
          + " X,\"=\",Y+(2*Z),!",
      // after deposit: BANK=100 CHECK=100 FEE=0 → 100 = 100 + 0
      "D INIT^BANK D DEP^BANK(100) S ST=$$STAT^BANK() S X=$P($P(ST,\"BANK=\",2),\" \",1) S"
          + " Y=$P($P(ST,\"CHECK=\",2),\" \",1) S Z=$P($P(ST,\"FEE=\",2),\" \",1) W"
          + " X,\"=\",Y+(2*Z),!",
      // after overdraft: BANK=2 CHECK=-4 FEE=3 → 2 = -4 + 6
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) S ST=$$STAT^BANK() S X=$P($P(ST,\"BANK=\",2),\""
          + " \",1) S Y=$P($P(ST,\"CHECK=\",2),\" \",1) S Z=$P($P(ST,\"FEE=\",2),\" \",1) W"
          + " X,\"=\",Y+(2*Z),!",
      // after two advances: BANK=-5 CHECK=-17 FEE=6 → -5 = -17 + 12
      "D INIT^BANK D DEP^BANK(100) D WDR^BANK(101) D DEP^BANK(10) D WDR^BANK(20) S ST=$$STAT^BANK()"
          + " S X=$P($P(ST,\"BANK=\",2),\" \",1) S Y=$P($P(ST,\"CHECK=\",2),\" \",1) S"
          + " Z=$P($P(ST,\"FEE=\",2),\" \",1) W X,\"=\",Y+(2*Z),!",
    };
    String[] counterpartExp = {"0=0", "100=100", "2=2", "-5=-5"};
    r = runTests("counterpart", bank, counterpart, counterpartExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Edge values: minimum amounts, exact boundaries
    // ================================================================
    String[] edge = {
      // deposit exactly 1
      "D INIT^BANK W $$DEP^BANK(1),!",
      // withdraw exactly 1 from 1 → 0, no fee
      "D INIT^BANK D DEP^BANK(1) W $$WDR^BANK(1),!",
      // withdraw 1 from 1 → 0 (no fee since not negative)
      "D INIT^BANK D DEP^BANK(1) W $$WDR^BANK(1),\" \",$$BAL^BANK(),!",
      // withdraw 2 from 1 → 1-2-3=-4 (fee)
      "D INIT^BANK D DEP^BANK(1) W $$WDR^BANK(2),!",
      // deposit at exactly -100: allowed
      "D INIT^BANK S A=$$WDR^BANK(97) W A,\" \",$$DEP^BANK(1),!",
      // -100 + 1 = -99
      // deposit at exactly 0
      "D INIT^BANK D DEP^BANK(0) W $$DEP^BANK(5),!",
      // withdraw 0 is rejected
      "D INIT^BANK D DEP^BANK(50) W $$WDR^BANK(0),!",
      // multiple INIT is idempotent
      "D INIT^BANK D DEP^BANK(100) D INIT^BANK W $$BAL^BANK(),\" \",$$RECON^BANK(),!",
      // large amount
      "D INIT^BANK W $$DEP^BANK(999999),!",
    };
    String[] edgeExp = {
      "1", // DEP(1) → 1
      "0", // WDR(1) from 1 → 0
      "0 0", // WDR(1) from 1 → 0, BAL=0
      "-4", // WDR(2) from 1 → 1-2=-1, fee 3 → -4
      "-100 -99", // WDR(97) → -100, DEP(1) → -99
      "5", // DEP(0) rejected (-1), DEP(5) → 5
      "-1", // WDR(0) rejected
      "0 1", // INIT resets: BAL=0, RECON=1
      "999999", // large deposit
    };
    r = runTests("edge", bank, edge, edgeExp);
    pass += r[0];
    fail += r[1];

    // ================================================================
    // Summary
    // ================================================================
    System.out.println();
    System.out.printf("  summary: pass=%d  fail=%d%n", pass, fail);
    System.out.println(fail == 0 ? "  RESULT: Bank smoke OK" : "  RESULT: Bank smoke FAIL");
    System.exit(fail > 0 ? 1 : 0);
  }

  static int[] runTests(String name, String code, String[] tests, String[] expected) {
    int pass = 0, fail = 0;
    for (int i = 0; i < tests.length; i++) {
      try {
        var rt = new mjvm.runtime.MRuntime(new MStore());
        var fe = new mjvm.compiler.MFrontend();
        var libProg = fe.compileProgram(code);
        mjvm.runtime.IrMachine.run(libProg, rt, fe.lastTags());
        rt.reset();
        var testProg = fe.compileProgram(tests[i] + "\n");
        mjvm.runtime.IrMachine.run(testProg, rt, fe.lastTags());
        String got = rt.output().strip();
        String want = expected[i];
        if (want.equals(got)) {
          pass++;
        } else {
          fail++;
          System.out.printf("  %s[%d] FAIL exp=\"%s\" got=\"%s\"%n", name, i, want, got);
        }
      } catch (Exception e) {
        fail++;
        System.out.printf("  %s[%d] EXC %s%n", name, i, e.getMessage());
      }
    }
    return new int[] {pass, fail};
  }

  private BankSmoke() {}
}
