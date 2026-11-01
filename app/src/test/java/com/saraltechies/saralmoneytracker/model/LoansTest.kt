package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoansTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private fun rupees(r: Long) = r * 100

    private fun lent(rate: Long = 0, unit: RateUnit = RateUnit.MONTH, plan: LoanPlan = LoanPlan.FLEXIBLE, months: Int = 0, emi: Long = 0) =
        Loan(id = 1, accountId = 9, direction = LoanDirection.LENT, startDate = jan1, rate = rate, rateUnit = unit, plan = plan, installments = months, installment = emi)

    private fun given(amount: Long, date: LocalDate = jan1) = LoanEvent(date, LoanEventKind.GIVEN, amount)
    private fun repaid(amount: Long, date: LocalDate) = LoanEvent(date, LoanEventKind.REPAID, amount)
    private fun interest(amount: Long, date: LocalDate) = LoanEvent(date, LoanEventKind.INTEREST, amount)

    @Test
    fun monthsCountWholeMonthsThenDays() {
        assertEquals(1.0, LoanMath.monthsBetween(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 5)), 1e-9)
        assertEquals(1 + 15.0 / 28, LoanMath.monthsBetween(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 20)), 1e-9)
        assertEquals(0.0, LoanMath.monthsBetween(jan1, jan1), 1e-9)
        assertEquals(0.0, LoanMath.monthsBetween(jan1, jan1.minusDays(3)), 1e-9)
    }

    @Test
    fun rateUnits() {
        assertEquals(0.02, LoanMath.monthlyRate(200, RateUnit.MONTH), 1e-12)
        assertEquals(0.01, LoanMath.monthlyRate(1200, RateUnit.YEAR), 1e-12)
    }

    @Test
    fun simpleInterestPerMonth() {
        // ₹10,000 at 2% a month: ₹200 every month.
        val s = LoanMath.summary(lent(rate = 200), listOf(given(rupees(10_000))), LocalDate.of(2026, 4, 1))
        assertEquals(rupees(600), s.interestAccrued)
        assertEquals(rupees(600), s.interestDue)
        assertEquals(rupees(200), s.monthlyInterest)
        assertEquals(rupees(10_600), s.outstanding)
        // Due today (the 1st), with all three unpaid months.
        assertEquals(LocalDate.of(2026, 4, 1), s.nextDueDate)
        assertEquals(rupees(600), s.nextDueAmount)
    }

    @Test
    fun nextInterestDateIsTheMonthlyAnniversary() {
        val s = LoanMath.summary(lent(rate = 200), listOf(given(rupees(10_000)), interest(rupees(200), LocalDate.of(2026, 2, 1))), LocalDate.of(2026, 2, 10))
        assertEquals(LocalDate.of(2026, 3, 1), s.nextDueDate)
        assertEquals(rupees(200), s.nextDueAmount)
    }

    @Test
    fun repayingPrincipalLowersInterest() {
        val s = LoanMath.summary(
            lent(rate = 200),
            listOf(given(rupees(10_000)), repaid(rupees(5_000), LocalDate.of(2026, 2, 1)), interest(rupees(200), LocalDate.of(2026, 2, 1))),
            LocalDate.of(2026, 3, 1),
        )
        assertEquals(rupees(300), s.interestAccrued) // 200 in January on 10,000 + 100 in February on 5,000
        assertEquals(rupees(100), s.interestDue)
        assertEquals(rupees(5_000), s.principalLeft)
        assertEquals(rupees(5_100), s.outstanding)
        assertEquals(0.5f, s.progress, 1e-6f)
    }

    @Test
    fun interestFreeCredit() {
        val s = LoanMath.summary(lent(), listOf(given(rupees(2_500)), repaid(rupees(1_000), LocalDate.of(2026, 3, 1))), LocalDate.of(2026, 6, 1))
        assertEquals(0L, s.interestAccrued)
        assertEquals(rupees(1_500), s.outstanding)
        assertNull(s.nextDueDate)
        assertFalse(s.settled)
        val done = LoanMath.summary(lent(), listOf(given(rupees(2_500)), repaid(rupees(2_500), LocalDate.of(2026, 3, 1))), LocalDate.of(2026, 6, 1))
        assertTrue(done.settled)
        assertEquals(0L, done.outstanding)
    }

    @Test
    fun emiMatchesTheBankFormula() {
        // ₹1,00,000 at 12% a year for 12 months: ₹8,884.88 a month.
        assertEquals(888_488L, LoanMath.emi(rupees(100_000), 0.01, 12))
        // No interest: split evenly, rounded up so it's always repaid.
        assertEquals(rupees(1_000), LoanMath.emi(rupees(12_000), 0.0, 12))
        assertEquals(33_334L, LoanMath.emi(rupees(1_000), 0.0, 3))
    }

    @Test
    fun scheduleRepaysEverything() {
        val plan = LoanMath.schedule(rupees(100_000), 0.01, 12, 888_488, jan1)
        assertEquals(12, plan.size)
        assertEquals(LocalDate.of(2026, 2, 1), plan.first().date)
        assertEquals(rupees(1_000), plan.first().interest)
        assertEquals(0L, plan.last().balanceAfter)
        assertEquals(rupees(100_000).toDouble(), plan.sumOf { it.principal }.toDouble(), 12.0)
    }

    @Test
    fun monthlyPlanTracksMissedInstalments() {
        val loan = lent(plan = LoanPlan.MONTHLY, months = 12, emi = rupees(1_000))
        val events = listOf(given(rupees(12_000)), repaid(rupees(1_000), LocalDate.of(2026, 2, 1)), repaid(rupees(1_000), LocalDate.of(2026, 3, 1)))
        val s = LoanMath.summary(loan, events, LocalDate.of(2026, 4, 10))
        assertEquals(3, s.installmentsDue)
        assertEquals(rupees(1_000), s.overdue)
        assertEquals(LocalDate.of(2026, 4, 10), s.nextDueDate)
        assertEquals(rupees(1_000), s.nextDueAmount)
    }

    @Test
    fun monthlyPlanPaidAhead() {
        val loan = lent(plan = LoanPlan.MONTHLY, months = 12, emi = rupees(1_000))
        val events = listOf(given(rupees(12_000)), repaid(rupees(1_500), LocalDate.of(2026, 2, 1)))
        val s = LoanMath.summary(loan, events, LocalDate.of(2026, 2, 15))
        assertEquals(1, s.installmentsDue)
        assertEquals(0L, s.overdue)
        assertEquals(LocalDate.of(2026, 3, 1), s.nextDueDate)
        assertEquals(rupees(500), s.nextDueAmount)
    }

    @Test
    fun emiLoanPaidOnTimeHasNoInterestLeft() {
        val emi = LoanMath.emi(rupees(100_000), 0.01, 12)
        val loan = lent(rate = 1200, unit = RateUnit.YEAR, plan = LoanPlan.MONTHLY, months = 12, emi = emi)
        val plan = LoanMath.schedule(rupees(100_000), 0.01, 12, emi, jan1)
        val events = listOf(given(rupees(100_000))) + plan.take(3).flatMap { listOf(interest(it.interest, it.date), repaid(it.principal, it.date)) }
        val s = LoanMath.summary(loan, events, plan[2].date)
        assertEquals(0L, s.overdue)
        assertTrue(s.interestDue <= 2) // a paisa or two of rounding at most
        assertEquals(plan[2].balanceAfter.toDouble(), s.principalLeft.toDouble(), 3.0)
    }

    @Test
    fun eventsFromEntries() {
        val borrowed = Loan(id = 2, accountId = 9, direction = LoanDirection.BORROWED, startDate = jan1)
        val txns = listOf(
            // Borrowed ₹5,000 into the bank (account 1), paid ₹2,000 back, and ₹100 interest.
            Txn(id = 10, type = TxnType.TRANSFER, amount = rupees(5_000), date = jan1, categoryId = null, accountId = 9, toAccountId = 1, loanId = 2),
            Txn(id = 11, type = TxnType.TRANSFER, amount = rupees(2_000), date = jan1.plusMonths(1), categoryId = null, accountId = 1, toAccountId = 9, loanId = 2),
            Txn(id = 12, type = TxnType.EXPENSE, amount = rupees(100), date = jan1.plusMonths(1), categoryId = 5, accountId = 1, loanId = 2),
        )
        val events = LoanMath.events(borrowed, opening = -rupees(1_000), txns = txns)
        assertEquals(listOf(LoanEventKind.GIVEN, LoanEventKind.GIVEN, LoanEventKind.REPAID, LoanEventKind.INTEREST), events.map { it.kind })
        val s = LoanMath.summary(borrowed, events, jan1.plusMonths(2))
        assertEquals(rupees(6_000), s.given)
        assertEquals(rupees(4_000), s.principalLeft)
    }

    @Test
    fun paymentsPayInterestFirst() {
        assertEquals(rupees(200) to rupees(800), LoanMath.split(rupees(1_000), rupees(200)))
        assertEquals(rupees(150) to 0L, LoanMath.split(rupees(150), rupees(200)))
        assertEquals(0L to rupees(500), LoanMath.split(rupees(500), 0))
    }
}
