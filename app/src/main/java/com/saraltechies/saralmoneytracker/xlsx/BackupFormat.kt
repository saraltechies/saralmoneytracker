package com.saraltechies.saralmoneytracker.xlsx

import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import java.io.OutputStream
import java.io.Writer
import java.time.LocalDate
import java.util.Locale

/**
 * The export file. It's a normal Excel workbook to read and filter, and also the app's full backup: importing it
 * restores accounts, categories with budgets, repeating entries and every transaction. Sheet names, column names
 * and words like "Expense" stay in English so a backup reads back the same in any language.
 */
object BackupFormat {

    const val SHEET_TRANSACTIONS = "Transactions"
    const val SHEET_ACCOUNTS = "Accounts"
    const val SHEET_CATEGORIES = "Categories"
    const val SHEET_RECURRING = "Recurring"

    private val TX_HEADER = listOf("Date", "Type", "Amount", "Category", "Account", "To account", "Note")

    fun isBackupHeader(sheet: XSheet): Boolean {
        val header = sheet.rows[0]?.values?.map { it.text.trim().lowercase(Locale.ROOT) }.orEmpty()
        return "date" in header && "type" in header && "amount" in header && "account" in header
    }

    class Data(
        val accounts: List<Account>,
        val categories: List<Category>,
        val txns: List<Txn>,
        val recurring: List<Recurring>,
    )

    fun writeXlsx(data: Data, out: OutputStream, exportedOn: LocalDate, appVersion: String) {
        val acc = data.accounts.associateBy { it.id }
        val cat = data.categories.associateBy { it.id }
        val w = XlsxWriter()
        w.sheet(
            SHEET_TRANSACTIONS,
            listOf(TX_HEADER) + data.txns.sortedWith(compareBy({ it.date }, { it.createdAt })).map { t ->
                listOf(
                    t.date, typeName(t.type), MoneyCell(t.amount), t.categoryId?.let { cat[it]?.name }.orEmpty(),
                    acc[t.accountId]?.name.orEmpty(), t.toAccountId?.let { acc[it]?.name }.orEmpty(), t.note,
                )
            },
            widths = listOf(14, 10, 14, 22, 18, 18, 40),
        )
        w.sheet(
            SHEET_ACCOUNTS,
            listOf(listOf("Name", "Type", "Opening balance", "Hidden")) + data.accounts.map {
                listOf(it.name, accountTypeName(it.type), MoneyCell(it.opening), if (it.archived) "Yes" else "")
            },
            widths = listOf(24, 10, 16, 8),
        )
        w.sheet(
            SHEET_CATEGORIES,
            listOf(listOf("Name", "Kind", "Monthly budget", "Icon", "Colour", "Hidden")) + data.categories.map {
                listOf(
                    it.name, typeName(it.kind), it.budget?.let(::MoneyCell), it.icon,
                    "#" + Integer.toHexString(it.color).uppercase(Locale.ROOT).padStart(8, '0'), if (it.archived) "Yes" else "",
                )
            },
            widths = listOf(24, 10, 16, 12, 12, 8),
        )
        w.sheet(
            SHEET_RECURRING,
            listOf(listOf("Type", "Amount", "Category", "Account", "To account", "Note", "Repeat", "Start date", "Next date", "End date", "Paused")) +
                data.recurring.map { r ->
                    listOf(
                        typeName(r.type), MoneyCell(r.amount), r.categoryId?.let { cat[it]?.name }.orEmpty(),
                        acc[r.accountId]?.name.orEmpty(), r.toAccountId?.let { acc[it]?.name }.orEmpty(), r.note,
                        frequencyName(r.frequency), r.startDate, r.nextDate, r.endDate, if (r.active) "" else "Yes",
                    )
                },
            widths = listOf(10, 14, 22, 18, 18, 30, 10, 14, 14, 14, 8),
        )
        w.sheet(
            "About",
            listOf(
                listOf("Saral Money Tracker export"),
                listOf("Exported on", exportedOn),
                listOf("App version", appVersion),
                listOf("Import this file in Saral Money Tracker (Settings → Import from Excel) to restore everything."),
            ),
            widths = listOf(30, 16),
        )
        w.write(out)
    }

    /** Transactions only, for spreadsheet apps that prefer CSV. */
    fun writeCsv(data: Data, out: Writer) {
        val acc = data.accounts.associateBy { it.id }
        val cat = data.categories.associateBy { it.id }
        out.write(TX_HEADER.joinToString(",") + "\r\n")
        for (t in data.txns.sortedWith(compareBy({ it.date }, { it.createdAt }))) {
            val fields = listOf(
                t.date.toString(), typeName(t.type), java.math.BigDecimal.valueOf(t.amount, 2).toPlainString(),
                t.categoryId?.let { cat[it]?.name }.orEmpty(), acc[t.accountId]?.name.orEmpty(),
                t.toAccountId?.let { acc[it]?.name }.orEmpty(), t.note,
            )
            out.write(fields.joinToString(",") { csv(it) } + "\r\n")
        }
        out.flush()
    }

    private fun csv(value: String): String {
        // A leading = + - @ would run as a formula when the file is opened in a spreadsheet.
        val safe = if (value.isNotEmpty() && value[0] in "=+@" ) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun typeName(type: TxnType) = when (type) {
        TxnType.EXPENSE -> "Expense"
        TxnType.INCOME -> "Income"
        TxnType.TRANSFER -> "Transfer"
    }

    fun parseType(text: String): TxnType? = when (text.trim().lowercase(Locale.ROOT)) {
        "expense" -> TxnType.EXPENSE
        "income" -> TxnType.INCOME
        "transfer" -> TxnType.TRANSFER
        else -> null
    }

    fun accountTypeName(type: AccountType) = when (type) {
        AccountType.CASH -> "Cash"
        AccountType.BANK -> "Bank"
        AccountType.CARD -> "Card"
        AccountType.WALLET -> "Wallet"
        AccountType.OTHER -> "Other"
    }

    fun parseAccountType(text: String): AccountType =
        AccountType.entries.firstOrNull { accountTypeName(it).equals(text.trim(), true) } ?: AccountType.OTHER

    fun frequencyName(f: Frequency) = when (f) {
        Frequency.DAILY -> "Daily"
        Frequency.WEEKLY -> "Weekly"
        Frequency.MONTHLY -> "Monthly"
        Frequency.YEARLY -> "Yearly"
    }

    fun parseFrequency(text: String): Frequency? = Frequency.entries.firstOrNull { frequencyName(it).equals(text.trim(), true) }
}
