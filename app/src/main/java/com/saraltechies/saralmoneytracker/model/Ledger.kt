package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate

/** Someone who buys from you on credit (a customer) or sells to you on credit (a supplier). */
enum class PartyRole { CUSTOMER, SUPPLIER }

/**
 * A customer's or supplier's running account (a khata). Like a loan, each party has its own hidden account of type
 * [AccountType.PARTY]: its balance is what they owe you (positive) or what you owe them (negative).
 *
 * - Goods sold on credit are a sale (income) recorded in the party's account, so profit counts it once.
 * - Their payment is a transfer from the party's account into yours, so it isn't counted as income again.
 * - Goods bought on credit are a purchase (expense) in the party's account; paying them is a transfer back.
 */
data class Party(
    val id: Long = 0,
    val accountId: Long,
    val contactId: Long?,
    val role: PartyRole,
    val bookId: Long = Books.PERSONAL_ID,
    val createdAt: Long = 0,
    /** Days of credit: each sale (or purchase) on credit is due this many days later; 0 for no due dates. */
    val creditDays: Int = 0,
)

enum class LedgerKind {
    /** You sold goods or services on credit (a sale). */
    GAVE_GOODS,
    /** You gave money: paid a supplier, or lent cash to a customer. */
    GAVE_MONEY,
    /** You bought goods or services on credit (a purchase). */
    GOT_GOODS,
    /** You got money: a customer paid you, or a supplier advanced cash. */
    GOT_MONEY,
    /** What was owed before you started using the app. */
    OPENING,
}

/** One line of a party's account. [change] is how it moved the balance: plus means they owe you more. */
data class LedgerEntry(
    val date: LocalDate,
    val kind: LedgerKind,
    val change: Long,
    val balanceAfter: Long,
    val note: String,
    val txnId: Long?,
    val categoryId: Long?,
    /** Your account the money came from or went to; null for goods and the opening balance. */
    val accountId: Long?,
) {
    val amount: Long get() = kotlin.math.abs(change)
    val gave: Boolean get() = change > 0
}

object Ledgers {

    /** A party's entries, oldest first, each with the balance after it. */
    fun entries(party: Party, opening: Long, txns: List<Txn>): List<LedgerEntry> {
        val raw = mutableListOf<Triple<Txn?, LedgerKind, Long>>()
        for (t in txns) {
            val (kind, change) = when {
                t.type == TxnType.INCOME && t.accountId == party.accountId -> LedgerKind.GAVE_GOODS to t.amount
                t.type == TxnType.EXPENSE && t.accountId == party.accountId -> LedgerKind.GOT_GOODS to -t.amount
                t.type == TxnType.TRANSFER && t.toAccountId == party.accountId -> LedgerKind.GAVE_MONEY to t.amount
                t.type == TxnType.TRANSFER && t.accountId == party.accountId -> LedgerKind.GOT_MONEY to -t.amount
                else -> continue
            }
            raw += Triple(t, kind, change)
        }
        val sorted = raw.sortedWith(compareBy({ it.first?.date }, { it.first?.createdAt }))
        val out = ArrayList<LedgerEntry>(sorted.size + 1)
        var balance = opening
        if (opening != 0L) {
            val first = sorted.firstOrNull()?.first?.date ?: LocalDate.now()
            out += LedgerEntry(first, LedgerKind.OPENING, opening, opening, "", null, null, null)
        }
        for ((t, kind, change) in sorted) {
            t ?: continue
            balance += change
            val mine = when (kind) {
                LedgerKind.GAVE_MONEY -> t.accountId
                LedgerKind.GOT_MONEY -> t.toAccountId
                else -> null
            }
            out += LedgerEntry(t.date, kind, change, balance, t.note, t.id, t.categoryId, mine)
        }
        return out
    }

    /** Indian mobile numbers for WhatsApp links: digits only, with 91 in front of a 10-digit number. */
    fun whatsAppNumber(phone: String): String? {
        val digits = phone.filter { it.isDigit() }.removePrefix("00")
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            digits.length in 11..15 -> digits
            else -> null
        }
    }

    /**
     * What a party has to pay (customer) or be paid (supplier), bill by bill. Each charge (goods or money given to a
     * customer; goods bought from a supplier) is due [Party.creditDays] after its date, and payments settle the
     * oldest charges first. The old balance is due at once.
     */
    fun dues(party: Party, entries: List<LedgerEntry>, today: LocalDate): LedgerDues {
        if (party.creditDays <= 0) return LedgerDues(0, 0, null, emptyList())
        // For a customer, charges raise the balance; for a supplier, they lower it.
        val sign = if (party.role == PartyRole.CUSTOMER) 1 else -1
        val open = ArrayDeque<Pair<LocalDate, Long>>()
        var credit = 0L
        for (e in entries) {
            val change = e.change * sign
            if (change > 0) {
                val due = if (e.kind == LedgerKind.OPENING) e.date else e.date.plusDays(party.creditDays.toLong())
                var amount = change
                // An advance paid earlier settles this charge first.
                val used = minOf(credit, amount)
                credit -= used
                amount -= used
                if (amount > 0) open.addLast(due to amount)
            } else if (change < 0) {
                var pay = -change
                while (pay > 0 && open.isNotEmpty()) {
                    val (due, amount) = open.removeFirst()
                    if (amount > pay) {
                        open.addFirst(due to amount - pay)
                        pay = 0
                    } else {
                        pay -= amount
                    }
                }
                credit += pay
            }
        }
        val bills = open.sortedBy { it.first }
        return LedgerDues(
            overdue = bills.filter { it.first.isBefore(today) }.sumOf { it.second },
            dueToday = bills.filter { it.first == today }.sumOf { it.second },
            next = bills.firstOrNull { !it.first.isBefore(today) },
            bills = bills,
        )
    }
}

/** A party's unpaid bills by due date: what's late, what's due today and the next one coming. */
data class LedgerDues(
    val overdue: Long,
    val dueToday: Long,
    val next: Pair<LocalDate, Long>?,
    val bills: List<Pair<LocalDate, Long>>,
) {
    val oldestOverdue: LocalDate? get() = bills.firstOrNull()?.first?.takeIf { overdue > 0 }
}
