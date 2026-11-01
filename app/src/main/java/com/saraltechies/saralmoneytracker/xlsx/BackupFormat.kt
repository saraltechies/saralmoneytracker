package com.saraltechies.saralmoneytracker.xlsx

import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Loan
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
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
    const val SHEET_LOANS = "Loans"
    const val SHEET_CONTACTS = "Contacts"

    private val TX_HEADER = listOf("Date", "Type", "Amount", "Category", "Account", "To account", "Note", "Loan")

    fun isBackupHeader(sheet: XSheet): Boolean {
        val header = sheet.rows[0]?.values?.map { it.text.trim().lowercase(Locale.ROOT) }.orEmpty()
        return "date" in header && "type" in header && "amount" in header && "account" in header
    }

    class Data(
        val accounts: List<Account>,
        val categories: List<Category>,
        val txns: List<Txn>,
        val recurring: List<Recurring>,
        val loans: List<Loan> = emptyList(),
        val contacts: List<Contact> = emptyList(),
    )

    fun writeXlsx(data: Data, out: OutputStream, exportedOn: LocalDate, appVersion: String) {
        val acc = data.accounts.associateBy { it.id }
        val cat = data.categories.associateBy { it.id }
        val loanAccounts = data.loans.associate { it.id to acc[it.accountId]?.name.orEmpty() }
        fun loanName(t: Txn) = t.loanId?.let { loanAccounts[it] }.orEmpty()
        val w = XlsxWriter()
        w.sheet(
            SHEET_TRANSACTIONS,
            listOf(TX_HEADER) + data.txns.sortedWith(compareBy({ it.date }, { it.createdAt })).map { t ->
                listOf(
                    t.date, typeName(t.type), MoneyCell(t.amount), t.categoryId?.let { cat[it]?.name }.orEmpty(),
                    acc[t.accountId]?.name.orEmpty(), t.toAccountId?.let { acc[it]?.name }.orEmpty(), t.note, loanName(t),
                )
            },
            widths = listOf(14, 10, 14, 22, 18, 18, 40, 18),
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
        // Contacts are linked by name, so two with the same name get "Ravi" and "Ravi (2)".
        val contactNames = HashMap<Long, String>()
        val usedNames = HashSet<String>()
        for (c in data.contacts) {
            var name = c.name.ifBlank { "?" }
            var n = 2
            while (!usedNames.add(name.lowercase(Locale.ROOT))) name = "${c.name} (${n++})"
            contactNames[c.id] = name
        }
        w.sheet(
            SHEET_CONTACTS,
            listOf(listOf("Name", "Type", "Phone", "Email", "Address", "PAN / GSTIN", "Bank", "Account number", "IFSC", "UPI ID", "Reference", "Note")) +
                data.contacts.map { c ->
                    listOf(
                        contactNames[c.id].orEmpty(), contactKindName(c.kind), c.phone, c.email, c.address, c.taxId, c.bankName,
                        c.bankAccount, c.ifsc, c.upi, c.reference, c.note,
                    )
                },
            widths = listOf(22, 10, 16, 26, 36, 18, 18, 20, 14, 22, 18, 30),
        )
        w.sheet(
            SHEET_LOANS,
            listOf(listOf("Name", "Direction", "Start date", "Interest %", "Rate per", "Plan", "Instalments", "Instalment amount", "Note", "Closed", "Contact")) +
                data.loans.map { l ->
                    listOf(
                        loanAccounts[l.id].orEmpty(), directionName(l.direction), l.startDate,
                        java.math.BigDecimal.valueOf(l.rate, 2).stripTrailingZeros().toPlainString(), rateUnitName(l.rateUnit), planName(l.plan),
                        if (l.plan == LoanPlan.MONTHLY) l.installments.toString() else "", if (l.plan == LoanPlan.MONTHLY) MoneyCell(l.installment) else null,
                        l.note, if (l.closed) "Yes" else "", l.contactId?.let { contactNames[it] }.orEmpty(),
                    )
                },
            widths = listOf(22, 10, 14, 10, 10, 10, 12, 16, 30, 8, 22),
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
        val loanAccounts = data.loans.associate { it.id to acc[it.accountId]?.name.orEmpty() }
        fun loanName(t: Txn) = t.loanId?.let { loanAccounts[it] }.orEmpty()
        out.write(TX_HEADER.joinToString(",") + "\r\n")
        for (t in data.txns.sortedWith(compareBy({ it.date }, { it.createdAt }))) {
            val fields = listOf(
                t.date.toString(), typeName(t.type), java.math.BigDecimal.valueOf(t.amount, 2).toPlainString(),
                t.categoryId?.let { cat[it]?.name }.orEmpty(), acc[t.accountId]?.name.orEmpty(),
                t.toAccountId?.let { acc[it]?.name }.orEmpty(), t.note, loanName(t),
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
        AccountType.LOAN -> "Loan"
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

    fun directionName(d: LoanDirection) = when (d) {
        LoanDirection.LENT -> "Lent"
        LoanDirection.BORROWED -> "Borrowed"
    }

    fun parseDirection(text: String): LoanDirection = if (text.trim().equals("borrowed", true)) LoanDirection.BORROWED else LoanDirection.LENT

    fun rateUnitName(u: RateUnit) = when (u) {
        RateUnit.MONTH -> "Month"
        RateUnit.YEAR -> "Year"
    }

    fun parseRateUnit(text: String): RateUnit = if (text.trim().equals("year", true)) RateUnit.YEAR else RateUnit.MONTH

    fun planName(p: LoanPlan) = when (p) {
        LoanPlan.FLEXIBLE -> "Any time"
        LoanPlan.MONTHLY -> "Monthly"
    }

    fun parsePlan(text: String): LoanPlan = if (text.trim().equals("monthly", true)) LoanPlan.MONTHLY else LoanPlan.FLEXIBLE

    fun contactKindName(k: ContactKind) = when (k) {
        ContactKind.PERSON -> "Person"
        ContactKind.BANK -> "Bank"
        ContactKind.COMPANY -> "Company"
        ContactKind.VENDOR -> "Vendor"
        ContactKind.SELF -> "Me"
    }

    fun parseContactKind(text: String): ContactKind = ContactKind.entries.firstOrNull { contactKindName(it).equals(text.trim(), true) } ?: ContactKind.PERSON
}
