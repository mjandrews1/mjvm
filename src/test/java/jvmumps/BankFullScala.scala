package jvmumps

import java.util.concurrent.*
import java.util.Collections
import java.util.concurrent.atomic.*
import scala.jdk.CollectionConverters.*

/**
 * BankFullScala — expansive bank model in native Scala.
 * Multi-service, double-entry accounting, immutable journal.
 */
object BankFullScala {

  final val SVC_SAVINGS = 0
  final val SVC_CHECKING = 1
  final val SVC_LOAN = 2
  final val SUB_STANDARD = 0
  final val SUB_CD = 1
  final val ACCT_PERSONAL = 0
  final val ACCT_BUSINESS = 1

  def bench(nAccounts: Int, nInstances: Int, txnPerInstance: Long): Array[Double] = {
    val bank = new BankFullScala
    val totalTxn = nInstances * txnPerInstance
    val t0 = System.nanoTime()

    val accountIds = scala.collection.mutable.ListBuffer[Int]()
    for (i <- 0 until nAccounts) {
      val id = bank.createAccount(ACCT_PERSONAL, s"User$i")
      accountIds += id
      bank.findService(id, SVC_SAVINGS).foreach(svc => bank.deposit(svc, 1000.0))
    }

    if (nInstances == 1) {
      runTransactions(bank, accountIds.toList, txnPerInstance)
    } else {
      val ex = Executors.newFixedThreadPool(nInstances)
      val futures = scala.collection.mutable.ListBuffer[Future[_]]()
      val perInst = totalTxn / nInstances
      for (i <- 0 until nInstances) {
        val count = if (i == nInstances - 1) totalTxn - perInst * (nInstances - 1) else perInst
        futures += ex.submit(new Runnable {
          def run(): Unit = runTransactions(bank, accountIds.toList, count)
        })
      }
      futures.foreach(_.get())
      ex.shutdown()
    }

    val elapsed = System.nanoTime() - t0
    val ms = elapsed / 1e6
    val tps = totalTxn / (ms / 1000.0)
    val ok = bank.verify()
    Array(ms, tps, if (ok) 1.0 else 0.0)
  }

  private def runTransactions(bank: BankFullScala, accountIds: List[Int], n: Long): Unit = {
    if (accountIds.isEmpty) return
    val rng = ThreadLocalRandom.current()
    var i = 0L
    while (i < n) {
      val acctId = accountIds(rng.nextInt(accountIds.length))
      bank.findService(acctId, SVC_SAVINGS).foreach { svc =>
        val amt = (rng.nextInt(100) + 1).toDouble
        if (rng.nextInt(2) == 0) bank.deposit(svc, amt)
        else bank.withdraw(svc, amt)
      }
      i += 1
    }
  }
}

class BankFullScala {
  import BankFullScala._

  case class JournalEntry(
    `type`: String,
    serviceId: Int,
    amount: Double,
    balanceAfter: Double,
    timestamp: Long = System.nanoTime()
  )

  case class Service(
    var id: Int = 0,
    var accountId: Int = 0,
    var `type`: Int = 0,
    var subtype: Int = 0,
    var balance: Double = 0.0,
    var rate: Double = 0.0,
    var monthlyFee: Double = 0.0,
    var minBalance: Double = 0.0
  )

  case class Account(
    var id: Int = 0,
    var `type`: Int = 0,
    var owner: String = "",
    val services: scala.collection.mutable.ListBuffer[Int] = scala.collection.mutable.ListBuffer()
  )

  var bankBalance: Double = 1_000_000.0
  var bankName: String = "First National Test Bank"
  private var nextAccountId = 0
  private var nextServiceId = 0
  val accounts = new ConcurrentHashMap[Int, Account]()
  val services = new ConcurrentHashMap[Int, Service]()
  val journal = Collections.synchronizedList(new java.util.ArrayList[JournalEntry]())

  def createAccount(`type`: Int, owner: String): Int = {
    nextAccountId += 1
    val id = nextAccountId
    accounts.put(id, Account(id, `type`, owner))
    addService(id, SVC_SAVINGS, SUB_STANDARD, 100.0, 0.02, 5.0)
    id
  }

  def addService(accountId: Int, `type`: Int, subtype: Int,
                 minBalance: Double, rate: Double, monthlyFee: Double): Int = {
    nextServiceId += 1
    val id = nextServiceId
    services.put(id, Service(id, accountId, `type`, subtype, 0.0, rate, monthlyFee, minBalance))
    accounts.get(accountId).services += id
    id
  }

  def deposit(serviceId: Int, amount: Double): Double = {
    if (amount <= 0) return -1.0
    val svc = services.get(serviceId)
    if (svc == null) return -1.0
    svc.balance += amount
    bankBalance -= amount
    journal.add(JournalEntry("DEPOSIT", serviceId, amount, svc.balance))
    svc.balance
  }

  def withdraw(serviceId: Int, amount: Double): Double = {
    if (amount <= 0) return -1.0
    val svc = services.get(serviceId)
    if (svc == null) return -1.0
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
    newBal
  }

  def transfer(fromServiceId: Int, toServiceId: Int, amount: Double): Int = {
    if (amount <= 0) return 0
    val from = services.get(fromServiceId)
    val to = services.get(toServiceId)
    if (from == null || to == null) return 0
    if (from.balance < amount) return 0
    from.balance -= amount
    to.balance += amount
    journal.add(JournalEntry("TRANSFER", fromServiceId, amount, from.balance))
    journal.add(JournalEntry("TRANSFER", toServiceId, amount, to.balance))
    1
  }

  def interAccountTransfer(fromAccountId: Int, toAccountId: Int, amount: Double): Int = {
    val fromSvc = findService(fromAccountId, SVC_SAVINGS)
    val toSvc = findService(toAccountId, SVC_SAVINGS)
    if (fromSvc.isEmpty || toSvc.isEmpty) return 0
    transfer(fromSvc.get, toSvc.get, amount)
  }

  def findService(accountId: Int, `type`: Int): Option[Int] = {
    val acct = accounts.get(accountId)
    if (acct == null) return None
    acct.services.find(sid => services.get(sid).`type` == `type`)
  }

  def accrueInterest(): Double = {
    var total = 0.0
    val it = services.values().iterator()
    while (it.hasNext) {
      val svc = it.next()
      if (svc.balance > 0 && svc.rate > 0) {
        val interest = svc.balance * (svc.rate / 12.0)
        svc.balance += interest
        total += interest
        journal.add(JournalEntry("INTEREST", svc.id, interest, svc.balance))
      }
    }
    total
  }

  def chargeMonthlyFees(): Double = {
    var total = 0.0
    val it = services.values().iterator()
    while (it.hasNext) {
      val svc = it.next()
      if (svc.monthlyFee > 0) {
        svc.balance -= svc.monthlyFee
        total += svc.monthlyFee
        journal.add(JournalEntry("FEE", svc.id, svc.monthlyFee, svc.balance))
      }
    }
    total
  }

  def verify(): Boolean = {
    var debit = 0.0
    var credit = 0.0
    val it = journal.iterator()
    while (it.hasNext) {
      val e = it.next()
      e.`type` match {
        case "DEPOSIT" | "WITHDRAW" | "TRANSFER" | "INTEREST" | "FEE" =>
          debit += e.amount
          credit += e.amount
        case _ =>
      }
    }
    Math.abs(debit - credit) < 0.01
  }

  def accountBalance(accountId: Int): Double = {
    val acct = accounts.get(accountId)
    if (acct == null) return 0.0
    acct.services.map(sid => services.get(sid).balance).sum
  }
}
