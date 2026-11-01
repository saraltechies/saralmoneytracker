package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate

/** One line of a loan statement, with where the loan stood right after it. */
data class StatementRow(
    val date: LocalDate,
    val kind: LoanEventKind,
    val amount: Long,
    val note: String,
    /** Account the money came from or went to, if known. */
    val account: String?,
    val principalLeft: Long,
    val interestDue: Long,
) {
    val outstanding: Long get() = (principalLeft + interestDue).coerceAtLeast(0)
}

/** Everything a statement shows for a period: the balance brought forward, each entry, and the totals. */
data class StatementData(
    val from: LocalDate,
    val to: LocalDate,
    /** Where the loan stood the day before [from]; null when the period starts with the loan. */
    val opening: LoanSummary?,
    val rows: List<StatementRow>,
    /** Where the loan stands at the end of the period. */
    val closing: LoanSummary,
    val givenInPeriod: Long,
    val repaidInPeriod: Long,
    val interestPaidInPeriod: Long,
)

object Statements {

    /**
     * Builds the statement of [loan] from [from] to [to]. [accounts] names the account of each entry (by txn id),
     * so the statement can say "Cash" or "HDFC".
     */
    fun build(loan: Loan, events: List<LoanEvent>, from: LocalDate, to: LocalDate, accounts: Map<Long, String> = emptyMap()): StatementData {
        val start = if (from.isBefore(loan.startDate)) loan.startDate else from
        val opening = if (start.isAfter(loan.startDate) && events.any { it.date.isBefore(start) }) {
            LoanMath.summary(loan, events, start.minusDays(1))
        } else null
        val inPeriod = events.filter { !it.date.isBefore(start) && !it.date.isAfter(to) }.sortedBy { it.date }
        // Several entries on one day: the balance after each counts the ones before it on that day only.
        val rows = inPeriod.mapIndexed { i, e ->
            val upTo = events.filter { it.date.isBefore(e.date) } + inPeriod.subList(0, i + 1).filter { it.date == e.date }
            val s = LoanMath.summary(loan, upTo, e.date)
            StatementRow(e.date, e.kind, e.amount, e.note, e.txnId?.let { accounts[it] }, s.principalLeft, s.interestDue)
        }
        return StatementData(
            from = start, to = to, opening = opening, rows = rows, closing = LoanMath.summary(loan, events, to),
            givenInPeriod = inPeriod.filter { it.kind == LoanEventKind.GIVEN }.sumOf { it.amount },
            repaidInPeriod = inPeriod.filter { it.kind == LoanEventKind.REPAID }.sumOf { it.amount },
            interestPaidInPeriod = inPeriod.filter { it.kind == LoanEventKind.INTEREST }.sumOf { it.amount },
        )
    }

    /** India's financial year (1 April to 31 March) that contains [date]. */
    fun financialYear(date: LocalDate): Pair<LocalDate, LocalDate> {
        val startYear = if (date.monthValue >= 4) date.year else date.year - 1
        return LocalDate.of(startYear, 4, 1) to LocalDate.of(startYear + 1, 3, 31)
    }
}
