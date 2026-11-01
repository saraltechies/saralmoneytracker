package com.saraltechies.saralmoneytracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.MoneyDb
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.util.PartyStatementPdf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * The credit ledger end to end on a real database (a sale on credit, a payment, an old balance), its PDF
 * statement, and a picture of the home-screen widget, for CI to pull and check by eye.
 */
@RunWith(AndroidJUnit4::class)
class LedgerAndWidgetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir get() = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }

    @Test
    fun creditLedgerFlow(): Unit = runBlocking {
        context.deleteDatabase(MoneyDb.NAME)
        val repo = MoneyRepository(context)
        val shop = repo.createBook("Sharma Traders", BookKind.BUSINESS)
        val cash = repo.accounts(book = shop).first { it.name.contains("cash", true) }
        repo.saveMyDetails(
            Contact(
                name = "Sharma Traders", kind = ContactKind.SELF, bookId = shop, address = "Shop 4, Station Road, Moradabad 244001",
                phone = "+91 90000 12345", taxId = "09ABCDE1234F1Z5", bankName = "State Bank of India", bankAccount = "30012345678",
                ifsc = "SBIN0001234", upi = "sharmatraders@sbi",
            ),
        )
        val partyId = repo.createParty(Contact(name = "Ramesh Kirana", phone = "98765 00000"), PartyRole.CUSTOMER, shop, opening = 75_000)
        val party = repo.partyItem(partyId)!!.party
        val sales = repo.goodsCategory(shop, sale = true)!!
        val d = LocalDate.of(2026, 11, 2)
        repo.savePartyEntry(party, gave = true, amount = 245_000, date = d, accountId = null, categoryId = sales.id, note = "Bill 1042: rice, oil")
        repo.savePartyEntry(party, gave = false, amount = 200_000, date = d.plusDays(3), accountId = cash.id, categoryId = null, note = "Cash")
        repo.savePartyEntry(party, gave = true, amount = 118_500, date = d.plusDays(6), accountId = null, categoryId = sales.id, note = "Bill 1077")

        val item = repo.partyItem(partyId)!!
        assertEquals(75_000L + 245_000 - 200_000 + 118_500, item.balance)
        // Sales count once; the payment isn't income.
        val report = repo.bookReport(d.withDayOfMonth(1), d.withDayOfMonth(30), book = shop)
        assertEquals(363_500L, report.totalIncome)
        assertEquals(item.balance, report.customersOwe)
        assertTrue(repo.txns(book = shop).any { it.type == TxnType.TRANSFER })
        // Hidden from account pickers, counted in balances.
        assertTrue(repo.accounts(book = Books.ALL).none { it.id == party.accountId })

        File(dir, "credit-ledger-statement.pdf").outputStream().use {
            PartyStatementPdf(context, Currencies.of("INR")).write(item, repo.myDetails(shop), repo.accounts(book = Books.ALL).associate { a -> a.id to a.name }, it)
        }
        context.deleteDatabase(MoneyDb.NAME)
    }

    @Test
    fun widgetLooksRight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val views = RemoteViews(context.packageName, R.layout.widget_money)
            views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_spent_today_in, "Sharma Traders"))
            views.setTextViewText(R.id.widget_today, "₹1,250")
            views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_this_month, "₹38,420"))
            val parent = FrameLayout(context)
            val view = views.apply(context, parent)
            val density = context.resources.displayMetrics.density
            val w = (330 * density).toInt()
            val h = (84 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(dir, "widget.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(File(dir, "widget.png").length() > 500)
    }
}
