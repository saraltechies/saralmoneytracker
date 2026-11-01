package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun rupees(r: Long) = r * 100

    private val loan = Loan(id = 1, accountId = 9, direction = LoanDirection.LENT, startDate = d("2026-01-01"), rate = 200, rateUnit = RateUnit.MONTH)
    private val events = listOf(
        LoanEvent(d("2026-01-01"), LoanEventKind.GIVEN, rupees(10_000), txnId = 1),
        LoanEvent(d("2026-02-01"), LoanEventKind.INTEREST, rupees(200), txnId = 2),
        LoanEvent(d("2026-04-01"), LoanEventKind.INTEREST, rupees(400), txnId = 3),
        LoanEvent(d("2026-04-01"), LoanEventKind.REPAID, rupees(5_000), txnId = 4),
    )

    @Test
    fun wholeLoanHasRunningBalances() {
        val s = Statements.build(loan, events, d("2026-01-01"), d("2026-05-01"), mapOf(1L to "Cash", 4L to "HDFC"))
        assertNull(s.opening)
        assertEquals(4, s.rows.size)
        assertEquals("Cash", s.rows[0].account)
        assertEquals(rupees(10_000), s.rows[0].principalLeft)
        assertEquals(0L, s.rows[0].interestDue)
        // After paying January's interest on 1 Feb, nothing is due.
        assertEquals(0L, s.rows[1].interestDue)
        // By 1 April two more months ran (400) and were paid.
        assertEquals(0L, s.rows[2].interestDue)
        assertEquals(rupees(10_000), s.rows[2].principalLeft)
        // The repayment on the same day comes after the interest.
        assertEquals(rupees(5_000), s.rows[3].principalLeft)
        assertEquals("HDFC", s.rows[3].account)
        // April on the 5,000 left: 100.
        assertEquals(rupees(100), s.closing.interestDue)
        assertEquals(rupees(10_000), s.givenInPeriod)
        assertEquals(rupees(5_000), s.repaidInPeriod)
        assertEquals(rupees(600), s.interestPaidInPeriod)
    }

    @Test
    fun laterPeriodBringsBalanceForward() {
        val s = Statements.build(loan, events, d("2026-03-01"), d("2026-04-30"))
        val opening = s.opening
        assertNotNull(opening)
        assertEquals(rupees(10_000), opening!!.principalLeft)
        // February's interest was not paid by 28 Feb: 1 Feb–28 Feb is 27/28 of a month.
        assertTrue(opening.interestDue in rupees(190)..rupees(200))
        assertEquals(2, s.rows.size)
        assertEquals(0L, s.givenInPeriod)
    }

    @Test
    fun periodBeforeTheLoanStartsAtTheLoan() {
        val s = Statements.build(loan, events, d("2025-04-01"), d("2026-01-31"))
        assertEquals(d("2026-01-01"), s.from)
        assertNull(s.opening)
        assertEquals(1, s.rows.size)
    }

    @Test
    fun financialYears() {
        assertEquals(d("2026-04-01") to d("2027-03-31"), Statements.financialYear(d("2026-11-01")))
        assertEquals(d("2025-04-01") to d("2026-03-31"), Statements.financialYear(d("2026-03-31")))
        assertEquals(d("2026-04-01") to d("2027-03-31"), Statements.financialYear(d("2026-04-01")))
    }

    @Test
    fun contactChecks() {
        assertTrue(Contact.isUpiId("ravi.kumar@okhdfcbank"))
        assertTrue(Contact.isUpiId("9876543210@ybl"))
        assertFalse(Contact.isUpiId("ravi"))
        assertFalse(Contact.isUpiId("ravi@"))
        assertTrue(Contact.isIfsc("HDFC0001234"))
        assertFalse(Contact.isIfsc("HDFC1001234"))
        assertTrue(Contact.isEmail("a.b@example.co.in"))
        assertFalse(Contact.isEmail("a@b"))
        assertFalse(Contact(name = "Ravi").hasDetails)
        assertTrue(Contact(name = "Ravi", phone = "98765 43210").hasDetails)
        assertEquals("ABCDE1234F", Contact(name = " x ", taxId = " abcde1234f ").trimmed().taxId)
    }

    @Test
    fun upiLink() {
        assertEquals(
            "upi://pay?pa=shop%40okicici&pn=Saral%20Techies&am=1250.50&cu=INR&tn=Loan%20repayment%20Ravi",
            UpiLink.build("shop@okicici", "Saral Techies", 125_050, "Loan repayment Ravi"),
        )
        assertEquals("upi://pay?pa=a%40b&pn=A&cu=INR", UpiLink.build("a@b", "A", 0, ""))
    }
}
