package com.saraltechies.saralmoneytracker.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToLong

/** Money you gave to someone (they owe you) or took from someone (you owe them). */
enum class LoanDirection { LENT, BORROWED }

/** How the interest rate is quoted: "2% a month" (common between people) or "12% a year" (banks). */
enum class RateUnit { MONTH, YEAR }

/** Paid back whenever possible, or in fixed monthly instalments (EMI). */
enum class LoanPlan { FLEXIBLE, MONTHLY }

/**
 * Money lent to or borrowed from a person, on credit or on interest.
 *
 * Every loan has its own account of type [AccountType.LOAN], named after the person. Lending is then a transfer
 * from your account into it and a repayment is a transfer back, so your balances and net worth stay right and none
 * of it counts as spending or income. Interest is real income (or expense) and is recorded as such.
 */
data class Loan(
    val id: Long = 0,
    val accountId: Long,
    val direction: LoanDirection,
    /** The day the money was given; interest runs from here. */
    val startDate: LocalDate,
    /** Interest rate in hundredths of a percent: 2% = 200, 1.5% = 150. Zero for credit without interest. */
    val rate: Long = 0,
    val rateUnit: RateUnit = RateUnit.MONTH,
    val plan: LoanPlan = LoanPlan.FLEXIBLE,
    /** Number of monthly instalments, for [LoanPlan.MONTHLY]. */
    val installments: Int = 0,
    /** Amount of each instalment, for [LoanPlan.MONTHLY]. */
    val installment: Long = 0,
    val note: String = "",
    val closed: Boolean = false,
    val createdAt: Long = 0,
    val bookId: Long = Books.PERSONAL_ID,
    /** The person, bank or vendor this loan is with, and their details. */
    val contactId: Long? = null,
    /** Gold, silver or something else kept as security (girvi); null for a loan without one. */
    val pledge: Pledge? = null,
)

/** What a pledge is made of. */
enum class PledgeKind { GOLD, SILVER, OTHER }

/**
 * Something kept as security for a loan (girvi): with you when you lent the money, with the lender when you borrowed.
 * Each loan has its own pledge, so the same person can pledge a chain at 12% a year and bangles later at 18%.
 */
data class Pledge(
    val kind: PledgeKind,
    /** What the items are: "1 chain, 2 bangles". */
    val items: String = "",
    /** Weight in milligrams, so 10.25 g is 10250. Zero when not known. */
    val weightMg: Long = 0,
    /** Purity as people say it: "22K", "916", "92.5". */
    val purity: String = "",
    /** What the items were valued at, in hundredths. Zero when not known. */
    val value: Long = 0,
    /** The day the items were given back; null while they're still held. */
    val returnedOn: LocalDate? = null,
) {
    val returned: Boolean get() = returnedOn != null

    companion object {
        /** "10.25" or "10,5" grams to milligrams; 0 when empty, null when it isn't a number. */
        fun parseGrams(text: String): Long? {
            val t = text.trim().replace(',', '.')
            if (t.isEmpty()) return 0
            return t.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.movePointRight(3)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
        }

        /** Milligrams as grams without needless zeros: 10250 is "10.25", 8000 is "8". */
        fun grams(mg: Long): String = BigDecimal.valueOf(mg, 3).stripTrailingZeros().toPlainString()
    }
}

/** Gold, silver and other items added up, with what they were valued at. */
data class PledgeTotals(val goldMg: Long = 0, val silverMg: Long = 0, val others: Int = 0, val value: Long = 0, val count: Int = 0) {
    val isEmpty: Boolean get() = count == 0

    operator fun plus(p: Pledge) = PledgeTotals(
        goldMg = goldMg + if (p.kind == PledgeKind.GOLD) p.weightMg else 0,
        silverMg = silverMg + if (p.kind == PledgeKind.SILVER) p.weightMg else 0,
        others = others + if (p.kind == PledgeKind.OTHER) 1 else 0,
        value = value + p.value,
        count = count + 1,
    )

    companion object {
        /** Pledges of [loans] still held: not given back, on loans that aren't closed. */
        fun held(loans: List<Loan>): PledgeTotals =
            loans.filter { !it.closed }.mapNotNull { it.pledge?.takeIf { p -> !p.returned } }.fold(PledgeTotals()) { t, p -> t + p }
    }
}

enum class LoanEventKind {
    /** Principal added: the money given at the start, or more given later. */
    GIVEN,
    /** Principal paid back. */
    REPAID,
    /** Interest paid. */
    INTEREST,
}

data class LoanEvent(val date: LocalDate, val kind: LoanEventKind, val amount: Long, val txnId: Long? = null, val note: String = "")

data class Installment(val number: Int, val date: LocalDate, val amount: Long, val interest: Long, val principal: Long, val balanceAfter: Long)

data class LoanSummary(
    /** All principal given, including the start. */
    val given: Long,
    val repaid: Long,
    val interestAccrued: Long,
    val interestPaid: Long,
    /** Monthly interest on what is still owed, at today's balance. */
    val monthlyInterest: Long,
    /** For monthly plans: instalments that have come due so far. */
    val installmentsDue: Int,
    /** For monthly plans: how much of the due instalments is still unpaid. */
    val overdue: Long,
    /** The next payment date and what is left to pay on it; null when nothing more is expected. */
    val nextDueDate: LocalDate?,
    val nextDueAmount: Long,
) {
    val principalLeft: Long get() = given - repaid
    /** Interest earned or charged but not paid yet. Never negative. */
    val interestDue: Long get() = (interestAccrued - interestPaid).coerceAtLeast(0)
    /** Everything still owed: principal plus unpaid interest. */
    val outstanding: Long get() = (principalLeft + interestDue).coerceAtLeast(0)
    val totalPaid: Long get() = repaid + interestPaid
    val settled: Boolean get() = principalLeft <= 0 && interestDue <= 0
    /** Share of the money given that has come back, 0..1. */
    val progress: Float get() = if (given <= 0) 0f else (repaid.toFloat() / given).coerceIn(0f, 1f)
}

/** Interest and instalment arithmetic. Amounts are hundredths, as everywhere in the app. */
object LoanMath {

    /** The monthly rate as a fraction: 2% a month is 0.02, 12% a year is 0.01. */
    fun monthlyRate(rate: Long, unit: RateUnit): Double = when (unit) {
        RateUnit.MONTH -> rate / 10_000.0
        RateUnit.YEAR -> rate / 10_000.0 / 12
    }

    fun monthlyRate(loan: Loan): Double = monthlyRate(loan.rate, loan.rateUnit)

    /**
     * Months between two dates, counting whole calendar months and then the part of the next month by days:
     * 5 Jan to 5 Feb is exactly 1, 5 Jan to 20 Feb is 1 + 15/28 (February has 28 days that year).
     */
    fun monthsBetween(from: LocalDate, to: LocalDate): Double {
        if (!to.isAfter(from)) return 0.0
        val whole = ChronoUnit.MONTHS.between(from, to)
        val mark = from.plusMonths(whole)
        val nextMark = from.plusMonths(whole + 1)
        val part = ChronoUnit.DAYS.between(mark, to).toDouble() / ChronoUnit.DAYS.between(mark, nextMark)
        return whole + part
    }

    /** The equal monthly instalment (EMI) that repays [principal] in [months] at [monthlyRate], interest on the reducing balance. */
    fun emi(principal: Long, monthlyRate: Double, months: Int): Long {
        if (months <= 0 || principal <= 0) return 0
        if (monthlyRate <= 0.0) return ceilDiv(principal, months.toLong())
        val f = (1 + monthlyRate).pow(months)
        return (principal * monthlyRate * f / (f - 1)).roundToLong()
    }

    /** The full instalment plan from the start date: due on the same day each month, interest on the reducing balance. */
    fun schedule(principal: Long, monthlyRate: Double, months: Int, installment: Long, start: LocalDate): List<Installment> {
        if (months <= 0 || installment <= 0 || principal <= 0) return emptyList()
        val rows = ArrayList<Installment>(months)
        var balance = principal
        for (n in 1..months) {
            val interest = (balance * monthlyRate).roundToLong()
            // The last instalment clears whatever is left, so rounding never leaves a few paise owed.
            val pay = if (n == months || installment > balance + interest) balance + interest else installment
            val principalPart = pay - interest
            balance -= principalPart
            rows += Installment(n, start.plusMonths(n.toLong()), pay, interest, principalPart, balance.coerceAtLeast(0))
            if (balance <= 0) break
        }
        return rows
    }

    /**
     * Where a loan stands on [asOf]. Interest is simple interest on the principal still owed, so paying back part
     * of the principal lowers the interest from that day on. For a monthly plan, payments are matched against the
     * instalments in order, so paying early or partly is handled.
     */
    fun summary(loan: Loan, events: List<LoanEvent>, asOf: LocalDate): LoanSummary {
        val sorted = events.filter { !it.date.isAfter(asOf) }.sortedBy { it.date }
        val rate = monthlyRate(loan)
        var given = 0L
        var repaid = 0L
        var interestPaid = 0L
        var accrued = 0.0
        var balance = 0L
        var since: LocalDate? = null
        for (e in sorted) {
            since?.let { if (rate > 0 && balance > 0) accrued += balance * rate * monthsBetween(it, e.date) }
            since = maxOf(e.date, loan.startDate)
            when (e.kind) {
                LoanEventKind.GIVEN -> { given += e.amount; balance += e.amount }
                LoanEventKind.REPAID -> { repaid += e.amount; balance -= e.amount }
                LoanEventKind.INTEREST -> interestPaid += e.amount
            }
        }
        since?.let { if (rate > 0 && balance > 0) accrued += balance * rate * monthsBetween(it, asOf) }
        val monthly = if (balance > 0) (balance * rate).roundToLong() else 0L

        var installmentsDue = 0
        var overdue = 0L
        var nextDate: LocalDate? = null
        var nextAmount = 0L
        val paid = repaid + interestPaid
        if (loan.plan == LoanPlan.MONTHLY && loan.installments > 0 && loan.installment > 0) {
            val plan = schedule(given.coerceAtLeast(0), rate, loan.installments, loan.installment, loan.startDate)
            var cumulative = 0L
            var dueSoFar = 0L
            for (row in plan) {
                cumulative += row.amount
                if (!row.date.isAfter(asOf)) {
                    installmentsDue++
                    dueSoFar = cumulative
                } else if (nextDate == null && cumulative > paid) {
                    nextDate = row.date
                    nextAmount = (cumulative - maxOf(paid, dueSoFar)).coerceAtMost(row.amount)
                }
            }
            overdue = (dueSoFar - paid).coerceAtLeast(0)
            // Behind on payments: the next thing to pay is what's overdue, today.
            if (overdue > 0) {
                nextDate = asOf
                nextAmount = overdue
            }
            if (balance <= 0 && overdue == 0L) nextDate = null
        } else if (rate > 0 && balance > 0) {
            // Interest is usually paid every month on the day the money was given.
            val months = ChronoUnit.MONTHS.between(loan.startDate, asOf)
            var d = loan.startDate.plusMonths(months)
            if (d.isBefore(asOf)) d = loan.startDate.plusMonths(months + 1)
            nextDate = d
            // Everything unpaid by that day, including any months that were missed.
            val byThen = accrued + balance * rate * monthsBetween(asOf, d)
            nextAmount = (byThen.roundToLong() - interestPaid).coerceAtLeast(0)
        }
        return LoanSummary(
            given = given, repaid = repaid, interestAccrued = accrued.roundToLong(), interestPaid = interestPaid,
            monthlyInterest = monthly, installmentsDue = installmentsDue, overdue = overdue, nextDueDate = nextDate, nextDueAmount = nextAmount,
        )
    }

    /**
     * A loan's history from its account's opening balance (a loan given before using the app) and its entries:
     * transfers into or out of the loan account move the principal, income or expense entries are interest.
     */
    fun events(loan: Loan, opening: Long, txns: List<Txn>): List<LoanEvent> {
        // A lent loan's account holds what you're owed (positive); a borrowed one holds what you owe (negative).
        val sign = if (loan.direction == LoanDirection.LENT) 1 else -1
        val list = mutableListOf<LoanEvent>()
        if (opening != 0L) {
            val v = opening * sign
            list += LoanEvent(loan.startDate, if (v > 0) LoanEventKind.GIVEN else LoanEventKind.REPAID, kotlin.math.abs(v))
        }
        for (t in txns) {
            val kind = if (t.type == TxnType.TRANSFER) {
                val delta = when (loan.accountId) {
                    t.toAccountId -> t.amount
                    t.accountId -> -t.amount
                    else -> continue
                } * sign
                if (delta > 0) LoanEventKind.GIVEN else LoanEventKind.REPAID
            } else {
                LoanEventKind.INTEREST
            }
            list += LoanEvent(t.date, kind, t.amount, t.id, t.note)
        }
        return list.sortedBy { it.date }
    }

    /** Splits a payment: interest still due is paid first, the rest goes to the principal. */
    fun split(amount: Long, interestDue: Long): Pair<Long, Long> {
        val interest = amount.coerceAtMost(interestDue.coerceAtLeast(0)).coerceAtLeast(0)
        return interest to (amount - interest)
    }

    private fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b
}
