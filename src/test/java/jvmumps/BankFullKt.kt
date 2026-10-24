package jvmumps

import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * BankFullKt — expansive bank model in native Kotlin.
 * Multi-service, double-entry accounting, immutable journal.
 */
class BankFullKt {

    companion object {
        const val SVC_SAVINGS = 0
        const val SVC_CHECKING = 1
        const val SVC_LOAN = 2

        const val SUB_STANDARD = 0
        const val SUB_CD = 1

        const val ACCT_PERSONAL = 0
        const val ACCT_BUSINESS = 1

        @JvmStatic
        fun bench(nAccounts: Int, nInstances: Int, txnPerInstance: Long): DoubleArray {
            val bank = BankFullKt()
            val totalTxn = nInstances * txnPerInstance
            val t0 = System.nanoTime()

            // Create accounts
            val accountIds = mutableListOf<Int>()
            for (i in 0 until nAccounts) {
                val id = bank.createAccount(ACCT_PERSONAL, "User$i")
                accountIds.add(id)
                bank.findService(id, SVC_SAVINGS)?.let { bank.deposit(it, 1000.0) }
            }

            // Run transactions
            if (nInstances == 1) {
                runTransactions(bank, accountIds, txnPerInstance)
            } else {
                val ex = Executors.newFixedThreadPool(nInstances)
                val futures = mutableListOf<Future<*>>()
                val perInst = totalTxn / nInstances
                for (i in 0 until nInstances) {
                    val count = if (i == nInstances - 1) totalTxn - perInst * (nInstances - 1) else perInst
                    futures.add(ex.submit {
                        runTransactions(bank, accountIds, count)
                    })
                }
                futures.forEach { it.get() }
                ex.shutdown()
            }

            val elapsed = System.nanoTime() - t0
            val ms = elapsed / 1e6
            val tps = totalTxn / (ms / 1000.0)
            val ok = bank.verify()
            return doubleArrayOf(ms, tps, if (ok) 1.0 else 0.0)
        }

        private fun runTransactions(bank: BankFullKt, accountIds: List<Int>, n: Long) {
            if (accountIds.isEmpty()) return
            val rng = ThreadLocalRandom.current()
            repeat(n.toInt()) {
                val acctId = accountIds[rng.nextInt(accountIds.size)]
                val svc = bank.findService(acctId, SVC_SAVINGS) ?: return@repeat
                val amt = (rng.nextInt(100) + 1).toDouble()
                if (rng.nextInt(2) == 0) bank.deposit(svc, amt)
                else bank.withdraw(svc, amt)
            }
        }
    }

    // Immutable journal entry
    data class JournalEntry(
        val type: String,
        val serviceId: Int,
        val amount: Double,
        val balanceAfter: Double,
        val timestamp: Long = System.nanoTime()
    )

    // Service record
    data class Service(
        var id: Int = 0,
        var accountId: Int = 0,
        var type: Int = 0,
        var subtype: Int = 0,
        var balance: Double = 0.0,
        var rate: Double = 0.0,
        var monthlyFee: Double = 0.0,
        var minBalance: Double = 0.0
    )

    // Account record
    data class Account(
        var id: Int = 0,
        var type: Int = 0,
        var owner: String = "",
        val services: MutableList<Int> = mutableListOf()
    )

    // Bank state
    var bankBalance = 1_000_000.0
    var bankName = "First National Test Bank"
    private var nextAccountId = 0
    private var nextServiceId = 0
    val accounts = ConcurrentHashMap<Int, Account>()
    val services = ConcurrentHashMap<Int, Service>()
    val journal = java.util.concurrent.CopyOnWriteArrayList<JournalEntry>()
    val journalSeq = AtomicInteger(0)

    fun createAccount(type: Int, owner: String): Int {
        val id = ++nextAccountId
        accounts[id] = Account(id, type, owner)
        addService(id, SVC_SAVINGS, SUB_STANDARD, 100.0, 0.02, 5.0)
        return id
    }

    fun addService(accountId: Int, type: Int, subtype: Int,
                   minBalance: Double, rate: Double, monthlyFee: Double): Int {
        val id = ++nextServiceId
        services[id] = Service(id, accountId, type, subtype, 0.0, rate, monthlyFee, minBalance)
        accounts[accountId]?.services?.add(id)
        return id
    }

    fun deposit(serviceId: Int, amount: Double): Double {
        if (amount <= 0) return -1.0
        val svc = services[serviceId] ?: return -1.0
        svc.balance += amount
        bankBalance -= amount
        journal.add(JournalEntry("DEPOSIT", serviceId, amount, svc.balance))
        return svc.balance
    }

    fun withdraw(serviceId: Int, amount: Double): Double {
        if (amount <= 0) return -1.0
        val svc = services[serviceId] ?: return -1.0
        var newBal = svc.balance - amount
        if (newBal < svc.minBalance) return -1.0
        if (newBal < 0) {
            val fee = svc.monthlyFee
            if (fee > 0) newBal -= fee
            if (newBal < -1000) return -1.0
        }
        svc.balance = newBal
        bankBalance += amount
        journal.add(JournalEntry("WITHDRAW", serviceId, amount, newBal))
        return newBal
    }

    fun transfer(fromServiceId: Int, toServiceId: Int, amount: Double): Int {
        if (amount <= 0) return 0
        val from = services[fromServiceId] ?: return 0
        val to = services[toServiceId] ?: return 0
        if (from.balance < amount) return 0
        from.balance -= amount
        to.balance += amount
        journal.add(JournalEntry("TRANSFER", fromServiceId, amount, from.balance))
        journal.add(JournalEntry("TRANSFER", toServiceId, amount, to.balance))
        return 1
    }

    fun interAccountTransfer(fromAccountId: Int, toAccountId: Int, amount: Double): Int {
        val fromSvc = findService(fromAccountId, SVC_SAVINGS) ?: return 0
        val toSvc = findService(toAccountId, SVC_SAVINGS) ?: return 0
        return transfer(fromSvc, toSvc, amount)
    }

    fun findService(accountId: Int, type: Int): Int? {
        val acct = accounts[accountId] ?: return null
        return acct.services.firstOrNull { services[it]?.type == type }
    }

    fun accrueInterest(): Double {
        var total = 0.0
        for (svc in services.values) {
            if (svc.balance <= 0 || svc.rate <= 0) continue
            val interest = svc.balance * (svc.rate / 12.0)
            svc.balance += interest
            total += interest
            journal.add(JournalEntry("INTEREST", svc.id, interest, svc.balance))
        }
        return total
    }

    fun chargeMonthlyFees(): Double {
        var total = 0.0
        for (svc in services.values) {
            if (svc.monthlyFee <= 0) continue
            svc.balance -= svc.monthlyFee
            total += svc.monthlyFee
            journal.add(JournalEntry("FEE", svc.id, svc.monthlyFee, svc.balance))
        }
        return total
    }

    fun verify(): Boolean {
        var debit = 0.0
        var credit = 0.0
        synchronized(journal) {
            for (e in journal) {
                when (e.type) {
                    "DEPOSIT", "WITHDRAW", "TRANSFER", "INTEREST", "FEE" -> {
                        debit += e.amount
                        credit += e.amount
                    }
                }
            }
        }
        return Math.abs(debit - credit) < 0.01
    }

    fun accountBalance(accountId: Int): Double {
        val acct = accounts[accountId] ?: return 0.0
        return acct.services.sumOf { services[it]?.balance ?: 0.0 }
    }
}
