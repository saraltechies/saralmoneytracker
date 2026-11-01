package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DuesAndForecastTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun rupees(r: Long) = r * 100

    private val customer = Party(id = 1, accountId = 7, contactId = 1, role = PartyRole.CUSTOMER, creditDays = 15)
    private val supplier = Party(id = 2, accountId = 8, contactId = 2, role = PartyRole.SUPPLIER, creditDays = 30)

    private fun entry(date: String, change: Long, kind: LedgerKind = if (change > 0) LedgerKind.GAVE_GOODS else LedgerKind.GOT_MONEY) =
        LedgerEntry(d(date), kind, change, 0, "", 1, null, null)

    @Test
    fun paymentsSettleTheOldestBillFirst() {
        val entries = listOf(entry("2026-11-01", rupees(1_000)), entry("2026-11-10", rupees(500)), entry("2026-11-12", -rupees(1_200)))
        val on20 = Ledgers.dues(customer, entries, d("2026-11-20"))
        assertEquals(0L, on20.overdue)
        assertEquals(d("2026-11-25") to rupees(300), on20.next)
        val dec1 = Ledgers.dues(customer, entries, d("2026-12-01"))
        assertEquals(rupees(300), dec1.overdue)
        assertEquals(d("2026-11-25"), dec1.oldestOverdue)
        assertEquals(rupees(300), Ledgers.dues(customer, entries, d("2026-11-25")).dueToday)
    }

    @Test
    fun advanceAndOldBalance() {
        // ₹200 old balance (due at once), ₹500 paid in advance, then a ₹1,000 sale.
        val entries = listOf(
            entry("2026-11-01", rupees(200), LedgerKind.OPENING),
            entry("2026-11-02", -rupees(500)),
            entry("2026-11-05", rupees(1_000)),
        )
        val dues = Ledgers.dues(customer, entries, d("2026-11-10"))
        assertEquals(0L, dues.overdue)
        // 300 of the advance was left after the old balance, so 700 of the sale is due on the 20th.
        assertEquals(d("2026-11-20") to rupees(700), dues.next)
    }

    @Test
    fun supplierBillsAndNoCreditDays() {
        val entries = listOf(entry("2026-10-01", -rupees(5_000), LedgerKind.GOT_GOODS), entry("2026-10-15", rupees(2_000), LedgerKind.GAVE_MONEY))
        val dues = Ledgers.dues(supplier, entries, d("2026-11-05"))
        assertEquals(rupees(3_000), dues.overdue)
        val none = Ledgers.dues(customer.copy(creditDays = 0), listOf(entry("2026-01-01", rupees(9_000))), d("2026-11-05"))
        assertEquals(0L, none.overdue)
        assertNull(none.next)
    }

    @Test
    fun forecastAddsUpAndFindsTheLowPoint() {
        val today = d("2026-11-03")
        val salary = Recurring(
            id = 1, type = TxnType.INCOME, amount = rupees(60_000), categoryId = 1, accountId = 1, frequency = Frequency.MONTHLY,
            startDate = d("2026-01-30"), nextDate = d("2026-11-30"),
        )
        val rent = Recurring(
            id = 2, type = TxnType.EXPENSE, amount = rupees(15_000), categoryId = 2, accountId = 1, frequency = Frequency.MONTHLY,
            startDate = d("2026-01-05"), nextDate = d("2026-11-05"),
        )
        val emiLoan = Loan(
            id = 1, accountId = 9, direction = LoanDirection.BORROWED, startDate = d("2026-01-10"), plan = LoanPlan.MONTHLY,
            installments = 24, installment = rupees(10_000),
        )
        val emiEvents = listOf(LoanEvent(d("2026-01-10"), LoanEventKind.GIVEN, rupees(240_000))) +
            (1..9).map { LoanEvent(d("2026-01-10").plusMonths(it.toLong()), LoanEventKind.REPAID, rupees(10_000)) }
        val emiSummary = LoanMath.summary(emiLoan, emiEvents, today)
        val bills = LedgerDues(0, 0, d("2026-11-20") to rupees(2_500), listOf(d("2026-11-20") to rupees(2_500)))

        val f = Forecasts.build(
            today, d("2026-11-30"), rupees(20_000),
            rules = listOf(salary, rent), ruleTitle = { it.note },
            loans = listOf(Triple(emiLoan, emiSummary, "Bank")),
            ledgerBills = listOf(Triple(customer, bills, "Ramesh")),
        )
        assertEquals(rupees(62_500), f.moneyIn)
        assertEquals(rupees(25_000), f.moneyOut)
        assertEquals(rupees(57_500), f.endBalance)
        // 20,000 - 15,000 rent on the 5th - 10,000 EMI on the 10th = -5,000 before the customer pays.
        assertEquals(d("2026-11-10") to -rupees(5_000), f.lowest)
    }
}
