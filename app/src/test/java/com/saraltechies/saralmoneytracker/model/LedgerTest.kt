package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LedgerTest {

    private fun d(s: String) = LocalDate.parse(s)

    private val cash = Account(id = 1, name = "Shop cash", type = AccountType.CASH, bookId = 2)
    private val customerAccount = Account(id = 7, name = "Ramesh", type = AccountType.PARTY, opening = 50_000, bookId = 2)
    private val supplierAccount = Account(id = 8, name = "Gupta Wholesale", type = AccountType.PARTY, bookId = 2)
    private val customer = Party(id = 1, accountId = 7, contactId = 1, role = PartyRole.CUSTOMER, bookId = 2)
    private val supplier = Party(id = 2, accountId = 8, contactId = 2, role = PartyRole.SUPPLIER, bookId = 2)
    private val sales = Category(id = 20, name = "Sales", kind = TxnType.INCOME, icon = "store", color = 0, bookId = 2)
    private val purchases = Category(id = 21, name = "Purchases & Stock", kind = TxnType.EXPENSE, icon = "store", color = 0, bookId = 2)

    private val txns = listOf(
        // Ramesh buys ₹1,200 of goods on credit, then pays ₹1,000 in cash.
        Txn(id = 1, type = TxnType.INCOME, amount = 120_000, date = d("2026-11-02"), categoryId = 20, accountId = 7, createdAt = 1),
        Txn(id = 2, type = TxnType.TRANSFER, amount = 100_000, date = d("2026-11-05"), categoryId = null, accountId = 7, toAccountId = 1, createdAt = 2),
        // Stock for ₹5,000 on credit from Gupta, paid ₹3,000.
        Txn(id = 3, type = TxnType.EXPENSE, amount = 500_000, date = d("2026-11-01"), categoryId = 21, accountId = 8, createdAt = 3),
        Txn(id = 4, type = TxnType.TRANSFER, amount = 300_000, date = d("2026-11-06"), categoryId = null, accountId = 1, toAccountId = 8, createdAt = 4),
    )

    @Test
    fun customerAccountRunsFromTheOldBalance() {
        val entries = Ledgers.entries(customer, customerAccount.opening, txns.filter { it.accountId == 7L || it.toAccountId == 7L })
        assertEquals(listOf(LedgerKind.OPENING, LedgerKind.GAVE_GOODS, LedgerKind.GOT_MONEY), entries.map { it.kind })
        assertEquals(listOf(50_000L, 170_000L, 70_000L), entries.map { it.balanceAfter })
        assertEquals(1L, entries[2].accountId) // paid into shop cash
        assertNull(entries[1].accountId)
        assertEquals(20L, entries[1].categoryId)
    }

    @Test
    fun supplierBalanceIsWhatYouOwe() {
        val entries = Ledgers.entries(supplier, 0, txns.filter { it.accountId == 8L || it.toAccountId == 8L })
        assertEquals(listOf(LedgerKind.GOT_GOODS, LedgerKind.GAVE_MONEY), entries.map { it.kind })
        assertEquals(-200_000L, entries.last().balanceAfter)
        assertEquals(1L, entries.last().accountId)
    }

    @Test
    fun salesCountOnceAndCreditShowsInTheReport() {
        val shop = Book(id = 2, name = "Sharma Traders", kind = BookKind.BUSINESS)
        val r = Reports.build(
            shop, d("2026-11-01"), d("2026-11-30"), txns, listOf(sales, purchases), listOf(cash, customerAccount, supplierAccount), emptyList(), "?",
        )
        // The payment from Ramesh is not income again; paying Gupta is not an expense again.
        assertEquals(120_000L, r.totalIncome)
        assertEquals(500_000L, r.totalExpenses)
        assertEquals(70_000L, r.customersOwe)
        assertEquals(200_000L, r.oweSuppliers)
        // Party accounts aren't listed as your accounts; cash got 1,000 and paid 3,000.
        assertEquals(listOf("Shop cash" to -200_000L), r.balances.map { it.first.name to it.second })
        // Not counted as money moved between books.
        assertEquals(0L, r.fromOtherBooks)
    }

    @Test
    fun whatsAppNumbers() {
        assertEquals("919876543210", Ledgers.whatsAppNumber("98765 43210"))
        assertEquals("919876543210", Ledgers.whatsAppNumber("+91 98765-43210"))
        assertEquals("919876543210", Ledgers.whatsAppNumber("09876543210"))
        assertEquals("447700900123", Ledgers.whatsAppNumber("0044 7700 900123"))
        assertNull(Ledgers.whatsAppNumber("12345"))
    }
}
