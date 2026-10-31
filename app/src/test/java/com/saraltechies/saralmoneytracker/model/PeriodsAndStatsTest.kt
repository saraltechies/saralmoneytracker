package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PeriodsAndStatsTest {

    private val prefs = PeriodPrefs(weekStart = DayOfWeek.MONDAY, monthStartDay = 1)
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun calendarPeriods() {
        assertEquals(Period(PeriodKind.MONTH, d("2026-10-01"), d("2026-10-31")), Periods.containing(d("2026-10-15"), PeriodKind.MONTH, prefs))
        assertEquals(Period(PeriodKind.WEEK, d("2026-10-26"), d("2026-11-01")), Periods.containing(d("2026-10-31"), PeriodKind.WEEK, prefs))
        val sunday = PeriodPrefs(weekStart = DayOfWeek.SUNDAY)
        assertEquals(d("2026-10-25"), Periods.containing(d("2026-10-31"), PeriodKind.WEEK, sunday).start)
        assertEquals(Period(PeriodKind.YEAR, d("2026-01-01"), d("2026-12-31")), Periods.containing(d("2026-06-01"), PeriodKind.YEAR, prefs))
    }

    @Test
    fun salaryCycleMonths() {
        val payday = PeriodPrefs(monthStartDay = 25)
        assertEquals(Period(PeriodKind.MONTH, d("2026-10-25"), d("2026-11-24")), Periods.containing(d("2026-11-03"), PeriodKind.MONTH, payday))
        assertEquals(Period(PeriodKind.MONTH, d("2026-10-25"), d("2026-11-24")), Periods.containing(d("2026-10-25"), PeriodKind.MONTH, payday))
        val jan = Periods.containing(d("2026-01-10"), PeriodKind.MONTH, payday)
        assertEquals(d("2025-12-25"), jan.start)
        assertEquals(Period(PeriodKind.MONTH, d("2026-01-25"), d("2026-02-24")), Periods.shift(jan, 1, payday))
    }

    @Test
    fun shiftingAcrossYearsAndRanges() {
        val dec = Periods.containing(d("2026-12-10"), PeriodKind.MONTH, prefs)
        assertEquals(d("2027-01-01"), Periods.shift(dec, 1, prefs).start)
        val range = Periods.custom(d("2026-10-10"), d("2026-10-01"))
        assertEquals(d("2026-10-01"), range.start)
        assertEquals(Period(PeriodKind.CUSTOM, d("2026-10-11"), d("2026-10-20")), Periods.shift(range, 1, prefs))
    }

    private val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, opening = 100_000)
    private val bank = Account(id = 2, name = "Bank", type = AccountType.BANK, opening = 5_000_000)
    private val card = Account(id = 3, name = "Card", type = AccountType.CARD)

    private fun t(type: TxnType, amount: Long, date: String, cat: Long? = 10, from: Long = 2, to: Long? = null) =
        Txn(type = type, amount = amount, date = d(date), categoryId = cat, accountId = from, toAccountId = to)

    @Test
    fun cardBillPaymentIsNotSpending() {
        val txns = listOf(
            t(TxnType.EXPENSE, 50_000, "2026-10-02", from = 3), // dinner on the card
            t(TxnType.TRANSFER, 50_000, "2026-10-20", cat = null, from = 2, to = 3), // paying the card bill
            t(TxnType.INCOME, 6_200_000, "2026-10-01", cat = 20),
        )
        assertEquals(Totals(income = 6_200_000, expense = 50_000), Stats.totals(txns))
        val balances = Stats.balances(listOf(cash, bank, card), txns)
        assertEquals(100_000L, balances[1])
        assertEquals(5_000_000L + 6_200_000 - 50_000, balances[2])
        assertEquals(0L, balances[3])
    }

    @Test
    fun budgetAndSafeDailyAmount() {
        val food = Category(id = 10, name = "Food", kind = TxnType.EXPENSE, icon = "restaurant", color = 0, budget = 600_000)
        val fun_ = Category(id = 11, name = "Fun", kind = TxnType.EXPENSE, icon = "movie", color = 0, budget = 400_000)
        val month = Periods.containing(d("2026-10-01"), PeriodKind.MONTH, prefs)
        val txns = listOf(t(TxnType.EXPENSE, 300_000, "2026-10-05"), t(TxnType.EXPENSE, 100_000, "2026-10-06", cat = 12))
        val summary = Stats.budget(listOf(food, fun_), txns, month, d("2026-10-22"))
        assertEquals(1_000_000L, summary.budget)
        assertEquals(400_000L, summary.spent)
        assertEquals(10, summary.daysLeft)
        assertEquals(60_000L, summary.safePerDay)
        assertEquals(food.id, summary.lines.first().category.id)
        assertNull(Stats.budget(listOf(food), txns, month, d("2026-11-02")).safePerDay)
    }

    @Test
    fun categorySharesAndSeries() {
        val txns = listOf(
            t(TxnType.EXPENSE, 300, "2026-10-01", cat = 1),
            t(TxnType.EXPENSE, 100, "2026-10-01", cat = 2),
            t(TxnType.EXPENSE, 600, "2026-10-31", cat = 1),
        )
        val shares = Stats.byCategory(txns, TxnType.EXPENSE)
        assertEquals(1L, shares[0].categoryId)
        assertEquals(0.9f, shares[0].fraction, 0.001f)
        val month = Periods.containing(d("2026-10-01"), PeriodKind.MONTH, prefs)
        val daily = Stats.series(txns, month, TxnType.EXPENSE)
        assertEquals(31, daily.values.size)
        assertEquals(400L, daily.values[0])
        assertEquals(600L, daily.values[30])
        val yearly = Stats.series(txns, Periods.containing(d("2026-10-01"), PeriodKind.YEAR, prefs), TxnType.EXPENSE)
        assertEquals(12, yearly.values.size)
        assertEquals(1000L, yearly.values[9])
    }

    @Test
    fun monthlyRepeatsKeepTheirDay() {
        val start = d("2026-01-31")
        assertEquals(d("2026-02-28"), Recurrence.next(start, Frequency.MONTHLY, start))
        assertEquals(d("2026-03-31"), Recurrence.next(start, Frequency.MONTHLY, d("2026-02-28")))
        assertEquals(d("2026-01-31"), Recurrence.next(start, Frequency.MONTHLY, d("2026-01-01")))
        assertEquals(d("2027-01-31"), Recurrence.next(start, Frequency.YEARLY, start))
        assertEquals(d("2026-02-07"), Recurrence.next(start, Frequency.WEEKLY, start))
    }

    @Test
    fun dueEntriesCatchUpAndStopAtTheEnd() {
        val rule = Recurring(
            type = TxnType.EXPENSE, amount = 1, categoryId = 1, accountId = 1, frequency = Frequency.MONTHLY,
            startDate = d("2026-07-05"), nextDate = d("2026-07-05"), endDate = d("2026-09-30"),
        )
        assertEquals(listOf(d("2026-07-05"), d("2026-08-05"), d("2026-09-05")), Recurrence.due(rule, d("2026-10-31")))
        assertEquals(emptyList<LocalDate>(), Recurrence.due(rule.copy(active = false), d("2026-10-31")))
        assertEquals(listOf(d("2026-11-05")), Recurrence.upcoming(rule.copy(endDate = null, nextDate = d("2026-11-05")), d("2026-10-31"), 7))
    }
}
