package com.saraltechies.saralmoneytracker

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Bill
import com.saraltechies.saralmoneytracker.model.BillItem
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.ui.setup.WHATS_NEW_VERSION
import com.saraltechies.saralmoneytracker.util.BillPdf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Bills on a real database and through the real screens: money counted once, balances right, and a PDF to look at. */
@RunWith(AndroidJUnit4::class)
class BillsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as SaralMoneyTrackerApp
    private val repo get() = app.repository
    private val today = LocalDate.now()
    private fun s(id: Int) = context.getString(id)
    private fun rupees(r: Long) = r * 100
    private val dir get() = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }

    private suspend fun fresh() {
        repo.deleteAll()
        app.settings.setCurrency("INR")
        app.settings.setSetupDone()
        app.settings.setWhatsNewSeen(WHATS_NEW_VERSION)
        app.settings.setBook(Books.ALL)
    }

    private val cash get() = repo.accounts(book = Books.ALL).first { it.type == AccountType.CASH }.id

    private fun income() = repo.txns(book = Books.ALL).filter { it.type == TxnType.INCOME }.sumOf { it.amount }

    private fun balance(account: Long) = repo.balances()[account] ?: 0

    @Test
    fun aCustomersBillGoesThroughTheirAccount(): Unit = runBlocking {
        fresh()
        val partyId = repo.createParty(Contact(name = "Anita"), PartyRole.CUSTOMER, Books.PERSONAL_ID, 0)
        val party = repo.partyItem(partyId)!!.party
        val before = balance(cash)
        val id = repo.createBill(
            Bill(number = "1", date = today, partyId = partyId, items = listOf(BillItem("Rice", 2000, "kg", rupees(500), 500))),
            received = rupees(300), receivedIn = cash,
        )
        // ₹1,000 plus 5% GST, ₹300 of it received.
        var view = repo.billView(id)!!
        assertEquals(rupees(1050), view.totals.total)
        assertEquals(rupees(300), view.payment.paid)
        assertEquals(rupees(750), repo.partyItem(partyId)!!.balance)
        assertEquals(before + rupees(300), balance(cash))
        // The sale counts once, and the payment isn't income.
        assertEquals(rupees(1050), income())
        val cats = repo.categories(book = Books.ALL).associateBy { it.id }
        val names = repo.txns(book = Books.ALL).filter { it.type == TxnType.INCOME }.map { cats[it.categoryId]?.name }
        assertTrue(s(R.string.bill_category_gst) in names)

        // A "You got" in the ledger, without a bill, settles it too.
        repo.savePartyEntry(party, gave = false, amount = rupees(750), date = today, accountId = cash, categoryId = null, note = "")
        view = repo.billView(id)!!
        assertTrue(view.payment.isPaid)
        assertEquals(0L, repo.partyItem(partyId)!!.balance)

        // Changing the bill replaces the sale, not the payment.
        repo.updateBill(view.bill.copy(items = listOf(BillItem("Rice", 1000, "kg", rupees(500), 500))))
        assertEquals(rupees(525), income())
        assertEquals(rupees(525), repo.billView(id)!!.totals.total)
        assertEquals(-rupees(525), repo.partyItem(partyId)!!.balance)

        // Deleting it takes its sale and payment back.
        repo.deleteBill(id)
        assertEquals(0L, income())
        assertEquals(-rupees(750), repo.partyItem(partyId)!!.balance)
        assertTrue(repo.billViews(Books.ALL).isEmpty())
    }

    @Test
    fun aWalkInSaleIsPaidIntoAnAccount(): Unit = runBlocking {
        fresh()
        val before = balance(cash)
        val id = repo.createBill(Bill(number = "7", date = today, customerName = "Ravi", accountId = cash, discount = rupees(10), items = listOf(BillItem("Soap", 2000, "", rupees(30)))))
        val view = repo.billView(id)!!
        assertEquals(rupees(50), view.totals.total)
        assertTrue(view.payment.isPaid)
        assertEquals("Ravi", view.customerName)
        assertEquals(before + rupees(50), balance(cash))
        assertEquals("8", repo.nextBillNumber(Books.PERSONAL_ID))
        assertTrue(repo.billNumberTaken(Books.PERSONAL_ID, "7"))
        assertFalse(repo.billNumberTaken(Books.PERSONAL_ID, "7", exceptId = id))
    }

    @Test
    fun deletingACustomerDeletesTheirBills(): Unit = runBlocking {
        fresh()
        val partyId = repo.createParty(Contact(name = "Gone"), PartyRole.CUSTOMER, Books.PERSONAL_ID, 0)
        repo.createBill(Bill(number = "1", date = today, partyId = partyId, items = listOf(BillItem("Tea", 1000, "", rupees(10)))))
        repo.deleteParty(repo.partyItem(partyId)!!.party)
        assertTrue(repo.billViews(Books.ALL).isEmpty())
        assertEquals(0L, income())
    }

    @Test
    fun billPdfsToLookAt(): Unit = runBlocking {
        fresh()
        val me = Contact(
            name = "Sharma Traders", kind = ContactKind.SELF, phone = "+91 90000 12345", address = "Shop 4, Station Road, Moradabad, Uttar Pradesh 244001",
            taxId = "09ABCDE1234F1Z5", bankName = "State Bank of India", bankAccount = "30012345678", ifsc = "SBIN0001234", upi = "sharmatraders@sbi",
        )
        val partyId = repo.createParty(Contact(name = "Anita General Store", phone = "+91 98765 00000", address = "Civil Lines, Bareilly", taxId = "09ABCPV1234K1Z2"), PartyRole.CUSTOMER, Books.PERSONAL_ID, 0)
        val gst = repo.createBill(
            Bill(
                number = "INV-0042", date = today, partyId = partyId, discount = rupees(100), note = "Goods once sold can be exchanged within 7 days.",
                items = listOf(
                    BillItem("Basmati rice, 25 kg bag", 4000, "bag", rupees(1850), 500, "1006"),
                    BillItem("Refined oil, 15 L tin", 2000, "tin", rupees(2150), 500, "1512"),
                    BillItem("Detergent powder", 12500, "kg", rupees(95), 1800, "3402"),
                    BillItem("Steel containers, set of 3", 1000, "set", rupees(640), 1800, "7323"),
                ),
            ),
            received = rupees(5000), receivedIn = cash,
        )
        val plain = repo.createBill(
            Bill(number = "43", date = today, customerName = "Ravi Kumar", accountId = cash, items = listOf(BillItem("Tea, 250 g", 3000, "pack", rupees(120)), BillItem("Sugar", 2500, "kg", rupees(44)))),
        )
        val currency = Currencies.of("INR")
        for ((id, name) in listOf(gst to "bill-gst.pdf", plain to "bill-plain.pdf")) {
            val file = File(dir, name)
            file.outputStream().use { BillPdf(context, currency).write(repo.billView(id)!!, me, it) }
            assertTrue(file.length() > 1000)
        }
    }

    /** Make a bill for a new customer by tapping through the app, then take the rest of the payment. */
    @Test
    fun makeABillOnScreen() {
        runBlocking { fresh() }
        ActivityScenario.launch(MainActivity::class.java).use {
            click(s(R.string.tab_accounts))
            click(s(R.string.bills_title))
            shot("bill-ui-1-empty")
            click(s(R.string.bill_new))
            click(s(R.string.bill_customer))
            click(s(R.string.bill_new_customer))
            type(s(R.string.ledger_customer_name), "Meena Stores")

            click(s(R.string.bill_add_item))
            type(s(R.string.bill_item_name), "Rice")
            type(s(R.string.bill_quantity), "2.5")
            type(s(R.string.bill_rate), "60")
            click(s(R.string.save))
            see("₹150")
            shot("bill-ui-2-form")

            click(s(R.string.bill_save))
            see(s(R.string.bill_title).format("1"))
            see("₹150")
            shot("bill-ui-3-detail")

            click(s(R.string.bill_record_payment))
            type(s(R.string.amount), "50")
            click(s(R.string.save))
            see("₹100")
            shot("bill-ui-4-part-paid")
        }
        val view = repo.billViews(Books.ALL).single()
        assertEquals(rupees(150), view.totals.total)
        assertEquals(rupees(50), view.payment.paid)
        assertEquals("Meena Stores", view.customerName)
        assertEquals(rupees(100), repo.partyItem(view.bill.partyId!!)!!.balance)
    }

    /** The credit ledger is one tap from Home, like loans, even with a long book name on a small phone. */
    @Test
    fun theLedgerOpensFromHome() {
        runBlocking {
            fresh()
            val shop = repo.createBook("Sharma General Traders", com.saraltechies.saralmoneytracker.model.BookKind.BUSINESS)
            app.settings.setBook(shop)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            wait(hasContentDescription(s(R.string.loans_title)))
            shot("home-top-bar")
            click(s(R.string.ledger_title))
            see(s(R.string.ledger_title))
            wait(hasText(s(R.string.ledger_customers), substring = true))
            shot("home-ledger-opened")
        }
    }

    private fun see(text: String) = wait(hasText(text))

    private fun click(text: String) {
        // A floating button keeps its label in a child node.
        val matcher = (hasText(text) or hasContentDescription(text) or androidx.compose.ui.test.hasAnyChild(hasText(text))) and hasClickAction()
        wait(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private fun type(label: String, value: String) {
        val field = hasSetTextAction() and hasText(label)
        wait(field)
        compose.onAllNodes(field).onFirst().performTextReplacement(value)
        compose.waitForIdle()
    }

    private fun wait(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("bill-failed")
            val dump = compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n\n") { i ->
                runCatching { compose.onAllNodes(isRoot())[i].printToString(maxDepth = 30) }.getOrElse { it.toString() }
            }
            File(dir, "bill-failed.txt").writeText("Looking for: " + matcher.description + "\n\n" + dump)
            throw e
        }
    }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
