package com.saraltechies.saralmoneytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillsTest {

    private fun rupees(r: Long) = r * 100
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val party = Party(id = 1, accountId = 50, contactId = null, role = PartyRole.CUSTOMER)

    private fun item(price: Long, qty: Long = 1000, gst: Long = 0) = BillItem("Thing", qty, "", rupees(price), gst)

    private fun bill(id: Long, day: Long, price: Long) = Bill(id = id, number = "$id", date = jan1.plusDays(day), partyId = 1, items = listOf(item(price)))

    private fun sale(id: Long, day: Long, amount: Long) = Txn(id = id, type = TxnType.INCOME, amount = rupees(amount), date = jan1.plusDays(day), categoryId = 1, accountId = 50, createdAt = id)

    private fun got(id: Long, day: Long, amount: Long) = Txn(id = id, type = TxnType.TRANSFER, amount = rupees(amount), date = jan1.plusDays(day), categoryId = null, accountId = 50, toAccountId = 9, createdAt = id)

    private fun payments(bills: List<Bill>, txns: List<Txn>, links: Map<Long, Long>, opening: Long = 0) =
        BillMath.payments(bills, Ledgers.entries(party, opening, txns), links)

    @Test
    fun quantityTimesRate() {
        assertEquals(rupees(75), item(50, qty = 1500).amount)
        // 0.333 kg at ₹100 is ₹33.30.
        assertEquals(3330L, item(100, qty = 333).amount)
    }

    @Test
    fun totalsWithoutGst() {
        val t = BillMath.totals(listOf(item(100), item(50)), discount = rupees(30))
        assertEquals(rupees(150), t.subtotal)
        assertEquals(rupees(120), t.total)
        assertEquals(0L, t.tax)
        assertEquals(rupees(120), t.net)
        assertFalse(t.hasGst)
    }

    @Test
    fun gstAddedOnTop() {
        val t = BillMath.totals(listOf(item(1000, gst = 1800), item(100, gst = 500)))
        assertEquals(rupees(180) + rupees(5), t.tax)
        assertEquals(rupees(1100) + rupees(185), t.total)
        assertEquals(rupees(1100), t.net)
        assertEquals(listOf(500L, 1800L), t.taxes.map { it.rate })
    }

    @Test
    fun discountIsSharedBeforeGst() {
        // ₹110 off ₹1,100: ₹100 off the 18% line and ₹10 off the 5% line.
        val t = BillMath.totals(listOf(item(1000, gst = 1800), item(100, gst = 500)), discount = rupees(110))
        assertEquals(rupees(162) + 450L, t.tax)
        assertEquals(rupees(990) + rupees(162) + 450L, t.total)
        assertEquals(rupees(110), t.discount)
    }

    @Test
    fun pricesThatIncludeGst() {
        // ₹118 including 18% is ₹100 plus ₹18; the customer pays ₹118.
        val t = BillMath.totals(listOf(item(118, gst = 1800)), pricesIncludeGst = true)
        assertEquals(rupees(118), t.total)
        assertEquals(rupees(18), t.tax)
        assertEquals(rupees(100), t.net)
    }

    @Test
    fun cgstAndSgstAlwaysAddUp() {
        val tax = BillTax(1800, 100, 19)
        assertEquals(10L, tax.cgst)
        assertEquals(9L, tax.sgst)
        assertEquals(19L, tax.cgst + tax.sgst)
    }

    @Test
    fun aDiscountCanNotPassTheBill() {
        assertEquals(0L, BillMath.totals(listOf(item(100)), discount = rupees(500)).total)
    }

    @Test
    fun paymentOnABillGoesToThatBill() {
        val bills = listOf(bill(1, 0, 100), bill(2, 1, 200))
        val p = payments(bills, listOf(sale(10, 0, 100), sale(11, 1, 200), got(12, 2, 50)), mapOf(10L to 1L, 11L to 2L, 12L to 2L))
        assertEquals(0L, p[1]?.paid)
        assertEquals(rupees(50), p[2]?.paid)
        assertEquals(rupees(150), p[2]?.due)
        assertTrue(p[2]!!.isPartPaid)
    }

    @Test
    fun otherPaymentsSettleTheOldestFirst() {
        val bills = listOf(bill(1, 0, 100), bill(2, 1, 200))
        val p = payments(bills, listOf(sale(10, 0, 100), sale(11, 1, 200), got(12, 2, 150)), mapOf(10L to 1L, 11L to 2L))
        assertTrue(p[1]!!.isPaid)
        assertEquals(rupees(50), p[2]?.paid)
    }

    @Test
    fun theOldBalanceIsPaidBeforeBills() {
        val bills = listOf(bill(1, 0, 100))
        val p = payments(bills, listOf(sale(10, 0, 100), got(11, 1, 120)), mapOf(10L to 1L), opening = rupees(100))
        assertEquals(rupees(20), p[1]?.paid)
    }

    @Test
    fun anEarlierCreditEntryIsPaidBeforeALaterBill() {
        val bills = listOf(bill(1, 5, 100))
        val p = payments(bills, listOf(sale(10, 0, 40), sale(11, 5, 100), got(12, 6, 60)), mapOf(11L to 1L))
        assertEquals(rupees(20), p[1]?.paid)
    }

    @Test
    fun anOverpaymentOnABillHelpsTheNext() {
        val bills = listOf(bill(1, 0, 100), bill(2, 1, 200))
        val p = payments(bills, listOf(sale(10, 0, 100), sale(11, 1, 200), got(12, 2, 130)), mapOf(10L to 1L, 11L to 2L, 12L to 1L))
        assertTrue(p[1]!!.isPaid)
        assertEquals(rupees(30), p[2]?.paid)
    }

    @Test
    fun nextNumbers() {
        assertEquals("1", BillMath.nextNumber(null))
        assertEquals("13", BillMath.nextNumber("12"))
        assertEquals("A-010", BillMath.nextNumber("A-009"))
        assertEquals("100", BillMath.nextNumber("99"))
        assertEquals("CASH1", BillMath.nextNumber("CASH"))
    }

    @Test
    fun quantities() {
        assertEquals(1500L, BillMath.parseQuantity("1.5"))
        assertEquals(2000L, BillMath.parseQuantity("2"))
        assertEquals(250L, BillMath.parseQuantity("0,25"))
        assertNull(BillMath.parseQuantity("0"))
        assertNull(BillMath.parseQuantity("a"))
        assertEquals("1.5", BillMath.quantityText(1500))
        assertEquals("2", BillMath.quantityText(2000))
    }
}
