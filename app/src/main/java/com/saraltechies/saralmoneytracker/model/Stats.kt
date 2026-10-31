package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Totals(val income: Long = 0, val expense: Long = 0) {
    val net: Long get() = income - expense
}

data class CategoryShare(val categoryId: Long?, val amount: Long, val fraction: Float, val count: Int)

/** Bars of a trend chart: one per day, or one per month for long ranges. */
data class Series(val buckets: List<LocalDate>, val values: List<Long>, val monthly: Boolean)

data class BudgetLine(val category: Category, val budget: Long, val spent: Long) {
    val fraction: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
    val left: Long get() = budget - spent
}

data class BudgetSummary(
    val lines: List<BudgetLine>,
    /** Sum of all category budgets. */
    val budget: Long,
    /** All spending in the period, budgeted or not. */
    val spent: Long,
    /** How much can still be spent per remaining day to stay within budget; null when the period is over or no budget. */
    val safePerDay: Long?,
    val daysLeft: Int,
) {
    val left: Long get() = budget - spent
    val fraction: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
}

object Stats {

    /** Transfers move money between your own accounts, so they're neither income nor expense. */
    fun totals(txns: Iterable<Txn>): Totals {
        var income = 0L
        var expense = 0L
        for (t in txns) when (t.type) {
            TxnType.INCOME -> income += t.amount
            TxnType.EXPENSE -> expense += t.amount
            TxnType.TRANSFER -> Unit
        }
        return Totals(income, expense)
    }

    fun byCategory(txns: Iterable<Txn>, type: TxnType): List<CategoryShare> {
        val sums = LinkedHashMap<Long?, Pair<Long, Int>>()
        for (t in txns) if (t.type == type) {
            val (sum, count) = sums[t.categoryId] ?: (0L to 0)
            sums[t.categoryId] = (sum + t.amount) to (count + 1)
        }
        val total = sums.values.sumOf { it.first }.coerceAtLeast(1)
        return sums.map { (id, v) -> CategoryShare(id, v.first, v.first.toFloat() / total, v.second) }
            .sortedByDescending { it.amount }
    }

    /** Per-day bars, or per-month bars when the range is longer than about two months. */
    fun series(txns: Iterable<Txn>, period: Period, type: TxnType): Series {
        val monthly = period.kind == PeriodKind.YEAR || period.days > 62
        val buckets = mutableListOf<LocalDate>()
        if (monthly) {
            var d = period.start.withDayOfMonth(1)
            while (!d.isAfter(period.end)) {
                buckets += d
                d = d.plusMonths(1)
            }
        } else {
            var d = period.start
            while (!d.isAfter(period.end)) {
                buckets += d
                d = d.plusDays(1)
            }
        }
        val values = LongArray(buckets.size)
        for (t in txns) {
            if (t.type != type || t.date !in period) continue
            val index = if (monthly) {
                ChronoUnit.MONTHS.between(buckets.first(), t.date.withDayOfMonth(1)).toInt()
            } else {
                ChronoUnit.DAYS.between(period.start, t.date).toInt()
            }
            if (index in values.indices) values[index] += t.amount
        }
        return Series(buckets, values.toList(), monthly)
    }

    /** Current balance of every account: opening balance plus everything that moved in or out. */
    fun balances(accounts: Iterable<Account>, txns: Iterable<Txn>): Map<Long, Long> {
        val balances = HashMap<Long, Long>()
        for (a in accounts) balances[a.id] = a.opening
        for (t in txns) when (t.type) {
            TxnType.EXPENSE -> balances.merge(t.accountId, -t.amount, Long::plus)
            TxnType.INCOME -> balances.merge(t.accountId, t.amount, Long::plus)
            TxnType.TRANSFER -> {
                balances.merge(t.accountId, -t.amount, Long::plus)
                t.toAccountId?.let { balances.merge(it, t.amount, Long::plus) }
            }
        }
        return balances
    }

    fun budget(categories: Iterable<Category>, txns: Iterable<Txn>, period: Period, today: LocalDate): BudgetSummary {
        val spentBy = HashMap<Long?, Long>()
        var spent = 0L
        for (t in txns) if (t.type == TxnType.EXPENSE && t.date in period) {
            spentBy.merge(t.categoryId, t.amount, Long::plus)
            spent += t.amount
        }
        val lines = categories
            .filter { it.kind == TxnType.EXPENSE && (it.budget ?: 0) > 0 }
            .map { BudgetLine(it, it.budget!!, spentBy[it.id] ?: 0) }
            .sortedByDescending { it.fraction }
        val budget = lines.sumOf { it.budget }
        val daysLeft = when {
            today.isAfter(period.end) -> 0
            today.isBefore(period.start) -> period.days
            else -> (ChronoUnit.DAYS.between(today, period.end) + 1).toInt()
        }
        val safe = if (budget > 0 && daysLeft > 0) ((budget - spent) / daysLeft).coerceAtLeast(0) else null
        return BudgetSummary(lines, budget, spent, safe, daysLeft)
    }
}
