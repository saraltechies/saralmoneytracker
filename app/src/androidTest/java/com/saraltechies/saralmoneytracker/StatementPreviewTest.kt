package com.saraltechies.saralmoneytracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.data.PersonLoans
import com.saraltechies.saralmoneytracker.model.Pledge
import com.saraltechies.saralmoneytracker.model.PledgeKind
import com.saraltechies.saralmoneytracker.data.PersonLoans
import com.saraltechies.saralmoneytracker.model.Pledge
import com.saraltechies.saralmoneytracker.model.PledgeKind
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.Loan
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEvent
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.Statements
import com.saraltechies.saralmoneytracker.util.StatementPdf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Draws sample loan statements (made-up people and numbers) so the PDF layout can be checked by eye. CI pulls the
 * files from the emulator; see .github/workflows/statement-preview.yml.
 */
@RunWith(AndroidJUnit4::class)
class StatementPreviewTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val me = Contact(
        name = "Sharma Traders", kind = ContactKind.SELF, phone = "+91 90000 12345", email = "accounts@sharmatraders.example",
        address = "Shop 4, Station Road, Moradabad, Uttar Pradesh 244001", taxId = "09ABCDE1234F1Z5", bankName = "State Bank of India",
        bankAccount = "30012345678", ifsc = "SBIN0001234", upi = "sharmatraders@sbi",
    )

    private fun write(name: String, item: LoanItem, from: LocalDate, to: LocalDate) {
        val dir = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }
        val file = File(dir, name)
        val data = Statements.build(item.loan, item.events, from, to, mapOf(1L to "SBI Current", 2L to "Cash"))
        file.outputStream().use { StatementPdf(context, Currencies.of("INR")).write(item, me, data, StatementPdf.Options(includePlan = true, includeQr = true), it) }
        assertTrue(file.length() > 1000)
    }

    @Test
    fun emiLoanToAPerson() {
        val start = LocalDate.of(2026, 1, 10)
        val emi = LoanMath.emi(20_000_000, 0.015, 12)
        val loan = Loan(
            id = 1, accountId = 9, direction = LoanDirection.LENT, startDate = start, rate = 150, rateUnit = RateUnit.MONTH,
            plan = LoanPlan.MONTHLY, installments = 12, installment = emi,
        )
        val plan = LoanMath.schedule(20_000_000, 0.015, 12, emi, start)
        val events = mutableListOf(LoanEvent(start, LoanEventKind.GIVEN, 20_000_000, txnId = 1))
        plan.take(7).forEach {
            events += LoanEvent(it.date, LoanEventKind.INTEREST, it.interest, txnId = 2, note = "EMI ${it.number}")
            events += LoanEvent(it.date, LoanEventKind.REPAID, it.principal, txnId = 2, note = "EMI ${it.number}")
        }
        val contact = Contact(
            name = "Rakesh Verma", phone = "+91 98765 00000", email = "rakesh.verma@example.com",
            address = "22 Civil Lines, Bareilly", taxId = "ABCPV1234K", reference = "Agreement dated 10 Jan 2026",
        )
        val today = LocalDate.of(2026, 10, 1)
        val item = LoanItem(loan, contact.name, events, LoanMath.summary(loan, events, today), emptyList(), contact)
        write("emi-loan.pdf", item, start, today)
    }

    @Test
    fun businessLoanFromABank() {
        val start = LocalDate.of(2025, 6, 1)
        val loan = Loan(id = 2, accountId = 8, direction = LoanDirection.BORROWED, startDate = start, rate = 1150, rateUnit = RateUnit.YEAR)
        val events = listOf(
            LoanEvent(start, LoanEventKind.GIVEN, 50_000_000, txnId = 1),
            LoanEvent(LocalDate.of(2025, 9, 1), LoanEventKind.INTEREST, 1_437_500, txnId = 1),
            LoanEvent(LocalDate.of(2025, 12, 1), LoanEventKind.REPAID, 10_000_000, txnId = 1),
            LoanEvent(LocalDate.of(2025, 12, 1), LoanEventKind.INTEREST, 1_437_500, txnId = 1),
        )
        val bank = Contact(
            name = "Punjab National Bank", kind = ContactKind.BANK, address = "Main Branch, Civil Lines, Moradabad",
            bankAccount = "LN0099887766", ifsc = "PUNB0123400", reference = "Business term loan LN0099887766",
        )
        val today = LocalDate.of(2026, 3, 31)
        val item = LoanItem(loan, bank.name, events, LoanMath.summary(loan, events, today), emptyList(), bank)
        write("bank-loan.pdf", item, LocalDate.of(2025, 4, 1), today)
    }

    /** Girvi: one person pledged gold twice and silver once, each at its own rate; one loan paid off, its ring returned. */
    @Test
    fun girviAccountOfOnePerson() {
        val today = LocalDate.of(2026, 10, 1)
        val sunita = Contact(id = 7, name = "Sunita Devi", phone = "+91 98765 43210", address = "Ram Ganga Vihar, Moradabad")
        fun item(id: Long, start: LocalDate, amount: Long, rate: Long, pledge: Pledge, extra: List<LoanEvent> = emptyList(), closed: Boolean = false): LoanItem {
            val loan = Loan(
                id = id, accountId = 100 + id, direction = LoanDirection.LENT, startDate = start, rate = rate, rateUnit = RateUnit.YEAR,
                contactId = sunita.id, pledge = pledge, closed = closed,
            )
            val events = listOf(LoanEvent(start, LoanEventKind.GIVEN, amount, txnId = id)) + extra
            return LoanItem(loan, sunita.name, events, LoanMath.summary(loan, events, today), emptyList(), sunita)
        }
        val ring = Pledge(PledgeKind.GOLD, items = "1 ring", weightMg = 4_100, purity = "22K", value = 2_800_000, returnedOn = LocalDate.of(2026, 5, 2))
        val person = PersonLoans(
            "c7", sunita.name, sunita,
            listOf(
                item(
                    1, LocalDate.of(2025, 11, 2), 2_000_000, 1500, ring, closed = true,
                    extra = listOf(LoanEvent(LocalDate.of(2026, 5, 2), LoanEventKind.INTEREST, 150_000), LoanEvent(LocalDate.of(2026, 5, 2), LoanEventKind.REPAID, 2_000_000)),
                ),
                item(
                    2, LocalDate.of(2026, 2, 1), 5_000_000, 1200,
                    Pledge(PledgeKind.GOLD, items = "1 chain", weightMg = 10_250, purity = "22K", value = 7_000_000),
                    extra = listOf(LoanEvent(LocalDate.of(2026, 7, 1), LoanEventKind.INTEREST, 250_000)),
                ),
                item(3, LocalDate.of(2026, 6, 1), 6_000_000, 1800, Pledge(PledgeKind.GOLD, items = "2 bangles", weightMg = 15_000, purity = "22K", value = 10_000_000)),
                item(4, LocalDate.of(2026, 8, 1), 1_200_000, 1000, Pledge(PledgeKind.SILVER, items = "Pair of anklets", weightMg = 200_000, purity = "92.5", value = 1_800_000)),
            ),
        )
        val dir = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }
        val file = File(dir, "girvi-account.pdf")
        file.outputStream().use { StatementPdf(context, Currencies.of("INR")).writeAccount(person, me, it, today) }
        assertTrue(file.length() > 1000)
        // And one of those loans on its own, with its security in the loan details.
        val chain = person.loans[1]
        write("girvi-loan.pdf", chain, chain.loan.startDate, today)
    }
}
