package com.saraltechies.saralmoneytracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_ACCOUNTS
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_CATEGORIES
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_RECURRING
import com.saraltechies.saralmoneytracker.data.MoneyDb.Companion.T_TXNS
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.DefaultData
import com.saraltechies.saralmoneytracker.model.Recurrence
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Stats
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import com.saraltechies.saralmoneytracker.xlsx.BackupFormat
import com.saraltechies.saralmoneytracker.xlsx.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
)

data class ImportReport(val added: Int, val duplicates: Int, val transfers: Int, val categoriesAdded: Int, val accountsAdded: Int, val budgetsSet: Int, val recurringAdded: Int)

/**
 * All reads and writes. Reads are plain blocking calls meant to run inside [watch] (which re-runs them on a
 * background thread after every change), so screens stay in sync without a database framework.
 */
class MoneyRepository(context: Context) {

    private val db = MoneyDb(context)
    private val version = MutableStateFlow(0L)

    fun <T> watch(read: () -> T): Flow<T> = version.map { withContext(Dispatchers.IO) { read() } }

    private fun changed() = version.update { it + 1 }

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

    fun accounts(includeArchived: Boolean = false): List<Account> = db.readableDatabase.query(
        T_ACCOUNTS, null, if (includeArchived) null else "archived = 0", null, null, null, "sort, id",
    ).use { c -> buildList { while (c.moveToNext()) add(MoneyDb.account(c)) } }

    fun categories(includeArchived: Boolean = false): List<Category> = db.readableDatabase.query(
        T_CATEGORIES, null, if (includeArchived) null else "archived = 0", null, null, null, "sort, id",
    ).use { c -> buildList { while (c.moveToNext()) add(MoneyDb.category(c)) } }

    /** Newest first. [query] matches the note or the category name. */
    fun txns(
        from: LocalDate? = null,
        to: LocalDate? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        query: String? = null,
        limit: Int? = null,
    ): List<Txn> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        from?.let { where += "day >= ?"; args += it.toEpochDay().toString() }
        to?.let { where += "day <= ?"; args += it.toEpochDay().toString() }
        accountId?.let { where += "(account_id = ? OR to_account_id = ?)"; args += it.toString(); args += it.toString() }
        categoryId?.let { where += "category_id = ?"; args += it.toString() }
        if (!query.isNullOrBlank()) {
            val q = query.trim()
            val catIds = categories(includeArchived = true).filter { it.name.contains(q, ignoreCase = true) }.map { it.id }
            val catClause = if (catIds.isEmpty()) "" else " OR category_id IN (${catIds.joinToString(",")})"
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

    fun recurring(): List<Recurring> = db.readableDatabase.query(T_RECURRING, null, null, null, null, null, "active DESC, next_day")
        .use { c -> buildList { while (c.moveToNext()) add(MoneyDb.recurring(c)) } }

    fun recurringRule(id: Long): Recurring? = db.readableDatabase.query(T_RECURRING, null, "id = ?", arrayOf(id.toString()), null, null, null)
        .use { c -> if (c.moveToFirst()) MoneyDb.recurring(c) else null }

    /** Current balance of every account, hidden ones included. */
    fun balances(): Map<Long, Long> = Stats.balances(accounts(includeArchived = true), txns())

    fun txnCount(): Int = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $T_TXNS", null).use { it.moveToFirst(); it.getInt(0) }

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

    fun exportData() = BackupFormat.Data(accounts(true), categories(true), txns(), recurring())

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
            val sort = w.rawQuery("SELECT COALESCE(MAX(sort), -1) + 1 FROM $T_CATEGORIES WHERE kind = ?", arrayOf(c.kind.name))
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
        val list = w.query(T_CATEGORIES, null, "kind = ? AND archived = 0", arrayOf(category.kind.name), null, null, "sort, id")
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
        val rules = withContext(Dispatchers.IO) { recurring() }.filter { it.active && !it.nextDate.isAfter(today) }
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
        listOf(T_TXNS, T_RECURRING, T_CATEGORIES, T_ACCOUNTS).forEach { w.delete(it, null, null) }
        w.execSQL("DELETE FROM sqlite_sequence")
        db.seed(w)
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

        fun account(name: String, type: AccountType? = null, opening: Long = 0, archived: Boolean = false): Long {
            accounts.firstOrNull { key(it.name) == key(name) }?.let { return it.id }
            val a = Account(name = name.trim(), type = type ?: guessAccountType(name), opening = opening, archived = archived, sort = accounts.size)
            val id = w.insert(T_ACCOUNTS, null, MoneyDb.values(a))
            accounts += a.copy(id = id)
            accountsAdded++
            return id
        }

        fun category(name: String, kind: TxnType, icon: String? = null, color: Int? = null): Long {
            val wanted = name.ifBlank { if (kind == TxnType.INCOME) options.otherIncomeName else options.otherExpenseName }
            categories.firstOrNull { it.kind == kind && key(it.name) == key(wanted) }?.let { return it.id }
            if (name.isBlank()) categories.firstOrNull { it.kind == kind && it.icon == "other" && !it.archived }?.let { return it.id }
            val c = Category(
                name = wanted.trim(), kind = kind, icon = icon ?: "other",
                color = color ?: DefaultData.palette[Math.floorMod(key(wanted).hashCode(), DefaultData.palette.size)],
                sort = categories.count { it.kind == kind },
            )
            val id = w.insert(T_CATEGORIES, null, MoneyDb.values(c))
            categories += c.copy(id = id)
            categoriesAdded++
            return id
        }

        val cardNames = if (options.cardBillsAsTransfers) cardBillCategories(result) else emptySet()

        for (a in result.accounts) account(a.name, a.type, a.opening, a.archived)
        for (c in result.categories) {
            if (c.kind == TxnType.EXPENSE && key(c.name) in cardNames) continue
            val id = category(c.name, c.kind, c.icon, c.color)
            if (c.budget != null && (options.useBudgets || result.isBackup) && c.kind == TxnType.EXPENSE) {
                w.execSQL("UPDATE $T_CATEGORIES SET budget = ? WHERE id = ?", arrayOf(c.budget, id))
                budgetsSet++
            }
        }

        // Existing entries, to skip rows imported before.
        val existing = HashSet<String>()
        w.rawQuery("SELECT type, day, amount, category_id, note FROM $T_TXNS", null).use { c ->
            while (c.moveToNext()) {
                existing += listOf(c.getString(0), c.getLong(1), c.getLong(2), if (c.isNull(3)) "" else c.getLong(3), key(c.getString(4) ?: "")).joinToString("|")
            }
        }

        var added = 0
        var duplicates = result.duplicatesInFile
        var transfers = 0
        var cardAccount: Long? = null
        for (row in result.rows) {
            val from = row.account?.let { account(it) } ?: options.defaultAccountId
            val txn = when {
                row.type == TxnType.EXPENSE && key(row.category) in cardNames -> {
                    val card = cardAccount ?: accounts.firstOrNull { it.type == AccountType.CARD && !it.archived }?.id
                        ?: account(options.cardAccountName, AccountType.CARD)
                    cardAccount = card
                    transfers++
                    Txn(type = TxnType.TRANSFER, amount = row.amount, date = row.date, categoryId = null, accountId = from, toAccountId = card, note = row.note)
                }
                row.type == TxnType.TRANSFER -> {
                    val to = row.toAccount?.let { account(it) } ?: continue
                    Txn(type = TxnType.TRANSFER, amount = row.amount, date = row.date, categoryId = null, accountId = from, toAccountId = to, note = row.note)
                }
                else -> Txn(type = row.type, amount = row.amount, date = row.date, categoryId = category(row.category, row.type), accountId = from, note = row.note)
            }
            val k = listOf(txn.type.name, txn.date.toEpochDay(), txn.amount, txn.categoryId ?: "", key(txn.note)).joinToString("|")
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
                categoryId = if (r.type == TxnType.TRANSFER) null else category(r.category, r.type),
                accountId = r.account?.let { account(it) } ?: options.defaultAccountId,
                toAccountId = r.toAccount?.let { account(it) },
                note = r.note, frequency = r.frequency, startDate = r.startDate, nextDate = r.nextDate, endDate = r.endDate, active = r.active,
            )
            if (existingRules.any { it.copy(id = 0) == rule }) continue
            w.insert(T_RECURRING, null, MoneyDb.values(rule))
            recurringAdded++
        }
        ImportReport(added, duplicates, transfers, categoriesAdded, accountsAdded, budgetsSet, recurringAdded)
    }

    companion object {
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
