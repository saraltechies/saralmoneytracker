package com.saraltechies.saralmoneytracker

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralmoneytracker.data.MoneyDb
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.util.ReportPdf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * A phone with data from version 1.0 (database version 1) updates straight to books, and a business book's profit
 * and loss report is drawn (pulled by CI with the statement previews).
 */
@RunWith(AndroidJUnit4::class)
class UpgradeAndReportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun version1DataMovesToPersonalBook(): Unit = runBlocking {
        context.deleteDatabase(MoneyDb.NAME)
        // The 1.0 schema, exactly as it shipped.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(MoneyDb.NAME).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, opening INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL, budget INTEGER, archived INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE txns (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, day INTEGER NOT NULL, category_id INTEGER, account_id INTEGER NOT NULL, to_account_id INTEGER, note TEXT NOT NULL DEFAULT '', recurring_id INTEGER, created INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX txns_day ON txns(day)")
            db.execSQL("CREATE TABLE recurring (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, category_id INTEGER, account_id INTEGER NOT NULL, to_account_id INTEGER, note TEXT NOT NULL DEFAULT '', frequency TEXT NOT NULL, start_day INTEGER NOT NULL, next_day INTEGER NOT NULL, end_day INTEGER, active INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("INSERT INTO accounts (name, type, opening) VALUES ('Cash', 'CASH', 500000), ('SBI', 'BANK', 10000000)")
            db.execSQL("INSERT INTO categories (name, kind, icon, color, budget) VALUES ('Food', 'EXPENSE', 'restaurant', -1, 600000), ('Salary', 'INCOME', 'salary', -1, NULL)")
            val day = LocalDate.of(2026, 10, 5).toEpochDay()
            db.execSQL("INSERT INTO txns (type, amount, day, category_id, account_id, created) VALUES ('EXPENSE', 25000, $day, 1, 1, 1), ('INCOME', 7500000, $day, 2, 2, 2)")
            db.version = 1
        }

        val repo = MoneyRepository(context)
        val books = repo.books()
        assertEquals(1, books.size)
        assertEquals(Books.PERSONAL_ID, books.single().id)
        assertEquals(listOf(Books.PERSONAL_ID), repo.accounts(book = Books.ALL).map { it.bookId }.distinct())
        assertEquals(2, repo.txns(book = Books.PERSONAL_ID).size)
        assertEquals(600000L, repo.categories(book = Books.PERSONAL_ID).single { it.name == "Food" }.budget)
        val balances = repo.balances()
        assertEquals(475000L, balances[1])

        // Add a business: it gets its own categories and accounts, and its report counts only its entries.
        val shop = repo.createBook("Sharma Traders", BookKind.BUSINESS)
        val shopAccounts = repo.accounts(book = shop)
        val shopCats = repo.categories(book = shop)
        assertEquals(2, shopAccounts.size)
        assertTrue(shopCats.any { it.kind == TxnType.INCOME } && shopCats.any { it.kind == TxnType.EXPENSE })
        val sales = shopCats.first { it.kind == TxnType.INCOME }
        val purchases = shopCats.first { it.kind == TxnType.EXPENSE }
        val rent = shopCats.first { it.kind == TxnType.EXPENSE && it.icon == "home" }
        val bank = shopAccounts.first { it.type == AccountType.BANK }
        val apr = LocalDate.of(2026, 4, 1)
        repo.saveTxn(Txn(type = TxnType.TRANSFER, amount = 5_000_000, date = apr.plusDays(1), categoryId = null, accountId = 2, toAccountId = bank.id))
        repo.saveTxn(Txn(type = TxnType.INCOME, amount = 18_250_000, date = apr.plusDays(9), categoryId = sales.id, accountId = bank.id))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 9_640_000, date = apr.plusDays(11), categoryId = purchases.id, accountId = bank.id))
        repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1_500_000, date = apr.plusDays(4), categoryId = rent.id, accountId = 2, note = "Shop rent"))
        repo.saveTxn(Txn(type = TxnType.TRANSFER, amount = 2_000_000, date = apr.plusDays(29), categoryId = null, accountId = bank.id, toAccountId = 1))
        repo.saveMyDetails(
            Contact(
                name = "Sharma Traders", kind = ContactKind.SELF, bookId = shop, address = "Shop 4, Station Road, Moradabad 244001",
                phone = "+91 90000 12345", taxId = "09ABCDE1234F1Z5",
            ),
        )

        val report = repo.bookReport(apr, LocalDate.of(2026, 4, 30), book = shop)
        assertEquals(18_250_000L, report.totalIncome)
        assertEquals(11_140_000L, report.totalExpenses)
        assertEquals(5_000_000L, report.fromOtherBooks)
        assertEquals(2_000_000L, report.toOtherBooks)
        // The personal book doesn't count the shop's rent, though its account paid it.
        assertEquals(0L, repo.bookReport(apr, LocalDate.of(2026, 4, 30), book = Books.PERSONAL_ID).totalExpenses)

        val dir = File(context.getExternalFilesDir(null), "preview").apply { mkdirs() }
        File(dir, "business-report.pdf").outputStream().use { ReportPdf(context, Currencies.of("INR")).write(report, repo.myDetails(shop), it) }
        context.deleteDatabase(MoneyDb.NAME)
    }
}
