/**
 * BankBenchmark.kt — Native Kotlin bank benchmark (T83).
 * Double-entry accounting: B(acct)-C(acct)=2*FEE(acct).
 */
import java.util.Random

val ACCOUNTS = 10000
val checking = DoubleArray(ACCOUNTS)
val bank = DoubleArray(ACCOUNTS)
val fees = DoubleArray(ACCOUNTS)
var txnCount = 0L
var rng = Random(42)

fun deposit(acct: Int, amt: Double) {
    checking[acct] += amt
    bank[acct] += amt
    txnCount++
}

fun withdraw(acct: Int, amt: Double) {
    val c = checking[acct]
    var n = c - amt
    var fee = 0.0
    if (n < 0) { fee = 3.0; n -= 3.0 }
    if (n < -100) return
    checking[acct] = n
    bank[acct] = bank[acct] - amt + fee
    if (fee > 0) fees[acct] += fee
    txnCount++
}

fun runBatch(n: Int) {
    for (i in 0 until n) {
        val acct = rng.nextInt(ACCOUNTS)
        val amt = (rng.nextInt(100) + 1).toDouble()
        if (rng.nextInt(2) == 0) deposit(acct, amt) else withdraw(acct, amt)
    }
}

fun checkInvariant(): Boolean {
    for (i in 0 until ACCOUNTS) {
        if (Math.abs(bank[i] - checking[i] - 2 * fees[i]) > 1e-9) return false
    }
    return true
}

fun main(args: Array<String>) {
    val maxScale = if (args.isNotEmpty()) args[0].toLong() else 10_000_000L
    val scales = longArrayOf(1000, 10000, 100000, 1000000, 10000000)

    println("=== Bank Native Benchmark: Kotlin ===")
    println("accounts=$ACCOUNTS  maxScale=$maxScale")
    println()
    println("scale        batch_ms    txn/ms    total_ms    invariant")
    println("-----------  --------    ------    --------    ----------")

    for (n in scales) {
        if (n > maxScale) break
        checking.fill(0.0); bank.fill(0.0); fees.fill(0.0)
        txnCount = 0; rng = Random(42)

        runBatch(1000) // warmup

        checking.fill(0.0); bank.fill(0.0); fees.fill(0.0)
        txnCount = 0; rng = Random(42)

        val batchSize = 100000
        val batches = maxOf(1L, n / batchSize)
        val perBatch = (n / batches).toInt()

        var totalNs = 0L
        for (b in 0 until batches) {
            val t0 = System.nanoTime()
            runBatch(perBatch)
            totalNs += System.nanoTime() - t0
        }

        val totalMs = totalNs / 1e6
        val batchMs = totalMs / batches
        val txnPerMs = txnCount / (totalMs / 1000.0)
        val inv = checkInvariant()

        println("%,11d  %8.1f    %6.1f    %8.1f    %s".format(
            txnCount, batchMs, txnPerMs, totalMs, if (inv) "OK" else "FAIL"))
    }
    println()
    println("DONE")
}
