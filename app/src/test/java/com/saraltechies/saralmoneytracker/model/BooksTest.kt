package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BooksTest {

    private fun d(s: String) = LocalDate.parse(s)

    private val personal = Book(id = 1, name = "Personal", kind = BookKind.PERSONAL)
    private val shop = Book(id = 2, name = "Sharma Traders", kind = BookKind.BUSINESS)

    private val accounts = listOf(
        Account(id = 1, name = "Cash", type = AccountType.CASH, bookId = 1),
        Account(id = 2, name = "SBI Savings", type = AccountType.BANK, opening = 10_000_000, bookId = 1),
        Account(id = 3, name = "Shop cash", type = AccountType.CASH, bookId = 2),
        Account(id = 4, name = "Shop current", type = AccountType.BANK, bookId = 2),
    )
    private val categories = listOf(
        Category(id = 10, name = "Groceries", kind = TxnType.EXPENSE, icon = "groceries", color = 0, bookId = 1),
        Category(id = 11, name = "Salary", kind = TxnType.INCOME, icon = "salary", color = 0, bookId = 1),
        Category(id = 20, name = "Sales", kind = TxnType.INCOME, icon = "store", color = 0, bookId = 2),
        Category(id = 21, name = "Purchases", kind = TxnType.EXPENSE, icon = "store", color = 0, bookId = 2),
        Category(id = 22, name = "Rent", kind = TxnType.EXPENSE, icon = "home", color = 0, bookId = 2),
    )
    private val txns = listOf(
        // Capital: 50,000 from personal savings into the shop's account.
        Txn(type = TxnType.TRANSFER, amount = 5_000_000, date = d("2026-04-02"), categoryId = null, accountId = 2, toAccountId = 4),
        Txn(type = TxnType.INCOME, amount = 8_000_000, date = d("2026-04-10"), categoryId = 20, accountId = 4),
        Txn(type = TxnType.EXPENSE, amount = 4_500_000, date = d("2026-04-12"), categoryId = 21, accountId = 4),
        // Shop rent paid from the personal account: a business expense all the same.
        Txn(type = TxnType.EXPENSE, amount = 1_500_000, date = d("2026-04-05"), categoryId = 22, accountId = 2),
        Txn(type = TxnType.EXPENSE, amount = 300_000, date = d("2026-04-06"), categoryId = 10, accountId = 1),
        // Drawings: 10,000 from the shop to personal cash.
        Txn(type = TxnType.TRANSFER, amount = 1_000_000, date = d("2026-04-30"), categoryId = null, accountId = 3, toAccountId = 1),
        // Outside the period.
        Txn(type = TxnType.INCOME, amount = 9_900_000, date = d("2026-05-01"), categoryId = 20, accountId = 4),
    )

    private fun report(book: Book?) = Reports.build(book, d("2026-04-01"), d("2026-04-30"), txns, categories, accounts, emptyList(), "Uncategorized")

    @Test
    fun businessProfitCountsItsCategoriesWhicheverAccountPaid() {
        val r = report(shop)
        assertEquals(8_000_000L, r.totalIncome)
        assertEquals(6_000_000L, r.totalExpenses)
        assertEquals(2_000_000L, r.net)
        assertEquals(listOf("Purchases", "Rent"), r.expenses.map { it.name })
        assertEquals(5_000_000L, r.fromOtherBooks)
        assertEquals(1_000_000L, r.toOtherBooks)
        // Shop current: 50,000 + 80,000 - 45,000; shop cash: -10,000.
        assertEquals(mapOf("Shop cash" to -1_000_000L, "Shop current" to 8_500_000L), r.balances.associate { it.first.name to it.second })
    }

    @Test
    fun personalBookLeavesBusinessOut() {
        val r = report(personal)
        assertEquals(0L, r.totalIncome)
        assertEquals(300_000L, r.totalExpenses)
        assertEquals(1_000_000L, r.fromOtherBooks)
        assertEquals(5_000_000L, r.toOtherBooks)
        // Savings: 1,00,000 - 50,000 capital - 15,000 shop rent.
        assertEquals(3_500_000L, r.balances.single { it.first.name == "SBI Savings" }.second)
    }

    @Test
    fun allBooksTogether() {
        val r = report(null)
        assertEquals(8_000_000L, r.totalIncome)
        assertEquals(6_300_000L, r.totalExpenses)
        assertEquals(0L, r.fromOtherBooks)
        assertEquals(4, r.balances.size)
    }

    @Test
    fun transfersBetweenBooks() {
        assertEquals(Books.Flow.CAPITAL_IN, Books.flow(personal, shop))
        assertEquals(Books.Flow.DRAWINGS, Books.flow(shop, personal))
        assertEquals(Books.Flow.SAME_BOOK, Books.flow(shop, shop))
        assertEquals(Books.Flow.BETWEEN, Books.flow(shop, shop.copy(id = 3)))
    }
}
