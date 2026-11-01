package com.saraltechies.saralmoneytracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Account
import com.saraltechies.saralmoneytracker.model.AccountType
import com.saraltechies.saralmoneytracker.model.Category
import com.saraltechies.saralmoneytracker.model.DefaultData
import com.saraltechies.saralmoneytracker.model.Frequency
import com.saraltechies.saralmoneytracker.model.Loan
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.Recurring
import com.saraltechies.saralmoneytracker.model.Txn
import com.saraltechies.saralmoneytracker.model.TxnType
import java.time.LocalDate

/**
 * The on-device database. Plain SQLite keeps the app small and free of code generation; there are only five tables.
 * Dates are stored as epoch days, amounts as hundredths.
 */
class MoneyDb(private val context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL,
                opening INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL, icon TEXT NOT NULL,
                color INTEGER NOT NULL, budget INTEGER, archived INTEGER NOT NULL DEFAULT 0, sort INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE txns (
                id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, day INTEGER NOT NULL,
                category_id INTEGER, account_id INTEGER NOT NULL, to_account_id INTEGER, note TEXT NOT NULL DEFAULT '',
                recurring_id INTEGER, created INTEGER NOT NULL, loan_id INTEGER)""",
        )
        db.execSQL("CREATE INDEX txns_day ON txns(day)")
        createLoans(db)
        db.execSQL(
            """CREATE TABLE recurring (
                id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, amount INTEGER NOT NULL, category_id INTEGER,
                account_id INTEGER NOT NULL, to_account_id INTEGER, note TEXT NOT NULL DEFAULT '', frequency TEXT NOT NULL,
                start_day INTEGER NOT NULL, next_day INTEGER NOT NULL, end_day INTEGER, active INTEGER NOT NULL DEFAULT 1)""",
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE txns ADD COLUMN loan_id INTEGER")
            createLoans(db)
        }
    }

    /** Version 2: money lent and borrowed. */
    private fun createLoans(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE loans (
                id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL, direction TEXT NOT NULL, start_day INTEGER NOT NULL,
                rate INTEGER NOT NULL DEFAULT 0, rate_unit TEXT NOT NULL, plan TEXT NOT NULL, installments INTEGER NOT NULL DEFAULT 0,
                installment INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', closed INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE INDEX txns_loan ON txns(loan_id)")
    }

    fun seed(db: SQLiteDatabase) {
        val res = context.resources
        val labels = DefaultData.Labels(
            expense = res.getStringArray(R.array.default_expense_categories).toList(),
            income = res.getStringArray(R.array.default_income_categories).toList(),
            cash = res.getString(R.string.default_account_cash),
            bank = res.getString(R.string.default_account_bank),
        )
        DefaultData.accounts(labels).forEach { db.insert(T_ACCOUNTS, null, values(it)) }
        DefaultData.categories(labels).forEach { db.insert(T_CATEGORIES, null, values(it)) }
    }

    companion object {
        const val NAME = "money.db"
        const val VERSION = 2
        const val T_ACCOUNTS = "accounts"
        const val T_CATEGORIES = "categories"
        const val T_TXNS = "txns"
        const val T_RECURRING = "recurring"
        const val T_LOANS = "loans"

        fun values(a: Account) = ContentValues().apply {
            if (a.id != 0L) put("id", a.id)
            put("name", a.name)
            put("type", a.type.name)
            put("opening", a.opening)
            put("archived", if (a.archived) 1 else 0)
            put("sort", a.sort)
        }

        fun values(c: Category) = ContentValues().apply {
            if (c.id != 0L) put("id", c.id)
            put("name", c.name)
            put("kind", c.kind.name)
            put("icon", c.icon)
            put("color", c.color)
            if (c.budget != null) put("budget", c.budget) else putNull("budget")
            put("archived", if (c.archived) 1 else 0)
            put("sort", c.sort)
        }

        fun values(t: Txn) = ContentValues().apply {
            if (t.id != 0L) put("id", t.id)
            put("type", t.type.name)
            put("amount", t.amount)
            put("day", t.date.toEpochDay())
            if (t.categoryId != null) put("category_id", t.categoryId) else putNull("category_id")
            put("account_id", t.accountId)
            if (t.toAccountId != null) put("to_account_id", t.toAccountId) else putNull("to_account_id")
            put("note", t.note)
            if (t.recurringId != null) put("recurring_id", t.recurringId) else putNull("recurring_id")
            put("created", if (t.createdAt != 0L) t.createdAt else System.currentTimeMillis())
            if (t.loanId != null) put("loan_id", t.loanId) else putNull("loan_id")
        }

        fun values(r: Recurring) = ContentValues().apply {
            if (r.id != 0L) put("id", r.id)
            put("type", r.type.name)
            put("amount", r.amount)
            if (r.categoryId != null) put("category_id", r.categoryId) else putNull("category_id")
            put("account_id", r.accountId)
            if (r.toAccountId != null) put("to_account_id", r.toAccountId) else putNull("to_account_id")
            put("note", r.note)
            put("frequency", r.frequency.name)
            put("start_day", r.startDate.toEpochDay())
            put("next_day", r.nextDate.toEpochDay())
            if (r.endDate != null) put("end_day", r.endDate.toEpochDay()) else putNull("end_day")
            put("active", if (r.active) 1 else 0)
        }

        fun values(l: Loan) = ContentValues().apply {
            if (l.id != 0L) put("id", l.id)
            put("account_id", l.accountId)
            put("direction", l.direction.name)
            put("start_day", l.startDate.toEpochDay())
            put("rate", l.rate)
            put("rate_unit", l.rateUnit.name)
            put("plan", l.plan.name)
            put("installments", l.installments)
            put("installment", l.installment)
            put("note", l.note)
            put("closed", if (l.closed) 1 else 0)
            put("created", if (l.createdAt != 0L) l.createdAt else System.currentTimeMillis())
        }

        fun loan(c: Cursor) = Loan(
            id = c.long("id"),
            accountId = c.long("account_id"),
            direction = if (c.str("direction") == LoanDirection.BORROWED.name) LoanDirection.BORROWED else LoanDirection.LENT,
            startDate = LocalDate.ofEpochDay(c.long("start_day")),
            rate = c.long("rate"),
            rateUnit = if (c.str("rate_unit") == RateUnit.YEAR.name) RateUnit.YEAR else RateUnit.MONTH,
            plan = if (c.str("plan") == LoanPlan.MONTHLY.name) LoanPlan.MONTHLY else LoanPlan.FLEXIBLE,
            installments = c.int("installments"),
            installment = c.long("installment"),
            note = c.str("note"),
            closed = c.int("closed") != 0,
            createdAt = c.long("created"),
        )

        private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
        private fun Cursor.longOrNull(name: String) = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
        private fun Cursor.str(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
        private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))

        fun account(c: Cursor) = Account(
            id = c.long("id"),
            name = c.str("name"),
            type = AccountType.entries.firstOrNull { it.name == c.str("type") } ?: AccountType.OTHER,
            opening = c.long("opening"),
            archived = c.int("archived") != 0,
            sort = c.int("sort"),
        )

        fun category(c: Cursor) = Category(
            id = c.long("id"),
            name = c.str("name"),
            kind = if (c.str("kind") == TxnType.INCOME.name) TxnType.INCOME else TxnType.EXPENSE,
            icon = c.str("icon"),
            color = c.int("color"),
            budget = c.longOrNull("budget"),
            archived = c.int("archived") != 0,
            sort = c.int("sort"),
        )

        fun txn(c: Cursor) = Txn(
            id = c.long("id"),
            type = TxnType.valueOf(c.str("type")),
            amount = c.long("amount"),
            date = LocalDate.ofEpochDay(c.long("day")),
            categoryId = c.longOrNull("category_id"),
            accountId = c.long("account_id"),
            toAccountId = c.longOrNull("to_account_id"),
            note = c.str("note"),
            recurringId = c.longOrNull("recurring_id"),
            createdAt = c.long("created"),
            loanId = c.longOrNull("loan_id"),
        )

        fun recurring(c: Cursor) = Recurring(
            id = c.long("id"),
            type = TxnType.valueOf(c.str("type")),
            amount = c.long("amount"),
            categoryId = c.longOrNull("category_id"),
            accountId = c.long("account_id"),
            toAccountId = c.longOrNull("to_account_id"),
            note = c.str("note"),
            frequency = Frequency.entries.firstOrNull { it.name == c.str("frequency") } ?: Frequency.MONTHLY,
            startDate = LocalDate.ofEpochDay(c.long("start_day")),
            nextDate = LocalDate.ofEpochDay(c.long("next_day")),
            endDate = c.longOrNull("end_day")?.let(LocalDate::ofEpochDay),
            active = c.int("active") != 0,
        )
    }
}
