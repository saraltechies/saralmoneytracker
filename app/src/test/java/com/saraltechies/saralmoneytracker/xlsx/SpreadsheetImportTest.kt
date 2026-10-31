package com.saraltechies.saralmoneytracker.xlsx

import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.time.LocalDate

class SpreadsheetImportTest {

    private fun d(s: String) = LocalDate.parse(s)

    private fun workbook(build: XlsxWriter.() -> Unit): List<XSheet> {
        val out = ByteArrayOutputStream()
        XlsxWriter().apply(build).write(out)
        return XlsxReader.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun writerAndReaderRoundTrip() {
        val sheets = workbook {
            sheet("Data & more", listOf(listOf("Date", "Amount", "Note"), listOf(d("2026-10-31"), MoneyCell(125_050), "Tea <& snacks>")))
        }
        assertEquals("Data & more", sheets.single().name)
        val row = sheets.single().rows[1]!!
        assertEquals(d("2026-10-31"), SpreadsheetImport.date(row[0]))
        assertEquals(125_050L, SpreadsheetImport.amount(row[1]))
        assertEquals("Tea <& snacks>", row[2]!!.text)
    }

    /** The layout of a typical hand-made tracker: a summary sheet plus one sheet per month with Money Out / Money In tables. */
    @Test
    fun readsMonthlyTrackerWorkbook() {
        val header = listOf(null, null, "Date", "Amount", "Category", "Description", null, null, "Date", "Amount", "Category", "Description")
        val sept = listOf(
            listOf(null, null, "Money Out", null, null, null, null, null, "Money In"),
            header,
            listOf(null, null, "18/09/2022", 1205.78, "Credit Card", "RBL Bank", null, null, d("2022-09-28"), MoneyCell(6_200_000), "Paycheck", "Monthly Salary"),
            listOf(null, null, "18/09/2022", 12.0, "Food", "Evening Tea"),
            listOf(null, null, d("2022-09-19"), 60.0, "Food", "Nariyal Pani"),
            listOf(null, null, "n/a", 99.0, "Food", "Broken row"),
        )
        // A later month that was made by copying September and still holds some of its rows.
        val jan = listOf(
            listOf(null, null, "Money Out", null, null, null, null, null, "Money In"),
            header,
            listOf(null, null, "18/09/2022", 12.0, "Food", "Evening Tea"),
            listOf(null, null, d("2024-01-01"), 10420.93, "Credit Card", "Kotak Rupay"),
        )
        val summary = listOf(
            listOf(null, "Month:", d("2024-01-01")),
            listOf("Expenses by Category", null, null, null, null, "Income by Category"),
            listOf("Category", "Planned", "Spent", "Difference", null, "Category", "Planned", "Earned"),
            listOf("Food", 6000.0, 349.0, 5651.0, null, "Paycheck", 61999.0, 62000.0),
            listOf("Credit Card", 50.0, 0.0, 50.0),
            listOf("Total:", 6050.0),
        )
        val result = SpreadsheetImport.read(workbook {
            sheet("Expense  Budget Summary", summary)
            sheet("Sep 2022", sept)
            sheet("Jan 2024", jan)
        })

        assertFalse(result.isBackup)
        assertEquals(5, result.rows.size)
        assertEquals(1, result.duplicatesInFile)
        assertEquals(1, result.skipped)
        val salary = result.rows.single { it.type == TxnType.INCOME }
        assertEquals(6_200_000L, salary.amount)
        assertEquals("Paycheck", salary.category)
        assertEquals(d("2022-09-18"), result.rows.first { it.note == "Evening Tea" }.date)
        assertEquals(4, result.rows.count { it.type == TxnType.EXPENSE })
        // Only the expense table's planned amounts become budgets.
        assertEquals(mapOf("Food" to 600_000L, "Credit Card" to 5_000L), result.categories.associate { it.name to it.budget })
        assertEquals(setOf("credit card"), MoneyRepository.cardBillCategories(result))
    }

    @Test
    fun readsBankStatementColumns() {
        val result = SpreadsheetImport.read(workbook {
            sheet(
                "Statement",
                listOf(
                    listOf("Txn Date", "Narration", "Withdrawal Amt", "Deposit Amt"),
                    listOf("01-10-2026", "UPI/Swiggy", "450.00", null),
                    listOf("02-10-2026", "SALARY OCT", null, "75,000.00"),
                ),
            )
        })
        assertEquals(listOf(TxnType.EXPENSE, TxnType.INCOME), result.rows.map { it.type })
        assertEquals(listOf(45_000L, 7_500_000L), result.rows.map { it.amount })
        assertEquals("UPI/Swiggy", result.rows[0].note)
    }

    @Test
    fun backupRestoresEverything() {
        val accounts = listOf(
            Account(id = 1, name = "Cash", type = AccountType.CASH, opening = 50_000),
            Account(id = 2, name = "HDFC, Savings", type = AccountType.BANK),
            Account(id = 3, name = "ICICI Card", type = AccountType.CARD, archived = true),
        )
        val categories = listOf(
            Category(id = 10, name = "Food & Dining", kind = TxnType.EXPENSE, icon = "restaurant", color = 0xFFF97316.toInt(), budget = 600_000),
            Category(id = 20, name = "Salary", kind = TxnType.INCOME, icon = "salary", color = 0xFF16A34A.toInt()),
        )
        val txns = listOf(
            Txn(type = TxnType.EXPENSE, amount = 12_050, date = d("2026-10-30"), categoryId = 10, accountId = 1, note = "=lunch, \"office\""),
            Txn(type = TxnType.TRANSFER, amount = 500_000, date = d("2026-10-31"), categoryId = null, accountId = 2, toAccountId = 3),
            Txn(type = TxnType.INCOME, amount = 7_500_000, date = d("2026-10-01"), categoryId = 20, accountId = 2),
        )
        val rules = listOf(
            Recurring(
                type = TxnType.INCOME, amount = 7_500_000, categoryId = 20, accountId = 2, note = "Salary", frequency = Frequency.MONTHLY,
                startDate = d("2026-10-01"), nextDate = d("2026-11-01"),
            ),
        )
        val out = ByteArrayOutputStream()
        BackupFormat.writeXlsx(BackupFormat.Data(accounts, categories, txns, rules), out, d("2026-10-31"), "1.0.0")
        val result = SpreadsheetImport.read(XlsxReader.read(ByteArrayInputStream(out.toByteArray())))

        assertTrue(result.isBackup)
        assertEquals(3, result.rows.size)
        val transfer = result.rows.single { it.type == TxnType.TRANSFER }
        assertEquals("HDFC, Savings", transfer.account)
        assertEquals("ICICI Card", transfer.toAccount)
        assertEquals("=lunch, \"office\"", result.rows.single { it.type == TxnType.EXPENSE }.note)
        assertEquals(listOf("Cash", "HDFC, Savings", "ICICI Card"), result.accounts.map { it.name })
        assertTrue(result.accounts.single { it.name == "ICICI Card" }.archived)
        assertEquals(50_000L, result.accounts.first().opening)
        val food = result.categories.single { it.name == "Food & Dining" }
        assertEquals(600_000L, food.budget)
        assertEquals("restaurant", food.icon)
        assertEquals(0xFFF97316.toInt(), food.color)
        val rule = result.recurring.single()
        assertEquals(Frequency.MONTHLY, rule.frequency)
        assertEquals(d("2026-11-01"), rule.nextDate)

        // CSV quotes commas and quotes, and neutralises a leading "=" so spreadsheets don't run it.
        val csv = StringWriter().also { BackupFormat.writeCsv(BackupFormat.Data(accounts, categories, txns, rules), it) }.toString()
        assertTrue(csv.contains("\"'=lunch, \"\"office\"\"\""))
        assertTrue(csv.lines().first().startsWith("Date,Type,Amount"))
    }

    @Test
    fun textDatesAndAmounts() {
        assertEquals(d("2026-10-05"), SpreadsheetImport.date(XCell("05/10/2026", null)))
        assertEquals(d("2026-10-25"), SpreadsheetImport.date(XCell("10/25/2026", null)))
        assertEquals(d("2026-10-05"), SpreadsheetImport.date(XCell("5 Oct 2026", null)))
        assertEquals(d("2026-10-05"), SpreadsheetImport.date(XCell("2026-10-05 00:00:00", null)))
        assertEquals(-125_000L, SpreadsheetImport.amount(XCell("(1,250)", null)))
        assertEquals(-50_000L, SpreadsheetImport.amount(XCell("500 Dr", null)))
        assertEquals(1_000_050L, SpreadsheetImport.amount(XCell("₹10,000.50", null)))
    }

    @Test
    fun guessesAccountTypes() {
        assertEquals(AccountType.CARD, MoneyRepository.guessAccountType("ICICI Amazon Pay Card"))
        assertEquals(AccountType.WALLET, MoneyRepository.guessAccountType("PhonePe"))
        assertEquals(AccountType.CASH, MoneyRepository.guessAccountType("Cash"))
        assertEquals(AccountType.BANK, MoneyRepository.guessAccountType("SBI"))
    }
}
