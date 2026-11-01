package com.saraltechies.saralmoneytracker

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.LoanTerms
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.setup.WHATS_NEW_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Takes the Play Store screenshots from the real app, filled with an invented shop (Sharma Traders) and invented
 * customers. Run on a 1080×1920 emulator by the "Store screenshots" workflow, which pulls the PNGs.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as SaralMoneyTrackerApp
    private val dir get() = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    private fun s(id: Int) = context.getString(id)
    private fun rupees(r: Long) = r * 100

    @Test
    fun storeScreenshots() {
        runBlocking { seed() }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            shot("1-home")

            click(s(R.string.add_entry))
            shot("2-add-entry")
            back()
            settle()

            click(s(R.string.tab_accounts))
            click(s(R.string.ledger_title))
            shot("3-credit-ledger")
            click("Anita General Store")
            shot("4-customer")
            back()
            back()
            settle()

            click(s(R.string.loans_title))
            click("Punjab National Bank")
            shot("5-loan-emi")
            back()
            back()
            settle()

            click(s(R.string.tab_stats))
            shot("6-stats")

            click(s(R.string.tab_home))
            // The cash-flow card: "By 30 Nov, about …".
            click(", about", substring = true)
            shot("7-cash-flow")
        }
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(1_200)
        compose.waitForIdle()
    }

    private fun click(text: String, substring: Boolean = false) {
        val matcher = hasText(text, substring = substring) and hasClickAction()
        compose.waitUntil(8_000) { compose.onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** An invented shop with a month of business: sales, purchases, bills, customers on credit, a bank loan. */
    private suspend fun seed() {
        val repo = app.repository
        val settings = app.settings
        repo.deleteAll()
        settings.setCurrency("INR")
        settings.setSetupDone()
        settings.setWhatsNewSeen(WHATS_NEW_VERSION)
        val today = LocalDate.now()
        val shop = repo.createBook("Sharma Traders", BookKind.BUSINESS)
        settings.setBook(shop)

        val accounts = repo.accounts(book = shop)
        val cash = accounts.first { it.type == AccountType.CASH }
        val bank = accounts.first { it.type == AccountType.BANK }
        repo.saveAccount(cash.copy(opening = rupees(18_500)))
        repo.saveAccount(bank.copy(opening = rupees(2_45_000)))
        val cats = repo.categories(book = shop)
        fun cat(icon: String, type: TxnType) = cats.first { it.icon == icon && it.kind == type }.id
        val sales = cat("store", TxnType.INCOME)
        val purchases = cat("store", TxnType.EXPENSE)
        val rent = cat("home", TxnType.EXPENSE)
        val power = cat("electricity", TxnType.EXPENSE)
        val wages = cat("work", TxnType.EXPENSE)
        val transport = cat("bus", TxnType.EXPENSE)
        val phone = cat("phone", TxnType.EXPENSE)

        repo.saveMyDetails(
            Contact(
                name = "Sharma Traders", kind = ContactKind.SELF, bookId = shop, address = "Shop 4, Station Road, Moradabad 244001",
                phone = "+91 90000 12345", taxId = "09ABCDE1234F1Z5", bankName = "State Bank of India", bankAccount = "30012345678",
                ifsc = "SBIN0001234", upi = "sharmatraders@sbi",
            ),
        )

        // This month so far: daily counter sales, stock, the shop's bills.
        val monthStart = today.withDayOfMonth(1)
        var d = monthStart
        var i = 0
        val daily = listOf(8_450L, 6_920L, 11_380L, 7_640L, 9_210L, 12_870L, 5_960L)
        while (!d.isAfter(today)) {
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = rupees(daily[i % daily.size]), date = d, categoryId = sales, accountId = if (i % 3 == 0) bank.id else cash.id, note = "Counter sales"))
            d = d.plusDays(1)
            i++
        }
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = rupees(42_600), date = monthStart, categoryId = purchases, accountId = bank.id, note = "Stock: rice, dal, oil"))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = rupees(15_000), date = monthStart, categoryId = rent, accountId = bank.id, note = "Shop rent"))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = rupees(2_340), date = monthStart, categoryId = power, accountId = bank.id, note = "Electricity bill"))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = rupees(1_200), date = today, categoryId = transport, accountId = cash.id, note = "Tempo for delivery"))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = rupees(399), date = today, categoryId = phone, accountId = bank.id, note = "Shop mobile recharge"))

        // Repeating: staff salary and rent, with a reminder 3 days before.
        val nextMonth = monthStart.plusMonths(1)
        repo.saveRecurring(
            Recurring(type = TxnType.EXPENSE, amount = rupees(12_000), categoryId = wages, accountId = bank.id, note = "Staff salary", frequency = Frequency.MONTHLY, startDate = today.plusDays(4), nextDate = today.plusDays(4), remindDays = 3),
        )
        repo.saveRecurring(
            Recurring(type = TxnType.EXPENSE, amount = rupees(15_000), categoryId = rent, accountId = bank.id, note = "Shop rent", frequency = Frequency.MONTHLY, startDate = nextMonth, nextDate = nextMonth, remindDays = 3),
        )

        // Customers on credit, and a supplier.
        val anita = repo.createParty(Contact(name = "Anita General Store", phone = "98765 11111"), PartyRole.CUSTOMER, shop, opening = rupees(1_250), creditDays = 15)
        val ramesh = repo.createParty(Contact(name = "Ramesh Kirana", phone = "98765 22222"), PartyRole.CUSTOMER, shop, opening = 0, creditDays = 15)
        val gupta = repo.createParty(Contact(name = "Gupta Wholesale", phone = "98765 33333"), PartyRole.SUPPLIER, shop, opening = 0, creditDays = 30)
        val pa = repo.partyItem(anita)!!.party
        val pr = repo.partyItem(ramesh)!!.party
        val pg = repo.partyItem(gupta)!!.party
        repo.savePartyEntry(pa, gave = true, amount = rupees(4_860), date = today.minusDays(24), accountId = null, categoryId = sales, note = "Bill 1031")
        repo.savePartyEntry(pa, gave = false, amount = rupees(3_000), date = today.minusDays(12), accountId = cash.id, categoryId = null, note = "Cash")
        repo.savePartyEntry(pa, gave = true, amount = rupees(2_740), date = today.minusDays(5), accountId = null, categoryId = sales, note = "Bill 1077")
        repo.savePartyEntry(pr, gave = true, amount = rupees(6_350), date = today.minusDays(8), accountId = null, categoryId = sales, note = "Bill 1062")
        repo.savePartyEntry(pr, gave = false, amount = rupees(2_000), date = today.minusDays(2), accountId = bank.id, categoryId = null, note = "UPI")
        repo.savePartyEntry(pg, gave = false, amount = rupees(28_400), date = today.minusDays(10), accountId = null, categoryId = purchases, note = "Invoice GW-882")
        repo.savePartyEntry(pg, gave = true, amount = rupees(15_000), date = today.minusDays(3), accountId = bank.id, categoryId = null, note = "NEFT")

        // A business term loan from a bank, paid by EMI.
        val start = today.minusMonths(7)
        val emi = LoanMath.emi(rupees(3_00_000), LoanMath.monthlyRate(1150, RateUnit.YEAR), 36)
        val loanId = repo.createLoan(
            LoanTerms(
                name = "Punjab National Bank", direction = LoanDirection.BORROWED, startDate = start, rate = 1150, rateUnit = RateUnit.YEAR,
                plan = LoanPlan.MONTHLY, installments = 36, installment = emi, note = "Business term loan", bookId = shop,
            ),
            rupees(3_00_000), bank.id,
            Contact(name = "Punjab National Bank", kind = ContactKind.BANK, reference = "TL/2026/4471", address = "Civil Lines, Moradabad"),
        )
        val loan = repo.loanItem(loanId)!!.loan
        LoanMath.schedule(rupees(3_00_000), LoanMath.monthlyRate(1150, RateUnit.YEAR), 36, emi, start).take(7).filter { !it.date.isAfter(today) }.forEach { row ->
            repo.addLoanEntries(loan, listOf(LoanEventKind.INTEREST to row.interest, LoanEventKind.REPAID to row.principal), row.date, bank.id, "EMI ${row.number}")
        }
        repo.processRecurring()
    }
}
