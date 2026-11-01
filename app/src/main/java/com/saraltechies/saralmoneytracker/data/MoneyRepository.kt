package com.saraltechies.saralmoneytracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_ACCOUNTS
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_BOOKS
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_CATEGORIES
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_CONTACTS
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_LOANS
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_PARTIES
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_RECURRING
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_TXNS
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Book
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.Books
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.DefaultData
import com.saraltechies.saralmoneytracker.model.Loan
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanSummary
import com.saraltechies.saralmoneytracker.model.LoanEvent
import com.saraltechies.saralmoneytracker.model.BookReport
import com.saraltechies.saralmoneytracker.model.LedgerEntry
import com.saraltechies.saralmoneytracker.model.Ledgers
import com.saraltechies.saralmoneytracker.model.Party
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.Recurrence
import com.saraltechies.saralmoneytracker.model.Reports
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.xlsx.BackupFormat
import com.saraltechies.saralmoneytracker.xlsx.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

data class ImportOptions(
    /** For rows that don't name an account. */
    val defaultAccountId: Long,
    /** Turn "Credit Card" expense rows into bill payments (transfers to a card account). */
    val cardBillsAsTransfers: Boolean,
    val useBudgets: Boolean,
    /** Localized names for things the import may need to create. */
    val cardAccountName: String,
    val otherExpenseName: String,
    val otherIncomeName: String,
    /** The book for anything the file doesn't place in a book of its own. */
    val bookId: Long = Books.PERSONAL_ID,
)

/** A customer or supplier as the screens show them: details, balance (plus: they owe you) and every entry. */
data class PartyItem(
    val party: Party,
    val name: String,
    val contact: Contact?,
    val opening: Long,
    val entries: List<LedgerEntry>,
    val txns: List<Txn>,
) {
    val balance: Long get() = entries.lastOrNull()?.balanceAfter ?: opening
    val lastDate: LocalDate? get() = entries.lastOrNull { it.txnId != null }?.date
}

/** A loan as the screens show it: the person's name, its history and where it stands. */
data class LoanItem(
    val loan: Loan,
    val name: String,
    val events: List<LoanEvent>,
    val summary: LoanSummary,
    val txns: List<Txn>,
    /** Who the loan is with, and their details; null only for a loan whose contact was lost. */
    val contact: Contact? = null,
)

/** The terms filled in on the loan form. */
data class LoanTerms(
    val name: String,
    val direction: LoanDirection,
    val startDate: LocalDate,
    val rate: Long,
    val rateUnit: com.saraltechies.saralmoneytracker.model.RateUnit,
    val plan: com.saraltechies.saralmoneytracker.model.LoanPlan,
    val installments: Int,
    val installment: Long,
    val note: String,
    val bookId: Long = Books.PERSONAL_ID,
)

data class ImportReport(val added: Int, val duplicates: Int, val transfers: Int, val categoriesAdded: Int, val accountsAdded: Int, val budgetsSet: Int, val recurringAdded: Int)

/**
 * All reads and writes. Reads are plain blocking calls meant to run inside [watch] (which re-runs them on a
 * background thread after every change), so screens stay in sync without a database framework.
 */
class MoneyRepository(private val context: Context) {

    private val db = MoneyDb(context)
    private val version = MutableStateFlow(0L)

    /**
     * The book the screens show: one book's id, or [Books.ALL]. Reads that take `book: Long? = null` follow it;
     * pass [Books.ALL] for every book (names in lists, balances, backups).
     */
    private val scope = MutableStateFlow(Books.ALL)
    val currentBook: StateFlow<Long> = scope

    fun setBook(id: Long) {
        scope.value = id
    }

    private fun resolve(book: Long?): Long = book ?: scope.value

    /** The book new accounts, categories and loans go to: the one on screen, or Personal when showing all. */
    fun defaultBook(): Long = scope.value.takeIf { it != Books.ALL } ?: Books.PERSONAL_ID

    /** Re-runs [read] on a background thread after every change and whenever the book on screen changes. */
    fun <T> watch(read: () -> T): Flow<T> = combine(version, scope) { _, _ -> withContext(Dispatchers.IO) { read() } }

    private fun accountsIn(book: Long) = "SELECT id FROM $T_ACCOUNTS WHERE book_id = $book"
    private fun categoriesIn(book: Long) = "SELECT id FROM $T_CATEGORIES WHERE book_id = $book"

    // ---- Books --------------------------------------------------------------------------------------------

    fun books(): List<Book> = db.readableDatabase.query(T_BOOKS, null, null, null, null, null, "sort, id")
        .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.book(c)) } }

    /**
     * Adds a book. A business book starts with business categories (sales, purchases, rent, salaries…) and a
     * business cash and bank account; a personal one with the usual personal ones.
     */
    suspend fun createBook(name: String, kind: BookKind): Long = write { w ->
        val sort = w.rawQuery("SELECT COALESCE(MAX(sort), 0) + 1 FROM $T_BOOKS", null).use { it.moveToFirst(); it.getInt(0) }
        val id = w.insert(T_BOOKS, null, MoneyDb.values(Book(name = name.trim(), kind = kind, sort = sort)))
        val res = context.resources
        if (kind == BookKind.BUSINESS) {
            val labels = DefaultData.BusinessLabels(
                expense = res.getStringArray(com.saraltechies.saralmoneytracker.R.array.default_business_expense_categories).toList(),
                income = res.getStringArray(com.saraltechies.saralmoneytracker.R.array.default_business_income_categories).toList(),
                cash = res.getString(com.saraltechies.saralmoneytracker.R.string.default_business_cash),
                bank = res.getString(com.saraltechies.saralmoneytracker.R.string.default_business_bank),
            )
            DefaultData.businessAccounts(labels, id).forEach { w.insert(T_ACCOUNTS, null, MoneyDb.values(it.copy(name = uniqueAccountName(w, it.name, null)))) }
            DefaultData.businessCategories(labels, id).forEach { w.insert(T_CATEGORIES, null, MoneyDb.values(it)) }
        } else {
            val labels = DefaultData.Labels(
                expense = res.getStringArray(com.saraltechies.saralmoneytracker.R.array.default_expense_categories).toList(),
                income = res.getStringArray(com.saraltechies.saralmoneytracker.R.array.default_income_categories).toList(),
                cash = res.getString(com.saraltechies.saralmoneytracker.R.string.default_account_cash),
                bank = res.getString(com.saraltechies.saralmoneytracker.R.string.default_account_bank),
            )
            DefaultData.accounts(labels).forEach { w.insert(T_ACCOUNTS, null, MoneyDb.values(it.copy(bookId = id, name = uniqueAccountName(w, "${it.name} (${name.trim()})", null)))) }
            DefaultData.categories(labels).forEach { w.insert(T_CATEGORIES, null, MoneyDb.values(it.copy(bookId = id))) }
        }
        id
    }

    suspend fun renameBook(id: Long, name: String, kind: BookKind) = write { w ->
        w.execSQL("UPDATE $T_BOOKS SET name = ?, kind = ? WHERE id = ?", arrayOf<Any?>(name.trim(), kind.name, id))
    }

    /** True when the book has entries, repeating entries or loans, so deleting it would lose history. */
    fun bookHasHistory(id: Long): Boolean = db.readableDatabase.rawQuery(
        """SELECT 1 FROM $T_TXNS WHERE account_id IN (${accountsIn(id)}) OR to_account_id IN (${accountsIn(id)}) OR category_id IN (${categoriesIn(id)})
           UNION SELECT 1 FROM $T_RECURRING WHERE account_id IN (${accountsIn(id)}) OR category_id IN (${categoriesIn(id)})
           UNION SELECT 1 FROM $T_LOANS WHERE book_id = $id UNION SELECT 1 FROM $T_PARTIES WHERE book_id = $id LIMIT 1""",
        null,
    ).use { it.moveToFirst() }

    /** Deletes a book that has no history, with its accounts, categories and details. The Personal book stays. */
    suspend fun deleteBook(id: Long): Boolean {
        if (id == Books.PERSONAL_ID || withContext(Dispatchers.IO) { bookHasHistory(id) }) return false
        write { w ->
            for (table in listOf(T_ACCOUNTS, T_CATEGORIES, T_CONTACTS, T_PARTIES)) w.delete(table, "book_id = ?", arrayOf(id.toString()))
            w.delete(T_BOOKS, "id = ?", arrayOf(id.toString()))
        }
        if (scope.value == id) scope.value = Books.ALL
        return true
    }

    private fun changed() {
        version.update { it + 1 }
        com.saraltechies.saralmoneytracker.util.Widgets.requestUpdate(context)
    }

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            block(w).also { w.setTransactionSuccessful() }
        } finally {
            w.endTransaction()
        }
    }.also { changed() }

    // ---- Reads --------------------------------------------------------------------------------------------

    /**
     * Your accounts. Loan accounts (one per [Loan]) are left out unless [includeLoans]: they belong on the Loans
     * screen, not in account pickers. Lookups for showing names pass `true` for both.
     */
    fun accounts(includeArchived: Boolean = false, includeLoans: Boolean = includeArchived, book: Long? = null): List<Account> {
        val b = resolve(book)
        val where = listOfNotNull(
            if (includeArchived) null else "archived = 0",
            if (includeLoans) null else "type NOT IN ('${AccountType.LOAN.name}', '${AccountType.PARTY.name}')",
            if (b == Books.ALL) null else "book_id = $b",
        ).joinToString(" AND ").ifEmpty { null }
        return db.readableDatabase.query(T_ACCOUNTS, null, where, null, null, null, "book_id, sort, id")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.account(c)) } }
    }

    fun categories(includeArchived: Boolean = false, book: Long? = null): List<Category> {
        val b = resolve(book)
        val where = listOfNotNull(if (includeArchived) null else "archived = 0", if (b == Books.ALL) null else "book_id = $b")
            .joinToString(" AND ").ifEmpty { null }
        return db.readableDatabase.query(T_CATEGORIES, null, where, null, null, null, "book_id, sort, id")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.category(c)) } }
    }

    /**
     * Newest first. [query] matches the note or the category name.
     *
     * With a book, entries that touch it: its categories, or money in or out of its accounts. With [profitAndLoss],
     * only the income and expenses that count in the book (by category), for totals, charts and budgets.
     */
    fun txns(
        from: LocalDate? = null,
        to: LocalDate? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        query: String? = null,
        limit: Int? = null,
        book: Long? = null,
        profitAndLoss: Boolean = false,
    ): List<Txn> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        val b = resolve(book)
        when {
            profitAndLoss && b == Books.ALL -> where += "type != '${TxnType.TRANSFER.name}'"
            profitAndLoss -> where += "type != '${TxnType.TRANSFER.name}' AND category_id IN (${categoriesIn(b)})"
            b != Books.ALL -> where += "(category_id IN (${categoriesIn(b)}) OR account_id IN (${accountsIn(b)}) OR to_account_id IN (${accountsIn(b)}))"
        }
        from?.let { where += "day >= ?"; args += it.toEpochDay().toString() }
        to?.let { where += "day <= ?"; args += it.toEpochDay().toString() }
        accountId?.let { where += "(account_id = ? OR to_account_id = ?)"; args += it.toString(); args += it.toString() }
        categoryId?.let { where += "category_id = ?"; args += it.toString() }
        if (!query.isNullOrBlank()) {
            val q = query.trim()
            val catIds = categories(includeArchived = true, book = Books.ALL).filter { it.name.contains(q, ignoreCase = true) }.map { it.id }
            // Also the name of an account, customer, supplier or loan, so "Ramesh" finds everything with Ramesh.
            val accIds = accounts(includeArchived = true, includeLoans = true, book = Books.ALL).filter { it.name.contains(q, ignoreCase = true) }.map { it.id }
            val accList = accIds.joinToString(",")
            val catClause = (if (catIds.isEmpty()) "" else " OR category_id IN (${catIds.joinToString(",")})") +
                (if (accIds.isEmpty()) "" else " OR account_id IN ($accList) OR to_account_id IN ($accList)")
            where += "(note LIKE ? ESCAPE '\\'$catClause)"
            args += "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        return db.readableDatabase.query(
            T_TXNS, null, where.joinToString(" AND ").ifEmpty { null }, args.toTypedArray(), null, null,
            "day DESC, created DESC", limit?.toString(),
        ).use { c -> buildList { while (c.moveToNext()) add(MoneyDb.txn(c)) } }
    }

    fun txn(id: Long): Txn? = db.readableDatabase.query(T_TXNS, null, "id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) MoneyDb.txn(c) else null }

    fun recurring(book: Long? = null): List<Recurring> {
        val b = resolve(book)
        val where = if (b == Books.ALL) null
        else "(category_id IN (${categoriesIn(b)}) OR account_id IN (${accountsIn(b)}) OR to_account_id IN (${accountsIn(b)}))"
        return db.readableDatabase.query(T_RECURRING, null, where, null, null, null, "active DESC, next_day")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.recurring(c)) } }
    }

    fun recurringRule(id: Long): Recurring? = db.readableDatabase.query(T_RECURRING, null, "id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) MoneyDb.recurring(c) else null }

    /** Current balance of every account, hidden ones included. */
    fun balances(): Map<Long, Long> = Stats.balances(accounts(includeArchived = true, book = Books.ALL), txns(book = Books.ALL))

    fun txnCount(book: Long? = null): Int {
        val b = resolve(book)
        val where = if (b == Books.ALL) "" else " WHERE category_id IN (${categoriesIn(b)}) OR account_id IN (${accountsIn(b)}) OR to_account_id IN (${accountsIn(b)})"
        return db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $T_TXNS$where", null).use { it.moveToFirst(); it.getInt(0) }
    }

    fun hasTxnOn(day: LocalDate): Boolean = db.readableDatabase.rawQuery(
        "SELECT 1 FROM $T_TXNS WHERE day = ? AND type != ? LIMIT 1", arrayOf(day.toEpochDay().toString(), TxnType.TRANSFER.name),
    ).use { it.moveToFirst() }

    fun isUsed(accountId: Long): Boolean = db.readableDatabase.rawQuery(
        "SELECT 1 FROM $T_TXNS WHERE account_id = ?1 OR to_account_id = ?1 UNION SELECT 1 FROM $T_RECURRING WHERE account_id = ?1 OR to_account_id = ?1 LIMIT 1",
        arrayOf(accountId.toString()),
    ).use { it.moveToFirst() }

    fun isCategoryUsed(categoryId: Long): Boolean = db.readableDatabase.rawQuery(
        "SELECT 1 FROM $T_TXNS WHERE category_id = ?1 UNION SELECT 1 FROM $T_RECURRING WHERE category_id = ?1 LIMIT 1",
        arrayOf(categoryId.toString()),
    ).use { it.moveToFirst() }

    fun exportData() = BackupFormat.Data(
        accounts(true, book = Books.ALL), categories(true, book = Books.ALL), txns(book = Books.ALL), recurring(book = Books.ALL),
        loans(book = Books.ALL), contacts(includeSelf = true), books(), parties(book = Books.ALL),
    )

    /** How often each category was used since [since], to put the usual ones first when adding an entry. */
    fun categoryUsage(type: TxnType, since: LocalDate): Map<Long, Int> = db.readableDatabase.rawQuery(
        "SELECT category_id, COUNT(*) FROM $T_TXNS WHERE type = ? AND day >= ? AND category_id IS NOT NULL GROUP BY category_id",
        arrayOf(type.name, since.toEpochDay().toString()),
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    // ---- Loans --------------------------------------------------------------------------------------------

    fun loans(book: Long? = null): List<Loan> {
        val b = resolve(book)
        return db.readableDatabase.query(T_LOANS, null, if (b == Books.ALL) null else "book_id = $b", null, null, null, "closed, start_day DESC, id DESC")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.loan(c)) } }
    }

    fun loan(id: Long): Loan? = db.readableDatabase.query(T_LOANS, null, "id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) MoneyDb.loan(c) else null }

    /** Which loan account is money lent and which is money borrowed, so entries can say "Given to …" or "Repaid to …". */
    fun loanDirections(): Map<Long, LoanDirection> = loans(book = Books.ALL).associate { it.accountId to it.direction }

    /** Every loan (of [book]) with its person, history and where it stands today. */
    fun loanItems(asOf: LocalDate = LocalDate.now(), book: Long? = null): List<LoanItem> {
        val loans = loans(book)
        if (loans.isEmpty()) return emptyList()
        val accounts = accounts(includeArchived = true, includeLoans = true, book = Books.ALL).associateBy { it.id }
        val contacts = contacts().associateBy { it.id }
        val byLoan = db.readableDatabase.query(T_TXNS, null, "loan_id IS NOT NULL", null, null, null, "day, created")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.txn(c)) } }.groupBy { it.loanId }
        return loans.map { loan ->
            val account = accounts[loan.accountId]
            val events = LoanMath.events(loan, account?.opening ?: 0, byLoan[loan.id].orEmpty())
            LoanItem(loan, account?.name.orEmpty(), events, LoanMath.summary(loan, events, asOf), byLoan[loan.id].orEmpty(), loan.contactId?.let { contacts[it] })
        }
    }

    fun loanItem(id: Long, asOf: LocalDate = LocalDate.now()): LoanItem? = loanItems(asOf, book = Books.ALL).firstOrNull { it.loan.id == id }

    // ---- Credit ledger (customers and suppliers) ------------------------------------------------------------

    fun parties(book: Long? = null): List<Party> {
        val b = resolve(book)
        return db.readableDatabase.query(T_PARTIES, null, if (b == Books.ALL) null else "book_id = $b", null, null, null, "id")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.party(c)) } }
    }

    /** Every customer and supplier (of [book]) with their balance and entries. */
    fun partyItems(book: Long? = null): List<PartyItem> {
        val parties = parties(book)
        if (parties.isEmpty()) return emptyList()
        val accounts = accounts(includeArchived = true, includeLoans = true, book = Books.ALL).associateBy { it.id }
        val contacts = contacts().associateBy { it.id }
        val ids = parties.joinToString(",") { it.accountId.toString() }
        val txns = db.readableDatabase.query(T_TXNS, null, "account_id IN ($ids) OR to_account_id IN ($ids)", null, null, null, "day, created")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.txn(c)) } }
        return parties.map { p ->
            val mine = txns.filter { it.accountId == p.accountId || it.toAccountId == p.accountId }
            val opening = accounts[p.accountId]?.opening ?: 0
            PartyItem(p, accounts[p.accountId]?.name.orEmpty(), p.contactId?.let { contacts[it] }, opening, Ledgers.entries(p, opening, mine), mine)
        }
    }

    fun partyItem(id: Long): PartyItem? = partyItems(Books.ALL).firstOrNull { it.party.id == id }

    /** The party an account belongs to, so an entry opened from a list goes to that customer's or supplier's page. */
    fun partyForAccount(accountId: Long): Party? = db.readableDatabase.query(T_PARTIES, null, "account_id = ?", arrayOf(accountId.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) MoneyDb.party(c) else null }

    /**
     * Adds a customer or supplier. [opening] is what was owed before the app (plus: they owe you), kept as the
     * party account's opening balance so it doesn't count as a sale or change your other balances.
     */
    suspend fun createParty(contact: Contact, role: PartyRole, bookId: Long, opening: Long): Long = write { w ->
        val contactId = saveContactIn(w, contact)
        val account = Account(
            name = uniqueAccountName(w, contact.name.trim(), null), type = AccountType.PARTY, opening = opening,
            sort = Int.MAX_VALUE / 2, bookId = bookId,
        )
        val accountId = w.insert(T_ACCOUNTS, null, MoneyDb.values(account))
        w.insert(T_PARTIES, null, MoneyDb.values(Party(accountId = accountId, contactId = contactId, role = role, bookId = bookId)))
    }

    suspend fun updateParty(party: Party, contact: Contact, role: PartyRole, bookId: Long, opening: Long) = write { w ->
        val contactId = saveContactIn(w, contact)
        w.update(T_PARTIES, MoneyDb.values(party.copy(contactId = contactId, role = role, bookId = bookId)), "id = ?", arrayOf(party.id.toString()))
        w.execSQL(
            "UPDATE $T_ACCOUNTS SET name = ?, book_id = ?, opening = ? WHERE id = ?",
            arrayOf<Any?>(uniqueAccountName(w, contact.name.trim(), party.accountId), bookId, opening, party.accountId),
        )
        if (party.contactId != null && party.contactId != contactId) deleteContactIfUnused(w, party.contactId)
    }

    /**
     * Records one line of a party's account. [gave] means you gave (goods sold on credit, or money); otherwise you
     * got (a payment, or goods bought on credit). With [categoryId] it is goods: a sale or purchase that counts in
     * profit; without, money moving between [accountId] and the party.
     */
    suspend fun savePartyEntry(
        party: Party, gave: Boolean, amount: Long, date: LocalDate, accountId: Long?, categoryId: Long?, note: String, existing: Txn? = null,
    ) = write { w ->
        val t = when {
            categoryId != null -> Txn(
                type = if (gave) TxnType.INCOME else TxnType.EXPENSE, amount = amount, date = date, categoryId = categoryId,
                accountId = party.accountId, note = note.trim(),
            )
            gave -> Txn(type = TxnType.TRANSFER, amount = amount, date = date, categoryId = null, accountId = accountId ?: return@write, toAccountId = party.accountId, note = note.trim())
            else -> Txn(type = TxnType.TRANSFER, amount = amount, date = date, categoryId = null, accountId = party.accountId, toAccountId = accountId ?: return@write, note = note.trim())
        }
        if (existing == null) w.insert(T_TXNS, null, MoneyDb.values(t))
        else w.update(T_TXNS, MoneyDb.values(t.copy(id = existing.id, createdAt = existing.createdAt)), "id = ?", arrayOf(existing.id.toString()))
    }

    /** Deletes a customer or supplier with every entry; sales and purchases recorded with them go too. */
    suspend fun deleteParty(party: Party) = write { w ->
        w.delete(T_TXNS, "account_id = ? OR to_account_id = ?", arrayOf(party.accountId.toString(), party.accountId.toString()))
        w.delete(T_PARTIES, "id = ?", arrayOf(party.id.toString()))
        w.delete(T_ACCOUNTS, "id = ?", arrayOf(party.accountId.toString()))
        party.contactId?.let { deleteContactIfUnused(w, it) }
    }

    /** The category goods sold (or bought) on credit go to by default: Sales or Purchases, else the first one. */
    fun goodsCategory(bookId: Long, sale: Boolean): Category? {
        val kind = if (sale) TxnType.INCOME else TxnType.EXPENSE
        val list = categories(book = bookId).filter { it.kind == kind }
        return list.firstOrNull { it.icon == "store" } ?: list.firstOrNull { it.icon == "other" } ?: list.firstOrNull()
    }

    /** The book's income and expense report (a business's profit and loss) for a period. */
    fun bookReport(from: LocalDate, to: LocalDate, book: Long? = null): BookReport {
        val b = resolve(book)
        return Reports.build(
            book = if (b == Books.ALL) null else books().firstOrNull { it.id == b },
            from = from, to = to,
            txns = txns(book = Books.ALL),
            categories = categories(includeArchived = true, book = Books.ALL),
            accounts = accounts(includeArchived = true, includeLoans = true, book = Books.ALL),
            loans = loanItems(to, b).map { it.loan to it.summary },
            uncategorized = context.getString(com.saraltechies.saralmoneytracker.R.string.uncategorized),
        )
    }

    // ---- Writes -------------------------------------------------------------------------------------------

    suspend fun saveTxn(t: Txn): Long = write { w ->
        if (t.id == 0L) w.insert(T_TXNS, null, MoneyDb.values(t))
        else {
            w.update(T_TXNS, MoneyDb.values(t), "id = ?", arrayOf(t.id.toString()))
            t.id
        }
    }

    suspend fun deleteTxn(id: Long) = write { it.delete(T_TXNS, "id = ?", arrayOf(id.toString())) }

    /** Puts a deleted entry back (Undo), with its original id. */
    suspend fun restoreTxn(t: Txn) = write { it.insertWithOnConflict(T_TXNS, null, MoneyDb.values(t), SQLiteDatabase.CONFLICT_REPLACE) }

    suspend fun saveAccount(a: Account): Long = write { w ->
        if (a.id == 0L) {
            val sort = w.rawQuery("SELECT COALESCE(MAX(sort), -1) + 1 FROM $T_ACCOUNTS", null).use { it.moveToFirst(); it.getInt(0) }
            w.insert(T_ACCOUNTS, null, MoneyDb.values(a.copy(sort = sort)))
        } else {
            w.update(T_ACCOUNTS, MoneyDb.values(a), "id = ?", arrayOf(a.id.toString()))
            a.id
        }
    }

    /** Deletes an unused account; one with history is hidden instead so past entries keep their account. */
    suspend fun removeAccount(id: Long): Boolean {
        val used = withContext(Dispatchers.IO) { isUsed(id) }
        write { w ->
            if (used) w.execSQL("UPDATE $T_ACCOUNTS SET archived = 1 WHERE id = ?", arrayOf(id))
            else w.delete(T_ACCOUNTS, "id = ?", arrayOf(id.toString()))
        }
        return used
    }

    suspend fun saveCategory(c: Category): Long = write { w ->
        if (c.id == 0L) {
            val sort = w.rawQuery("SELECT COALESCE(MAX(sort), -1) + 1 FROM $T_CATEGORIES WHERE kind = ? AND book_id = ?", arrayOf(c.kind.name, c.bookId.toString()))
                .use { it.moveToFirst(); it.getInt(0) }
            w.insert(T_CATEGORIES, null, MoneyDb.values(c.copy(sort = sort)))
        } else {
            w.update(T_CATEGORIES, MoneyDb.values(c), "id = ?", arrayOf(c.id.toString()))
            c.id
        }
    }

    suspend fun setBudget(categoryId: Long, budget: Long?) = write { w ->
        w.execSQL("UPDATE $T_CATEGORIES SET budget = ? WHERE id = ?", arrayOf<Any?>(budget?.takeIf { it > 0 }, categoryId))
    }

    /** Like [removeAccount]: categories with history are hidden, not deleted. */
    suspend fun removeCategory(id: Long): Boolean {
        val used = withContext(Dispatchers.IO) { isCategoryUsed(id) }
        write { w ->
            if (used) w.execSQL("UPDATE $T_CATEGORIES SET archived = 1, budget = NULL WHERE id = ?", arrayOf(id))
            else w.delete(T_CATEGORIES, "id = ?", arrayOf(id.toString()))
        }
        return used
    }

    suspend fun moveCategory(category: Category, up: Boolean) = write { w ->
        val list = w.query(T_CATEGORIES, null, "kind = ? AND archived = 0 AND book_id = ?", arrayOf(category.kind.name, category.bookId.toString()), null, null, "sort, id")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.category(c)) } }.toMutableList()
        val i = list.indexOfFirst { it.id == category.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@write
        list.add(j, list.removeAt(i))
        list.forEachIndexed { index, c -> w.execSQL("UPDATE $T_CATEGORIES SET sort = ? WHERE id = ?", arrayOf<Any?>(index, c.id)) }
    }

    suspend fun saveRecurring(r: Recurring): Long = write { w ->
        if (r.id == 0L) w.insert(T_RECURRING, null, MoneyDb.values(r))
        else {
            w.update(T_RECURRING, MoneyDb.values(r), "id = ?", arrayOf(r.id.toString()))
            r.id
        }
    }

    /** Stops a repeating entry. Entries it already added stay. */
    suspend fun deleteRecurring(id: Long) = write { w ->
        w.delete(T_RECURRING, "id = ?", arrayOf(id.toString()))
        w.execSQL("UPDATE $T_TXNS SET recurring_id = NULL WHERE recurring_id = ?", arrayOf(id))
    }

    /** Adds every repeating entry that has come due, and moves each rule to its next date. Returns how many were added. */
    suspend fun processRecurring(today: LocalDate = LocalDate.now()): Int {
        val rules = withContext(Dispatchers.IO) { recurring(book = Books.ALL) }.filter { it.active && !it.nextDate.isAfter(today) }
        if (rules.isEmpty()) return 0
        return write { w ->
            var added = 0
            for (rule in rules) {
                val dates = Recurrence.due(rule, today)
                for (date in dates) {
                    w.insert(
                        T_TXNS, null,
                        MoneyDb.values(
                            Txn(
                                type = rule.type, amount = rule.amount, date = date, categoryId = rule.categoryId,
                                accountId = rule.accountId, toAccountId = rule.toAccountId, note = rule.note, recurringId = rule.id,
                            ),
                        ),
                    )
                    added++
                }
                val next = dates.lastOrNull()?.let { Recurrence.next(rule.startDate, rule.frequency, it) } ?: rule.nextDate
                val ended = rule.endDate != null && next.isAfter(rule.endDate)
                w.update(T_RECURRING, MoneyDb.values(rule.copy(nextDate = next, active = !ended)), "id = ?", arrayOf(rule.id.toString()))
            }
            added
        }
    }

    /** Deletes everything and starts again with the default accounts and categories. */
    suspend fun deleteAll() = write { w ->
        listOf(T_TXNS, T_RECURRING, T_LOANS, T_PARTIES, T_CONTACTS, T_CATEGORIES, T_ACCOUNTS).forEach { w.delete(it, null, null) }
        w.delete(T_BOOKS, "id != ?", arrayOf(Books.PERSONAL_ID.toString()))
        w.execSQL("DELETE FROM sqlite_sequence")
        db.seed(w)
    }

    // ---- Loan writes --------------------------------------------------------------------------------------

    /**
     * Records a new loan of [amount]. With [accountId] the money leaves (or arrives in) that account today's way, as
     * a transfer; without it the loan is one from before you used the app, so your balances stay as they are.
     */
    suspend fun createLoan(terms: LoanTerms, amount: Long, accountId: Long?, contact: Contact): Long = write { w ->
        val sign = if (terms.direction == LoanDirection.LENT) 1 else -1
        val contactId = saveContactIn(w, contact.copy(name = contact.name.ifBlank { terms.name }))
        val account = Account(
            name = uniqueAccountName(w, terms.name.trim(), exceptId = null), type = AccountType.LOAN,
            opening = if (accountId == null) amount * sign else 0, sort = Int.MAX_VALUE / 2, bookId = terms.bookId,
        )
        val loanAccount = w.insert(T_ACCOUNTS, null, MoneyDb.values(account))
        val loanId = w.insert(T_LOANS, null, MoneyDb.values(loanFrom(terms, loanAccount).copy(contactId = contactId)))
        if (accountId != null) w.insert(T_TXNS, null, MoneyDb.values(loanTxn(terms.direction, loanAccount, loanId, LoanEventKind.GIVEN, amount, terms.startDate, accountId, "")))
        loanId
    }

    /** Changes the terms, and who the loan is with and their details. */
    suspend fun updateLoan(loan: Loan, terms: LoanTerms, contact: Contact) = write { w ->
        val contactId = saveContactIn(w, contact.copy(name = contact.name.ifBlank { terms.name }))
        w.update(
            T_LOANS, MoneyDb.values(loanFrom(terms, loan.accountId).copy(id = loan.id, closed = loan.closed, createdAt = loan.createdAt, contactId = contactId)),
            "id = ?", arrayOf(loan.id.toString()),
        )
        w.execSQL("UPDATE $T_ACCOUNTS SET name = ?, book_id = ? WHERE id = ?", arrayOf<Any?>(uniqueAccountName(w, terms.name.trim(), loan.accountId), terms.bookId, loan.accountId))
        // The old contact goes once nothing uses it.
        if (loan.contactId != null && loan.contactId != contactId) deleteContactIfUnused(w, loan.contactId)
    }

    // ---- Contacts -----------------------------------------------------------------------------------------

    /** People, banks and vendors, by name. Your own details ([ContactKind.SELF]) only with [includeSelf]. */
    fun contacts(includeSelf: Boolean = false): List<Contact> = db.readableDatabase.query(
        T_CONTACTS, null, if (includeSelf) null else "kind != '${ContactKind.SELF.name}'", null, null, null, "name COLLATE NOCASE, id",
    ).use { c -> buildList { while (c.moveToNext()) add(MoneyDb.contact(c)) } }

    /** Your own details for statements: name or business, address, bank and UPI. */
    fun myDetails(book: Long? = null): Contact? = db.readableDatabase.query(
        T_CONTACTS, null, "kind = ? AND book_id = ?", arrayOf(ContactKind.SELF.name, (resolve(book).takeIf { it != Books.ALL } ?: Books.PERSONAL_ID).toString()), null, null, "id", "1",
    )
        .use { c -> if (c.moveToFirst()) MoneyDb.contact(c) else null }

    suspend fun saveMyDetails(details: Contact) = write { w ->
        val existing = w.query(T_CONTACTS, arrayOf("id"), "kind = ? AND book_id = ?", arrayOf(ContactKind.SELF.name, details.bookId.toString()), null, null, "id", "1")
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        saveContactIn(w, details.copy(id = existing, kind = ContactKind.SELF))
    }

    /** Saves a contact and keeps the names of its loans' accounts in step. Returns its id. */
    private fun saveContactIn(w: SQLiteDatabase, contact: Contact): Long {
        val c = contact.trimmed()
        if (c.id == 0L) return w.insert(T_CONTACTS, null, MoneyDb.values(c))
        w.update(T_CONTACTS, MoneyDb.values(c), "id = ?", arrayOf(c.id.toString()))
        if (c.kind != ContactKind.SELF) {
            val accounts = w.rawQuery("SELECT account_id FROM $T_LOANS WHERE contact_id = ?", arrayOf(c.id.toString()))
                .use { cur -> buildList { while (cur.moveToNext()) add(cur.getLong(0)) } }
            for (a in accounts) w.execSQL("UPDATE $T_ACCOUNTS SET name = ? WHERE id = ?", arrayOf<Any?>(uniqueAccountName(w, c.name, a), a))
        }
        return c.id
    }

    private fun deleteContactIfUnused(w: SQLiteDatabase, contactId: Long) {
        val used = w.rawQuery(
            "SELECT 1 FROM $T_LOANS WHERE contact_id = ?1 UNION SELECT 1 FROM $T_PARTIES WHERE contact_id = ?1 LIMIT 1", arrayOf(contactId.toString()),
        ).use { it.moveToFirst() }
        if (!used) w.delete(T_CONTACTS, "id = ? AND kind != ?", arrayOf(contactId.toString(), ContactKind.SELF.name))
    }

    /**
     * Adds money given, a repayment, or interest. A payment that covers both interest and principal is two entries,
     * so interest shows up in your income (or spending) and the principal doesn't.
     */
    suspend fun addLoanEntries(loan: Loan, entries: List<Pair<LoanEventKind, Long>>, date: LocalDate, accountId: Long, note: String) = write { w ->
        for ((kind, amount) in entries) {
            if (amount <= 0) continue
            val categoryId = if (kind == LoanEventKind.INTEREST) interestCategory(w, loan.direction, loan.bookId) else null
            w.insert(T_TXNS, null, MoneyDb.values(loanTxn(loan.direction, loan.accountId, loan.id, kind, amount, date, accountId, note.trim(), categoryId)))
        }
    }

    /** Changes one entry of a loan's history. [kind] decides which way it goes, as when it was added. */
    suspend fun updateLoanEntry(loan: Loan, txn: Txn, kind: LoanEventKind, amount: Long, date: LocalDate, accountId: Long, note: String) = write { w ->
        val categoryId = if (kind == LoanEventKind.INTEREST) (txn.categoryId ?: interestCategory(w, loan.direction, loan.bookId)) else null
        val updated = loanTxn(loan.direction, loan.accountId, loan.id, kind, amount, date, accountId, note.trim(), categoryId).copy(id = txn.id, createdAt = txn.createdAt)
        w.update(T_TXNS, MoneyDb.values(updated), "id = ?", arrayOf(txn.id.toString()))
    }

    /** The amount of a loan given before using the app (kept as the loan account's opening balance). */
    suspend fun setLoanOpening(loan: Loan, amount: Long) = write { w ->
        val sign = if (loan.direction == LoanDirection.LENT) 1 else -1
        w.execSQL("UPDATE $T_ACCOUNTS SET opening = ? WHERE id = ?", arrayOf<Any?>(amount * sign, loan.accountId))
    }

    suspend fun setLoanClosed(id: Long, closed: Boolean) = write { w ->
        w.execSQL("UPDATE $T_LOANS SET closed = ? WHERE id = ?", arrayOf<Any?>(if (closed) 1 else 0, id))
    }

    /** Deletes a loan with its whole history; your account balances go back to how they'd be without it. */
    suspend fun deleteLoan(loan: Loan) = write { w ->
        w.delete(T_TXNS, "loan_id = ?", arrayOf(loan.id.toString()))
        w.delete(T_LOANS, "id = ?", arrayOf(loan.id.toString()))
        w.delete(T_ACCOUNTS, "id = ?", arrayOf(loan.accountId.toString()))
        loan.contactId?.let { deleteContactIfUnused(w, it) }
    }

    private fun loanFrom(terms: LoanTerms, accountId: Long) = Loan(
        accountId = accountId, direction = terms.direction, startDate = terms.startDate, rate = terms.rate.coerceAtLeast(0), rateUnit = terms.rateUnit, bookId = terms.bookId,
        plan = terms.plan, installments = terms.installments.coerceAtLeast(0), installment = terms.installment.coerceAtLeast(0), note = terms.note.trim(),
    )

    /**
     * Money given into a lent loan's account, or out of a borrowed loan's account into yours; repayments the other
     * way round; interest as income (lent) or expense (borrowed).
     */
    private fun loanTxn(
        direction: LoanDirection, loanAccount: Long, loanId: Long, kind: LoanEventKind, amount: Long, date: LocalDate, accountId: Long, note: String,
        categoryId: Long? = null,
    ): Txn {
        val intoLoan = (kind == LoanEventKind.GIVEN) == (direction == LoanDirection.LENT)
        return when (kind) {
            LoanEventKind.INTEREST -> Txn(
                type = if (direction == LoanDirection.LENT) TxnType.INCOME else TxnType.EXPENSE, amount = amount, date = date,
                categoryId = categoryId, accountId = accountId, note = note, loanId = loanId,
            )
            else -> Txn(
                type = TxnType.TRANSFER, amount = amount, date = date, categoryId = null,
                accountId = if (intoLoan) accountId else loanAccount, toAccountId = if (intoLoan) loanAccount else accountId, note = note, loanId = loanId,
            )
        }
    }

    /** The category interest goes to: one you already have for interest, or a new "Interest received" / "Interest paid". */
    private fun interestCategory(w: SQLiteDatabase, direction: LoanDirection, bookId: Long): Long {
        val kind = if (direction == LoanDirection.LENT) TxnType.INCOME else TxnType.EXPENSE
        val all = w.query(T_CATEGORIES, null, "kind = ? AND archived = 0 AND book_id = ?", arrayOf(kind.name, bookId.toString()), null, null, "sort, id")
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.category(c)) } }
        all.firstOrNull { it.icon == INTEREST_ICON }?.let { return it.id }
        all.firstOrNull { it.name.contains("interest", ignoreCase = true) }?.let { return it.id }
        val name = context.getString(if (direction == LoanDirection.LENT) com.saraltechies.saralmoneytracker.R.string.interest_received else com.saraltechies.saralmoneytracker.R.string.interest_paid)
        val color = DefaultData.palette[if (direction == LoanDirection.LENT) 2 else 5]
        return w.insert(T_CATEGORIES, null, MoneyDb.values(Category(name = name, kind = kind, icon = INTEREST_ICON, color = color, sort = all.size, bookId = bookId)))
    }

    /** Two loans to people with the same name get "Ravi" and "Ravi (2)", so backups can tell them apart. */
    private fun uniqueAccountName(w: SQLiteDatabase, base: String, exceptId: Long?): String {
        val taken = w.rawQuery("SELECT id, name FROM $T_ACCOUNTS", null).use { c ->
            buildSet { while (c.moveToNext()) if (c.getLong(0) != exceptId) add(c.getString(1).trim().lowercase(Locale.ROOT)) }
        }
        val name = base.ifBlank { "?" }
        if (name.lowercase(Locale.ROOT) !in taken) return name
        var n = 2
        while ("$name ($n)".lowercase(Locale.ROOT) in taken) n++
        return "$name ($n)"
    }

    // ---- Import -------------------------------------------------------------------------------------------

    suspend fun applyImport(result: ImportResult, options: ImportOptions): ImportReport = write { w ->
        val accounts = w.query(T_ACCOUNTS, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.account(c)) } }.toMutableList()
        val categories = w.query(T_CATEGORIES, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.category(c)) } }.toMutableList()
        var accountsAdded = 0
        var categoriesAdded = 0
        var budgetsSet = 0

        fun key(s: String) = s.trim().lowercase(Locale.ROOT)

        // Books named in a backup are matched by name, and added when missing; anything without one goes to the chosen book.
        val books = w.query(T_BOOKS, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.book(c)) } }.toMutableList()
        fun bookOf(name: String?): Long {
            if (name.isNullOrBlank()) return options.bookId
            books.firstOrNull { key(it.name) == key(name) }?.let { return it.id }
            val kind = result.books.firstOrNull { key(it.name) == key(name) }?.kind ?: BookKind.BUSINESS
            val book = Book(name = name.trim(), kind = kind, sort = books.size)
            val id = w.insert(T_BOOKS, null, MoneyDb.values(book))
            books += book.copy(id = id)
            return id
        }

        fun account(name: String, type: AccountType? = null, opening: Long = 0, archived: Boolean = false, book: Long = options.bookId): Long {
            // Two books can each have a "Cash": prefer the one in the book the row belongs to.
            (accounts.firstOrNull { key(it.name) == key(name) && it.bookId == book } ?: accounts.firstOrNull { key(it.name) == key(name) })?.let { return it.id }
            val a = Account(name = name.trim(), type = type ?: guessAccountType(name), opening = opening, archived = archived, sort = accounts.size, bookId = book)
            val id = w.insert(T_ACCOUNTS, null, MoneyDb.values(a))
            accounts += a.copy(id = id)
            accountsAdded++
            return id
        }

        fun category(name: String, kind: TxnType, icon: String? = null, color: Int? = null, book: Long = options.bookId): Long {
            val wanted = name.ifBlank { if (kind == TxnType.INCOME) options.otherIncomeName else options.otherExpenseName }
            categories.firstOrNull { it.kind == kind && it.bookId == book && key(it.name) == key(wanted) }?.let { return it.id }
            if (name.isBlank()) categories.firstOrNull { it.kind == kind && it.bookId == book && it.icon == "other" && !it.archived }?.let { return it.id }
            val c = Category(
                name = wanted.trim(), kind = kind, icon = icon ?: "other",
                color = color ?: DefaultData.palette[Math.floorMod(key(wanted).hashCode(), DefaultData.palette.size)],
                sort = categories.count { it.kind == kind && it.bookId == book }, bookId = book,
            )
            val id = w.insert(T_CATEGORIES, null, MoneyDb.values(c))
            categories += c.copy(id = id)
            categoriesAdded++
            return id
        }

        val cardNames = if (options.cardBillsAsTransfers) cardBillCategories(result) else emptySet()

        for (a in result.accounts) account(a.name, a.type, a.opening, a.archived, bookOf(a.book))

        // Contacts from a backup, matched to ones already here by name. Your own details only fill in if you have none.
        val existingContacts = w.query(T_CONTACTS, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.contact(c)) } }
        val contactIds = HashMap<String, Long>()
        for ((index, c) in result.contacts.withIndex()) {
            val id = if (c.kind == ContactKind.SELF) {
                val book = bookOf(result.contactBooks.getOrNull(index))
                existingContacts.firstOrNull { it.kind == ContactKind.SELF && it.bookId == book }?.id
                    ?: w.insert(T_CONTACTS, null, MoneyDb.values(c.trimmed().copy(bookId = book)))
            } else {
                existingContacts.firstOrNull { it.kind != ContactKind.SELF && key(it.name) == key(c.name) }?.id
                    ?: w.insert(T_CONTACTS, null, MoneyDb.values(c.trimmed()))
            }
            if (c.kind != ContactKind.SELF) contactIds[key(c.name)] = id
        }
        // Loans from a backup: each has its own account (already created above); link to it, or reuse the same loan.
        val loansByAccount = w.query(T_LOANS, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.loan(c)) } }.associateBy { it.accountId }.toMutableMap()
        val loanIds = HashMap<String, Long>()
        for (l in result.loans) {
            val accountId = account(l.name, AccountType.LOAN)
            val id = loansByAccount[accountId]?.id ?: w.insert(
                T_LOANS, null,
                MoneyDb.values(
                    Loan(
                        accountId = accountId, direction = l.direction, startDate = l.startDate, rate = l.rate, rateUnit = l.rateUnit, plan = l.plan,
                        installments = l.installments, installment = l.installment, note = l.note, closed = l.closed, bookId = bookOf(l.book),
                        contactId = (l.contact ?: l.name).let { contactIds[key(it)] } ?: w.insert(T_CONTACTS, null, MoneyDb.values(Contact(name = l.name))),
                    ),
                ),
            ).also { id -> loansByAccount[accountId] = Loan(id = id, accountId = accountId, direction = l.direction, startDate = l.startDate) }
            loanIds[key(l.name)] = id
        }
        // Customers and suppliers from a backup: their account (created above, type Party) and contact.
        val partiesByAccount = w.query(T_PARTIES, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.party(c)) } }.associateBy { it.accountId }.toMutableMap()
        for (p in result.parties) {
            val book = bookOf(p.book)
            val accountId = account(p.name, AccountType.PARTY, book = book)
            if (partiesByAccount.containsKey(accountId)) continue
            val contactId = (p.contact ?: p.name).let { contactIds[key(it)] } ?: w.insert(T_CONTACTS, null, MoneyDb.values(Contact(name = p.name)))
            val party = Party(accountId = accountId, contactId = contactId, role = p.role, bookId = book)
            partiesByAccount[accountId] = party.copy(id = w.insert(T_PARTIES, null, MoneyDb.values(party)))
        }
        fun loanOf(row: com.saraltechies.saralmoneytracker.xlsx.ImportedRow, from: Long, to: Long?): Long? =
            row.loan?.let { loanIds[key(it)] } ?: loansByAccount[from]?.id ?: to?.let { loansByAccount[it]?.id }
        for (c in result.categories) {
            if (c.kind == TxnType.EXPENSE && key(c.name) in cardNames) continue
            val id = category(c.name, c.kind, c.icon, c.color, bookOf(c.book))
            if (c.budget != null && (options.useBudgets || result.isBackup) && c.kind == TxnType.EXPENSE) {
                w.execSQL("UPDATE $T_CATEGORIES SET budget = ? WHERE id = ?", arrayOf(c.budget, id))
                budgetsSet++
            }
        }

        // Existing entries, to skip rows imported before.
        val existing = HashSet<String>()
        w.rawQuery("SELECT type, day, amount, category_id, note, account_id, to_account_id FROM $T_TXNS", null).use { c ->
            while (c.moveToNext()) {
                existing += listOf(
                    c.getString(0), c.getLong(1), c.getLong(2), if (c.isNull(3)) "" else c.getLong(3), key(c.getString(4) ?: ""), c.getLong(5), if (c.isNull(6)) "" else c.getLong(6),
                ).joinToString("|")
            }
        }

        var added = 0
        var duplicates = result.duplicatesInFile
        var transfers = 0
        var cardAccount: Long? = null
        for (row in result.rows) {
            val from = row.account?.let { account(it, book = bookOf(row.book)) } ?: options.defaultAccountId
            val txn = when {
                row.type == TxnType.EXPENSE && key(row.category) in cardNames -> {
                    val card = cardAccount ?: accounts.firstOrNull { it.type == AccountType.CARD && !it.archived }?.id
                        ?: account(options.cardAccountName, AccountType.CARD)
                    cardAccount = card
                    transfers++
                    Txn(type = TxnType.TRANSFER, amount = row.amount, date = row.date, categoryId = null, accountId = from, toAccountId = card, note = row.note)
                }
                row.type == TxnType.TRANSFER -> {
                    val to = row.toAccount?.let { account(it, book = bookOf(row.book)) } ?: continue
                    Txn(type = TxnType.TRANSFER, amount = row.amount, date = row.date, categoryId = null, accountId = from, toAccountId = to, note = row.note, loanId = loanOf(row, from, to))
                }
                else -> Txn(type = row.type, amount = row.amount, date = row.date, categoryId = category(row.category, row.type, book = bookOf(row.book)), accountId = from, note = row.note, loanId = row.loan?.let { loanIds[key(it)] })
            }
            // Accounts are part of the key: the same amount on the same day can be two real payments (say, to two people).
            val k = listOf(txn.type.name, txn.date.toEpochDay(), txn.amount, txn.categoryId ?: "", key(txn.note), txn.accountId, txn.toAccountId ?: "").joinToString("|")
            if (!existing.add(k)) {
                duplicates++
                continue
            }
            w.insert(T_TXNS, null, MoneyDb.values(txn))
            added++
        }

        var recurringAdded = 0
        val existingRules = w.query(T_RECURRING, null, null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.recurring(c)) } }
        for (r in result.recurring) {
            val rule = Recurring(
                type = r.type, amount = r.amount,
                categoryId = if (r.type == TxnType.TRANSFER) null else category(r.category, r.type, book = bookOf(r.book)),
                accountId = r.account?.let { account(it, book = bookOf(r.book)) } ?: options.defaultAccountId,
                toAccountId = r.toAccount?.let { account(it, book = bookOf(r.book)) },
                note = r.note, frequency = r.frequency, startDate = r.startDate, nextDate = r.nextDate, endDate = r.endDate, active = r.active,
            )
            if (existingRules.any { it.copy(id = 0) == rule }) continue
            w.insert(T_RECURRING, null, MoneyDb.values(rule))
            recurringAdded++
        }
        ImportReport(added, duplicates, transfers, categoriesAdded, accountsAdded, budgetsSet, recurringAdded)
    }

    companion object {
        /** Icon key of the categories interest on loans goes to. */
        const val INTEREST_ICON = "interest"

        /** Expense categories in the file that are really credit card bill payments. */
        fun cardBillCategories(result: ImportResult): Set<String> =
            result.rows.filter { it.type == TxnType.EXPENSE && isCardBill(it.category) }
                .map { it.category.trim().lowercase(Locale.ROOT) }.toSet()

        fun isCardBill(category: String): Boolean {
            val c = category.trim().lowercase(Locale.ROOT)
            return c == "credit card" || c == "credit cards" || c.contains("card bill") || c.contains("credit card payment") || c == "cc bill"
        }

        fun guessAccountType(name: String): AccountType {
            val n = name.lowercase(Locale.ROOT)
            return when {
                "card" in n -> AccountType.CARD
                "cash" in n -> AccountType.CASH
                listOf("upi", "paytm", "phonepe", "gpay", "google pay", "wallet", "amazon pay").any { it in n } -> AccountType.WALLET
                else -> AccountType.BANK
            }
        }
    }
}
