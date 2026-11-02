package com.saraltechies.saralmoneytracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.LoanTerms
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Changing a loan's dates, the way the screens do, on a real database: the interest has to follow. */
@RunWith(AndroidJUnit4::class)
class LoanDatesTest {

    private val repo = MoneyRepository(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    private val today = LocalDate.now()
    private fun rupees(r: Long) = r * 100
    private val cash get() = repo.accounts(book = Books.ALL).first { it.type == AccountType.CASH }.id

    private fun terms(name: String, start: LocalDate) = LoanTerms(
        name = name, direction = LoanDirection.LENT, startDate = start, rate = 1200, rateUnit = RateUnit.YEAR,
        plan = LoanPlan.FLEXIBLE, installments = 0, installment = 0, note = "",
    )

    /** ₹10,000 at 12% a year is ₹100 a month. */
    private suspend fun loan(name: String, monthsAgo: Long, fromAccounts: Boolean = true): Long =
        repo.createLoan(terms(name, today.minusMonths(monthsAgo)), rupees(10_000), if (fromAccounts) cash else null, Contact(name = name))

    private fun interest(id: Long) = repo.loanItem(id)!!.summary.interestAccrued

    @Test
    fun movingTheGivenEntryEarlierAddsInterest() = runBlocking {
        val id = loan("Entry earlier", 2)
        assertEquals(rupees(200), interest(id))
        val item = repo.loanItem(id)!!
        val given = item.txns.single()
        repo.updateLoanEntry(item.loan, given, LoanEventKind.GIVEN, given.amount, today.minusMonths(5), cash, "")
        assertEquals(rupees(500), interest(id))
        assertEquals(today.minusMonths(5), repo.loanItem(id)!!.loan.startDate)
    }

    @Test
    fun movingTheGivenEntryLaterLowersInterest() = runBlocking {
        val id = loan("Entry later", 5)
        val item = repo.loanItem(id)!!
        val given = item.txns.single()
        repo.updateLoanEntry(item.loan, given, LoanEventKind.GIVEN, given.amount, today.minusMonths(2), cash, "")
        assertEquals(rupees(200), interest(id))
        assertEquals(today.minusMonths(2), repo.loanItem(id)!!.loan.startDate)
    }

    @Test
    fun movingTheStartDateEarlierAddsInterest() = runBlocking {
        val id = loan("Start earlier", 2)
        repo.updateLoan(repo.loanItem(id)!!.loan, terms("Start earlier", today.minusMonths(5)), repo.loanItem(id)!!.contact!!)
        assertEquals(rupees(500), interest(id))
        assertEquals(today.minusMonths(5), repo.loanItem(id)!!.txns.single().date)
    }

    @Test
    fun movingTheStartDateLaterLowersInterest() = runBlocking {
        val id = loan("Start later", 5)
        repo.updateLoan(repo.loanItem(id)!!.loan, terms("Start later", today.minusMonths(2)), repo.loanItem(id)!!.contact!!)
        assertEquals(rupees(200), interest(id))
        assertEquals(today.minusMonths(2), repo.loanItem(id)!!.txns.single().date)
    }

    @Test
    fun anOlderLoanFollowsItsStartDate() = runBlocking {
        val id = loan("Older loan", 2, fromAccounts = false)
        repo.updateLoan(repo.loanItem(id)!!.loan, terms("Older loan", today.minusMonths(5)), repo.loanItem(id)!!.contact!!)
        assertEquals(rupees(500), interest(id))
    }

    @Test
    fun movingARepaymentChangesInterest() = runBlocking {
        val id = loan("Repayment", 6)
        val item = repo.loanItem(id)!!
        repo.addLoanEntries(item.loan, listOf(LoanEventKind.REPAID to rupees(5_000)), today.minusMonths(3), cash, "")
        // 3 months on 10,000 and 3 on 5,000.
        assertEquals(rupees(450), interest(id))
        val after = repo.loanItem(id)!!
        val repaid = after.txns.first { it.date == today.minusMonths(3) }
        repo.updateLoanEntry(after.loan, repaid, LoanEventKind.REPAID, repaid.amount, today.minusMonths(1), cash, "")
        // 5 months on 10,000 and 1 on 5,000.
        assertEquals(rupees(550), interest(id))
    }

    @Test
    fun deletingTheFirstAmountMovesTheStart() = runBlocking {
        val id = loan("Delete first", 6)
        val item = repo.loanItem(id)!!
        repo.addLoanEntries(item.loan, listOf(LoanEventKind.GIVEN to rupees(10_000)), today.minusMonths(2), cash, "More")
        val first = item.txns.single()
        repo.deleteTxn(first.id)
        assertEquals(today.minusMonths(2), repo.loanItem(id)!!.loan.startDate)
        assertEquals(rupees(200), interest(id))
        repo.restoreTxn(first)
        assertEquals(today.minusMonths(6), repo.loanItem(id)!!.loan.startDate)
    }
}
