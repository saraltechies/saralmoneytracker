package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate

data class ReportLine(val categoryId: Long?, val name: String, val amount: Long)

/**
 * A book's income and expense report (a business's profit and loss) for a period, with money moved in from or out
 * to your other books, account balances at the end, and loans still to receive or pay.
 */
data class BookReport(
    val book: Book?,
    val from: LocalDate,
    val to: LocalDate,
    val income: List<ReportLine>,
    val expenses: List<ReportLine>,
    /** Money moved in from your other books in the period (for a business: capital you put in). */
    val fromOtherBooks: Long,
    /** Money moved out to your other books (for a business: drawings). */
    val toOtherBooks: Long,
    val balances: List<Pair<Account, Long>>,
    val loansToReceive: Long,
    val loansToPay: Long,
    /** Customers' credit balances still to collect (from the credit ledger). */
    val customersOwe: Long = 0,
    /** What you owe suppliers on credit. */
    val oweSuppliers: Long = 0,
) {
    val totalIncome: Long get() = income.sumOf { it.amount }
    val totalExpenses: Long get() = expenses.sumOf { it.amount }
    /** Profit (or loss when negative). */
    val net: Long get() = totalIncome - totalExpenses
    val totalBalance: Long get() = balances.sumOf { it.second }
}

object Reports {

    /**
     * Builds the report for [book] (null for all books together) from every entry, account and category.
     * [loans] are the book's loans as they stand on [to].
     */
    fun build(
        book: Book?,
        from: LocalDate,
        to: LocalDate,
        txns: List<Txn>,
        categories: List<Category>,
        accounts: List<Account>,
        loans: List<Pair<Loan, LoanSummary>>,
        uncategorized: String,
    ): BookReport {
        val catById = categories.associateBy { it.id }
        val accById = accounts.associateBy { it.id }
        fun inBook(categoryId: Long?) = book == null || categoryId?.let { catById[it]?.bookId } == book.id
        fun accountInBook(id: Long?) = id != null && (book == null || accById[id]?.bookId == book.id)

        val period = txns.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        fun lines(type: TxnType) = period.filter { it.type == type && inBook(it.categoryId) }
            .groupBy { it.categoryId }
            .map { (id, list) -> ReportLine(id, id?.let { catById[it]?.name } ?: uncategorized, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        var fromOthers = 0L
        var toOthers = 0L
        if (book != null) {
            for (t in period) {
                if (t.type != TxnType.TRANSFER || t.loanId != null) continue
                val fromHere = accountInBook(t.accountId)
                val toHere = accountInBook(t.toAccountId)
                when {
                    toHere && !fromHere -> fromOthers += t.amount
                    fromHere && !toHere -> toOthers += t.amount
                }
            }
        }

        val bookAccounts = accounts.filter { (book == null || it.bookId == book.id) && !it.type.isHidden }
        val parties = accounts.filter { (book == null || it.bookId == book.id) && it.type == AccountType.PARTY }
        val balances = Stats.balances(bookAccounts + parties, txns.filter { !it.date.isAfter(to) })
        val shown = bookAccounts.filter { !it.archived || (balances[it.id] ?: 0) != 0L }.map { it to (balances[it.id] ?: 0) }

        val open = loans.filter { !it.first.closed }
        return BookReport(
            book = book, from = from, to = to, income = lines(TxnType.INCOME), expenses = lines(TxnType.EXPENSE),
            fromOtherBooks = fromOthers, toOtherBooks = toOthers, balances = shown,
            loansToReceive = open.filter { it.first.direction == LoanDirection.LENT }.sumOf { it.second.outstanding },
            loansToPay = open.filter { it.first.direction == LoanDirection.BORROWED }.sumOf { it.second.outstanding },
            customersOwe = parties.sumOf { (balances[it.id] ?: 0).coerceAtLeast(0) },
            oweSuppliers = -parties.sumOf { (balances[it.id] ?: 0).coerceAtMost(0) },
        )
    }
}
