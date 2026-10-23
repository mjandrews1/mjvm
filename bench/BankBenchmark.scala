/**
 * BankBenchmark.scala — Native Scala bank benchmark (T83).
 * Double-entry accounting: B(acct)-C(acct)=2*FEE(acct).
 */
import java.util.Random

val ACCOUNTS = 10000
val checking = new Array[Double](ACCOUNTS)
val bank = new Array[Double](ACCOUNTS)
val fees = new Array[Double](ACCOUNTS)
var txnCount = 0L
var rng = new Random(42)

def deposit(acct: Int, amt: Double): Unit = {
  checking(acct) += amt
  bank(acct) += amt
  txnCount += 1
}

def withdraw(acct: Int, amt: Double): Unit = {
  val c = checking(acct)
  var n = c - amt
  var fee = 0.0
  if (n < 0) { fee = 3.0; n -= 3.0 }
  if (n < -100) return
  checking(acct) = n
  bank(acct) = bank(acct) - amt + fee
  if (fee > 0) fees(acct) += fee
  txnCount += 1
}

def runBatch(n: Int): Unit = {
  var i = 0
  while (i < n) {
    val acct = rng.nextInt(ACCOUNTS)
    val amt = (rng.nextInt(100) + 1).toDouble
    if (rng.nextInt(2) == 0) deposit(acct, amt) else withdraw(acct, amt)
    i += 1
  }
}

def checkInvariant: Boolean = {
  var i = 0
  while (i < ACCOUNTS) {
    if (Math.abs(bank(i) - checking(i) - 2 * fees(i)) > 1e-9) return false
    i += 1
  }
  true
}

@main def bench(maxScaleArg: String): Unit = {
  val maxScale = maxScaleArg.toLong
  val scales = Array(1000L, 10000L, 100000L, 1000000L, 10000000L)

  println("=== Bank Native Benchmark: Scala ===")
  println(s"accounts=$ACCOUNTS  maxScale=$maxScale")
  println()
  println("scale        batch_ms    txn/ms    total_ms    invariant")
  println("-----------  --------    ------    --------    ----------")

  for (n <- scales) {
    if (n <= maxScale) {
      java.util.Arrays.fill(checking, 0.0); java.util.Arrays.fill(bank, 0.0); java.util.Arrays.fill(fees, 0.0)
      txnCount = 0; rng = new Random(42)

      runBatch(1000) // warmup

      java.util.Arrays.fill(checking, 0.0); java.util.Arrays.fill(bank, 0.0); java.util.Arrays.fill(fees, 0.0)
      txnCount = 0; rng = new Random(42)

      val batchSize = 100000L
      val batches = Math.max(1L, n / batchSize)
      val perBatch = (n / batches).toInt

      var totalNs = 0L
      var b = 0L
      while (b < batches) {
        val t0 = System.nanoTime()
        runBatch(perBatch)
        totalNs += System.nanoTime() - t0
        b += 1
      }

      val totalMs = totalNs / 1e6
      val batchMs = totalMs / batches
      val txnPerMs = txnCount / (totalMs / 1000.0)
      val inv = checkInvariant

      println(f"$txnCount%11d  $batchMs%8.1f    $txnPerMs%6.1f    $totalMs%8.1f    ${if (inv) "OK" else "FAIL"}")
    }
  }
  println()
  println("DONE")
}
