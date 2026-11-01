package com.saraltechies.saralmoneytracker.xlsx

import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Book
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.Pledge
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.TxnType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.absoluteValue

data class ImportedRow(
    val type: TxnType,
    val date: LocalDate,
    val amount: Long,
    /** Empty when the sheet had no category; transfers have none. */
    val category: String,
    val note: String,
    val account: String? = null,
    val toAccount: String? = null,
    /** The loan (by its account name) this entry belongs to, from our own export. */
    val loan: String? = null,
    /** The book its category belongs to, from our own export. */
    val book: String? = null,
)

data class ImportedLoan(
    val name: String,
    val direction: LoanDirection,
    val startDate: LocalDate,
    val rate: Long,
    val rateUnit: RateUnit,
    val plan: LoanPlan,
    val installments: Int,
    val installment: Long,
    val note: String,
    val closed: Boolean,
    /** The contact's name in the Contacts sheet. */
    val contact: String? = null,
    val book: String? = null,
    val pledge: Pledge? = null,
)

data class ImportedParty(val name: String, val role: PartyRole, val contact: String?, val book: String?, val creditDays: Int = 0)

data class ImportedAccount(val name: String, val type: AccountType, val opening: Long, val archived: Boolean = false, val book: String? = null)

data class ImportedCategory(
    val name: String,
    val kind: TxnType,
    val budget: Long?,
    val icon: String? = null,
    val color: Int? = null,
    val book: String? = null,
)

data class ImportedRecurring(
    val type: TxnType,
    val amount: Long,
    val category: String,
    val account: String?,
    val toAccount: String?,
    val note: String,
    val frequency: Frequency,
    val startDate: LocalDate,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val active: Boolean,
    val book: String? = null,
    val remindDays: Int = 0,
)

data class ImportResult(
    val rows: List<ImportedRow>,
    val accounts: List<ImportedAccount>,
    val categories: List<ImportedCategory>,
    val recurring: List<ImportedRecurring>,
    /** Exact copies inside the file (for example a month sheet copied from an older one). */
    val duplicatesInFile: Int,
    /** Rows that had an amount but no date we could read. */
    val skipped: Int,
    val sheetsRead: List<String>,
    /** True when the file is a Saral Money Tracker export (a full backup). */
    val isBackup: Boolean,
    val loans: List<ImportedLoan> = emptyList(),
    /** People, banks and vendors with their details, and your own (kind SELF); ids are not set. */
    val contacts: List<Contact> = emptyList(),
    /** For each of [contacts], the name of its book (your details are kept per book). */
    val contactBooks: List<String?> = emptyList(),
    /** Books named in the file, with their kind. */
    val books: List<Book> = emptyList(),
    /** Customers and suppliers of the credit ledger, by their account name. */
    val parties: List<ImportedParty> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty() && accounts.isEmpty() && categories.isEmpty() && recurring.isEmpty() && loans.isEmpty() && contacts.isEmpty()
}

/**
 * Turns a workbook into entries. Understands:
 * - Saral Money Tracker's own export (Transactions, Accounts, Categories and Recurring sheets);
 * - hand-made trackers: any sheet with a header row holding a date column and an amount column. Several
 *   tables side by side are fine ("Money Out" and "Money In"); a title above a table ("Money In",
 *   "Income", "Expenses") tells which kind it is;
 * - bank-statement style sheets with separate Debit/Withdrawal and Credit/Deposit columns;
 * - budget tables (Category + Planned/Budget columns under an "Expense" title) become monthly budgets.
 */
object SpreadsheetImport {

    fun read(sheets: List<XSheet>): ImportResult {
        val txSheet = sheets.firstOrNull { it.name.equals(BackupFormat.SHEET_TRANSACTIONS, true) && BackupFormat.isBackupHeader(it) }
        return if (txSheet != null) readBackup(sheets) else readTrackers(sheets)
    }

    // ---- Our own export -------------------------------------------------------------------------------

    private fun readBackup(sheets: List<XSheet>): ImportResult {
        fun sheet(name: String) = sheets.firstOrNull { it.name.equals(name, true) }
        val rows = mutableListOf<ImportedRow>()
        var skipped = 0
        sheet(BackupFormat.SHEET_TRANSACTIONS)?.let { s ->
            val h = headerIndex(s, 0)
            for (r in 1..s.lastRow) {
                val date = date(s.cell(r, h["date"] ?: -1)) ?: run { if (s.rows[r] != null) skipped++; null } ?: continue
                val amount = amount(s.cell(r, h["amount"] ?: -1))?.absoluteValue ?: continue
                if (amount == 0L) continue
                rows += ImportedRow(
                    type = BackupFormat.parseType(text(s, r, h["type"])) ?: TxnType.EXPENSE,
                    date = date,
                    amount = amount,
                    category = text(s, r, h["category"]),
                    note = text(s, r, h["note"]),
                    account = text(s, r, h["account"]).ifEmpty { null },
                    toAccount = text(s, r, h["to account"]).ifEmpty { null },
                    loan = text(s, r, h["loan"]).ifEmpty { null },
                    book = text(s, r, h["book"]).ifEmpty { null },
                )
            }
        }
        val accounts = sheet(BackupFormat.SHEET_ACCOUNTS)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                ImportedAccount(
                    name = name,
                    type = BackupFormat.parseAccountType(text(s, r, h["type"])),
                    opening = amount(s.cell(r, h["opening balance"] ?: -1)) ?: 0,
                    archived = text(s, r, h["hidden"]).equals("yes", true),
                    book = text(s, r, h["book"]).ifEmpty { null },
                )
            }
        }.orEmpty()
        val categories = sheet(BackupFormat.SHEET_CATEGORIES)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                ImportedCategory(
                    name = name,
                    kind = if (BackupFormat.parseType(text(s, r, h["kind"])) == TxnType.INCOME) TxnType.INCOME else TxnType.EXPENSE,
                    budget = amount(s.cell(r, h["monthly budget"] ?: -1))?.takeIf { it > 0 },
                    icon = text(s, r, h["icon"]).ifEmpty { null },
                    color = text(s, r, h["colour"]).removePrefix("#").toLongOrNull(16)?.toInt(),
                    book = text(s, r, h["book"]).ifEmpty { null },
                )
            }
        }.orEmpty()
        val recurring = sheet(BackupFormat.SHEET_RECURRING)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val start = date(s.cell(r, h["start date"] ?: -1)) ?: return@mapNotNull null
                val amount = amount(s.cell(r, h["amount"] ?: -1))?.absoluteValue ?: return@mapNotNull null
                ImportedRecurring(
                    type = BackupFormat.parseType(text(s, r, h["type"])) ?: TxnType.EXPENSE,
                    amount = amount,
                    category = text(s, r, h["category"]),
                    account = text(s, r, h["account"]).ifEmpty { null },
                    toAccount = text(s, r, h["to account"]).ifEmpty { null },
                    note = text(s, r, h["note"]),
                    frequency = BackupFormat.parseFrequency(text(s, r, h["repeat"])) ?: Frequency.MONTHLY,
                    startDate = start,
                    nextDate = date(s.cell(r, h["next date"] ?: -1)) ?: start,
                    endDate = date(s.cell(r, h["end date"] ?: -1)),
                    active = !text(s, r, h["paused"]).equals("yes", true),
                    remindDays = text(s, r, h["remind days before"]).toIntOrNull() ?: 0,
                    book = text(s, r, h["book"]).ifEmpty { null },
                )
            }
        }.orEmpty()
        val loans = sheet(BackupFormat.SHEET_LOANS)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                ImportedLoan(
                    name = name,
                    direction = BackupFormat.parseDirection(text(s, r, h["direction"])),
                    startDate = date(s.cell(r, h["start date"] ?: -1)) ?: return@mapNotNull null,
                    // "2" or "1.5" percent, read like an amount: hundredths of a percent.
                    rate = amount(s.cell(r, h["interest %"] ?: -1))?.absoluteValue ?: 0,
                    rateUnit = BackupFormat.parseRateUnit(text(s, r, h["rate per"])),
                    plan = BackupFormat.parsePlan(text(s, r, h["plan"])),
                    installments = text(s, r, h["instalments"]).toIntOrNull() ?: 0,
                    installment = amount(s.cell(r, h["instalment amount"] ?: -1))?.absoluteValue ?: 0,
                    note = text(s, r, h["note"]),
                    closed = text(s, r, h["closed"]).equals("yes", true),
                    contact = text(s, r, h["contact"]).ifEmpty { null },
                    book = text(s, r, h["book"]).ifEmpty { null },
                    pledge = BackupFormat.parsePledgeKind(text(s, r, h["pledge"]))?.let { kind ->
                        Pledge(
                            kind = kind,
                            items = text(s, r, h["pledged items"]),
                            weightMg = Pledge.parseGrams(text(s, r, h["weight g"])) ?: 0,
                            purity = text(s, r, h["purity"]),
                            value = amount(s.cell(r, h["pledge value"] ?: -1))?.absoluteValue ?: 0,
                            returnedOn = date(s.cell(r, h["returned on"] ?: -1)),
                        )
                    },
                )
            }
        }.orEmpty()
        val (unique, duplicates) = dedupe(rows)
        val contactRows = sheet(BackupFormat.SHEET_CONTACTS)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                Contact(
                    name = name, kind = BackupFormat.parseContactKind(text(s, r, h["type"])), phone = text(s, r, h["phone"]),
                    email = text(s, r, h["email"]), address = text(s, r, h["address"]), taxId = text(s, r, h["pan / gstin"]),
                    bankName = text(s, r, h["bank"]), bankAccount = text(s, r, h["account number"]), ifsc = text(s, r, h["ifsc"]),
                    upi = text(s, r, h["upi id"]), reference = text(s, r, h["reference"]), note = text(s, r, h["note"]),
                ) to text(s, r, h["book"]).ifEmpty { null }
            }
        }.orEmpty()
        val books = sheet(BackupFormat.SHEET_BOOKS)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                Book(name = name, kind = BackupFormat.parseBookKind(text(s, r, h["type"])))
            }
        }.orEmpty()
        val parties = sheet(BackupFormat.SHEET_PARTIES)?.let { s ->
            val h = headerIndex(s, 0)
            (1..s.lastRow).mapNotNull { r ->
                val name = text(s, r, h["name"]).ifEmpty { return@mapNotNull null }
                ImportedParty(
                    name, BackupFormat.parseRole(text(s, r, h["role"])), text(s, r, h["contact"]).ifEmpty { null }, text(s, r, h["book"]).ifEmpty { null },
                    text(s, r, h["credit days"]).toIntOrNull() ?: 0,
                )
            }
        }.orEmpty()
        return ImportResult(
            unique, accounts, categories, recurring, duplicates, skipped, sheets.map { it.name }, isBackup = true, loans = loans,
            contacts = contactRows.map { it.first }, contactBooks = contactRows.map { it.second }, books = books, parties = parties,
        )
    }

    // ---- Hand-made trackers and statements ----------------------------------------------------------------

    private class Table(
        val headerRow: Int,
        val type: TxnType?,
        val date: Int,
        val amount: Int?,
        val debit: Int?,
        val credit: Int?,
        val category: Int?,
        val note: Int?,
        val typeCol: Int?,
        val account: Int?,
    )

    private fun readTrackers(sheets: List<XSheet>): ImportResult {
        val rows = mutableListOf<ImportedRow>()
        val budgets = LinkedHashMap<String, Long>()
        var skipped = 0
        val read = mutableListOf<String>()
        for (sheet in sheets) {
            var found = false
            for (r in 0..sheet.lastRow) {
                val row = sheet.rows[r] ?: continue
                for (table in tablesIn(sheet, r, row)) {
                    found = true
                    skipped += readTable(sheet, table, rows)
                }
                budgetTable(sheet, r, row)?.let { (catCol, budgetCol) ->
                    found = true
                    for (rr in r + 1..sheet.lastRow) {
                        val name = text(sheet, rr, catCol)
                        if (name.isEmpty() || name.startsWith("total", true)) continue
                        val planned = amount(sheet.cell(rr, budgetCol)) ?: continue
                        if (planned > 0) budgets.putIfAbsent(name, planned)
                    }
                }
            }
            if (found) read += sheet.name
        }
        val (unique, duplicates) = dedupe(rows)
        val categories = budgets.map { (name, budget) -> ImportedCategory(name, TxnType.EXPENSE, budget) }
        return ImportResult(unique, emptyList(), categories, emptyList(), duplicates, skipped, read, isBackup = false)
    }

    /** Header rows: a date column plus an amount (or debit/credit) column. Tables side by side are split at each date column. */
    private fun tablesIn(sheet: XSheet, r: Int, row: Map<Int, XCell>): List<Table> {
        val labels = row.mapValues { norm(it.value.text) }
        val dateCols = labels.filter { (_, v) -> isDate(v) }.keys.sorted()
        if (dateCols.isEmpty()) return emptyList()
        val tables = mutableListOf<Table>()
        for ((i, dateCol) in dateCols.withIndex()) {
            val from = if (i == 0) (labels.keys.minOrNull() ?: dateCol) else dateCol
            val until = dateCols.getOrNull(i + 1) ?: Int.MAX_VALUE
            val group = labels.filterKeys { it in from until until && it != dateCol }
            fun col(test: (String) -> Boolean) = group.entries.filter { test(it.value) }.minByOrNull { it.key }?.key
            val amount = col { it.startsWith("amount") || it == "amt" || it == "value" }
            val debit = col { it.contains("debit") || it.contains("withdraw") || it == "paid" || it == "spent" || it == "money out" || it == "expense" }
            val credit = col { it.contains("credit") && !it.contains("card") || it.contains("deposit") || it == "received" || it == "earned" || it == "money in" || it == "income" }
            if (amount == null && debit == null) continue
            tables += Table(
                headerRow = r,
                type = titleAbove(sheet, r, from, until),
                date = dateCol,
                amount = amount,
                debit = if (amount == null) debit else null,
                credit = if (amount == null) credit else null,
                category = col { it == "category" || it == "head" || it == "category name" },
                note = col { it in NOTE_HEADERS || it.startsWith("description") || it.startsWith("narration") || it.startsWith("particular") },
                typeCol = col { it == "type" || it == "income/expense" || it == "in/out" },
                account = col { it == "account" || it == "payment mode" || it == "mode" || it == "paid via" || it == "paid from" },
            )
        }
        return tables
    }

    private fun readTable(sheet: XSheet, t: Table, out: MutableList<ImportedRow>): Int {
        var skipped = 0
        for (r in t.headerRow + 1..sheet.lastRow) {
            val row = sheet.rows[r] ?: continue
            // Stop at the next header row of the same table, if a sheet repeats them.
            if (tablesIn(sheet, r, row).isNotEmpty()) break
            var type: TxnType
            var amount: Long
            if (t.amount != null) {
                val a = amount(row[t.amount]) ?: continue
                amount = a.absoluteValue
                type = t.typeCol?.let { typeFromText(norm(row[it]?.text.orEmpty())) } ?: t.type ?: TxnType.EXPENSE
                if (a < 0 && t.typeCol == null && t.type == null) type = TxnType.EXPENSE
            } else {
                val debit = amount(row[t.debit!!])?.absoluteValue ?: 0
                val credit = t.credit?.let { amount(row[it])?.absoluteValue } ?: 0
                if (debit > 0) {
                    type = TxnType.EXPENSE; amount = debit
                } else if (credit > 0) {
                    type = TxnType.INCOME; amount = credit
                } else continue
            }
            if (amount == 0L) continue
            val date = date(row[t.date])
            if (date == null) {
                skipped++
                continue
            }
            out += ImportedRow(
                type = type,
                date = date,
                amount = amount,
                category = t.category?.let { row[it]?.text?.trim() }.orEmpty(),
                note = t.note?.let { row[it]?.text?.trim() }.orEmpty(),
                account = t.account?.let { row[it]?.text?.trim() }?.ifEmpty { null },
            )
        }
        return skipped
    }

    /** A "Category | Planned" table titled as expenses, e.g. the summary sheet of a monthly tracker. */
    private fun budgetTable(sheet: XSheet, r: Int, row: Map<Int, XCell>): Pair<Int, Int>? {
        val labels = row.mapValues { norm(it.value.text) }
        if (labels.values.any { isDate(it) }) return null
        val catCols = labels.filter { it.value == "category" }.keys.sorted()
        for ((i, catCol) in catCols.withIndex()) {
            val until = catCols.getOrNull(i + 1) ?: Int.MAX_VALUE
            val budgetCol = labels.entries.firstOrNull { it.key in catCol + 1 until until && (it.value == "planned" || it.value == "budget" || it.value == "budgeted") }?.key
                ?: continue
            if (titleAbove(sheet, r, catCol, until) == TxnType.EXPENSE) return catCol to budgetCol
        }
        return null
    }

    /** Looks up to three rows above a table for a title such as "Money In" or "Expenses by Category". */
    private fun titleAbove(sheet: XSheet, headerRow: Int, from: Int, until: Int): TxnType? {
        for (r in headerRow - 1 downTo maxOf(0, headerRow - 3)) {
            val texts = sheet.rows[r]?.filterKeys { it in from until until }?.values?.map { norm(it.text) } ?: continue
            for (t in texts) typeFromTitle(t)?.let { return it }
        }
        return null
    }

    private fun typeFromTitle(t: String): TxnType? = when {
        Regex("\\b(money out|out|expenses?|spent|spending|paid|debits?)\\b").containsMatchIn(t) -> TxnType.EXPENSE
        Regex("\\b(money in|in|income|incomes|earned|earnings|received|credits?)\\b").containsMatchIn(t) -> TxnType.INCOME
        else -> null
    }

    private fun typeFromText(t: String): TxnType? = when {
        t.isEmpty() -> null
        t.contains("transfer") -> TxnType.TRANSFER
        t.contains("income") || t == "in" || t == "cr" || t.contains("credit") || t.contains("received") -> TxnType.INCOME
        else -> TxnType.EXPENSE
    }

    // ---- Shared helpers ---------------------------------------------------------------------------------------

    private val NOTE_HEADERS = setOf("description", "note", "notes", "details", "remarks", "remark", "item", "items", "memo", "purpose", "narration", "particulars")

    private fun isDate(label: String) = label == "date" || label.endsWith(" date") || label.startsWith("date ")

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").removeSuffix(":")

    private fun text(sheet: XSheet, row: Int, col: Int?): String = col?.let { sheet.cell(row, it)?.text?.trim() }.orEmpty()

    private fun headerIndex(sheet: XSheet, row: Int): Map<String, Int> =
        sheet.rows[row].orEmpty().entries.associate { norm(it.value.text) to it.key }

    /** Hundredths from a number cell or text like "₹1,250.50" / "(1,250)" / "1250 Dr". */
    fun amount(cell: XCell?): Long? {
        cell ?: return null
        cell.number?.let { return Money.toMinor(it) }
        var t = cell.text.trim()
        if (t.isEmpty()) return null
        var negative = false
        if (t.startsWith("(") && t.endsWith(")")) {
            negative = true
            t = t.substring(1, t.length - 1)
        }
        if (t.endsWith("dr", true)) {
            negative = true
            t = t.dropLast(2)
        } else if (t.endsWith("cr", true)) t = t.dropLast(2)
        t = t.replace(Regex("[^0-9.\\-]"), "")
        val value = Money.parse(t) ?: return null
        return if (negative) -value.absoluteValue else value
    }

    private val TEXT_DATES: List<DateTimeFormatter> = listOf(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d", "uuuu/M/d", "d MMM uuuu", "d-MMM-uuuu", "d MMMM uuuu",
        "MMM d, uuuu", "MMMM d, uuuu", "d/M/uu", "d-M-uu", "d-MMM-uu", "d MMM uu",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    /** A date cell (Excel serial number) or text. Day-first like India and the UK unless the day can't be first. */
    fun date(cell: XCell?): LocalDate? {
        cell ?: return null
        cell.number?.let { return ExcelDates.fromSerial(it) }
        val t = cell.text.trim().substringBefore(' ').let { first -> if (first.any { it.isLetter() }) cell.text.trim() else first }
        if (t.isEmpty()) return null
        Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})$").find(t)?.let { m ->
            val (a, b, y) = m.destructured
            var day = a.toInt()
            var month = b.toInt()
            if (month > 12 && day <= 12) day = month.also { month = day }
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        for (candidate in setOf(cell.text.trim(), t)) for (f in TEXT_DATES) runCatching { return LocalDate.parse(candidate, f) }
        return null
    }

    private fun dedupe(rows: List<ImportedRow>): Pair<List<ImportedRow>, Int> {
        val seen = HashSet<String>()
        val unique = rows.filter { seen.add(key(it)) }
        return unique to (rows.size - unique.size)
    }

    fun key(r: ImportedRow): String =
        listOf(
            r.type, r.date, r.amount, r.category.lowercase(Locale.ROOT), r.note.lowercase(Locale.ROOT), r.book.orEmpty().lowercase(Locale.ROOT),
            r.account.orEmpty().lowercase(Locale.ROOT), r.toAccount.orEmpty().lowercase(Locale.ROOT),
        ).joinToString("|")
}
