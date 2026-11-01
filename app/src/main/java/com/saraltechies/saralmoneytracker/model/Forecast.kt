package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate

/** One expected movement of money: a repeating entry, an EMI or interest, or a customer's or supplier's bill. */
data class ForecastItem(
    val date: LocalDate,
    /** Plus for money expected in, minus for money going out. */
    val amount: Long,
    val title: String,
    val kind: Kind,
) {
    enum class Kind { REPEATING, LOAN, LEDGER }
}

data class CashForecast(
    val from: LocalDate,
    val to: LocalDate,
    val startBalance: Long,
    val items: List<ForecastItem>,
) {
    val moneyIn: Long get() = items.filter { it.amount > 0 }.sumOf { it.amount }
    val moneyOut: Long get() = -items.filter { it.amount < 0 }.sumOf { it.amount }
    val endBalance: Long get() = startBalance + items.sumOf { it.amount }

    /** The lowest the balance gets, and when: the day to watch out for. */
    val lowest: Pair<LocalDate, Long>
        get() {
            var balance = startBalance
            var low = from to startBalance
            for (item in items.sortedBy { it.date }) {
                balance += item.amount
                if (balance < low.second) low = item.date to balance
            }
            return low
        }

    /** Balance after each item, in date order. */
    fun running(): List<Pair<ForecastItem, Long>> {
        var balance = startBalance
        return items.sortedBy { it.date }.map { balance += it.amount; it to balance }
    }
}

/**
 * Where your money is heading: today's balance plus what's expected until [to]. Counts repeating income and
 * expenses, loan EMIs and interest (money in for loans you gave, out for loans you took), and customers' and
 * suppliers' bills as they fall due. Only real income and spending move it; transfers between your own accounts
 * don't.
 */
object Forecasts {

    fun build(
        today: LocalDate,
        to: LocalDate,
        startBalance: Long,
        rules: List<Recurring>,
        ruleTitle: (Recurring) -> String,
        loans: List<Triple<Loan, LoanSummary, String>>,
        ledgerBills: List<Triple<Party, LedgerDues, String>>,
    ): CashForecast {
        val items = mutableListOf<ForecastItem>()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, to)
        for (r in rules) {
            if (r.type == TxnType.TRANSFER) continue
            val dates = buildList {
                if (r.active && r.nextDate == today) add(today)
                addAll(Recurrence.upcoming(r, today, days))
            }
            for (d in dates) items += ForecastItem(d, if (r.type == TxnType.INCOME) r.amount else -r.amount, ruleTitle(r), ForecastItem.Kind.REPEATING)
        }
        for ((loan, s, name) in loans) {
            if (loan.closed) continue
            val sign = if (loan.direction == LoanDirection.LENT) 1 else -1
            if (loan.plan == LoanPlan.MONTHLY && loan.installments > 0 && loan.installment > 0) {
                // EMIs: anything overdue today, then each instalment of the plan that falls in the window, less
                // whatever was paid ahead.
                if (s.overdue > 0) items += ForecastItem(today, sign * s.overdue, name, ForecastItem.Kind.LOAN)
                val plan = LoanMath.schedule(s.given, LoanMath.monthlyRate(loan), loan.installments, loan.installment, loan.startDate)
                var ahead = (s.totalPaid - plan.filter { !it.date.isAfter(today) }.sumOf { it.amount }).coerceAtLeast(0)
                for (row in plan) {
                    if (!row.date.isAfter(today) || row.date.isAfter(to)) continue
                    val used = minOf(ahead, row.amount)
                    ahead -= used
                    if (row.amount - used > 0) items += ForecastItem(row.date, sign * (row.amount - used), name, ForecastItem.Kind.LOAN)
                }
            } else if (loan.rate > 0 && s.principalLeft > 0) {
                // Interest paid as you go: the next payment, then a month's interest each month after it.
                var date = s.nextDueDate ?: continue
                if (date.isAfter(to)) continue
                items += ForecastItem(maxOf(date, today), sign * s.nextDueAmount, name, ForecastItem.Kind.LOAN)
                date = date.plusMonths(1)
                while (!date.isAfter(to) && s.monthlyInterest > 0) {
                    items += ForecastItem(date, sign * s.monthlyInterest, name, ForecastItem.Kind.LOAN)
                    date = date.plusMonths(1)
                }
            }
        }
        for ((party, dues, name) in ledgerBills) {
            val sign = if (party.role == PartyRole.CUSTOMER) 1 else -1
            // Late bills are expected today; the rest on their due dates.
            if (dues.overdue > 0) items += ForecastItem(today, sign * dues.overdue, name, ForecastItem.Kind.LEDGER)
            for ((date, amount) in dues.bills) {
                if (date.isBefore(today) || date.isAfter(to)) continue
                items += ForecastItem(date, sign * amount, name, ForecastItem.Kind.LEDGER)
            }
        }
        return CashForecast(today, to, startBalance, items.sortedBy { it.date })
    }
}
