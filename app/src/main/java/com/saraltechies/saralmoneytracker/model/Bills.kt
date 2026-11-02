package com.saraltechies.saralmoneytracker.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * One line of a bill. [quantity] is in thousandths, so 1.5 kg is 1500; [rate] is the price of one in hundredths;
 * [gstRate] is in hundredths of a percent, so 18% is 1800.
 */
data class BillItem(
    val name: String,
    val quantity: Long = 1000,
    val unit: String = "",
    val rate: Long = 0,
    val gstRate: Long = 0,
    val hsn: String = "",
) {
    val amount: Long get() = BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(rate)).movePointLeft(3).setScale(0, RoundingMode.HALF_UP).toLong()
}

/**
 * A bill for something sold, to a customer in the credit ledger or to someone who pays on the spot (a walk-in sale).
 *
 * Saving a bill records its sale once: as income in the customer's account (so it adds to what they owe), or as income
 * in [accountId] for a walk-in sale, which is paid in full. A customer's payment is a transfer from their account, as
 * everywhere in the ledger, so nothing is counted twice. Entries that belong to a bill have [Txn.billId] set.
 */
data class Bill(
    val id: Long = 0,
    val number: String,
    val date: LocalDate,
    val bookId: Long = Books.PERSONAL_ID,
    /** The customer; null for a walk-in sale. */
    val partyId: Long? = null,
    /** For a walk-in sale, a name to print on the bill, if any. */
    val customerName: String = "",
    /** For a walk-in sale, the account the money went into. */
    val accountId: Long? = null,
    /** Taken off before tax, in hundredths. */
    val discount: Long = 0,
    /** The rates already include GST, as on most shelf prices. */
    val pricesIncludeGst: Boolean = false,
    /** Sold to another state: IGST instead of CGST and SGST. */
    val interState: Boolean = false,
    val note: String = "",
    val createdAt: Long = 0,
    val items: List<BillItem> = emptyList(),
)

/** The taxable value and tax at one GST rate. */
data class BillTax(val rate: Long, val taxable: Long, val tax: Long) {
    /** Half of the tax, the odd paisa going to CGST, so CGST and SGST always add up to the tax. */
    val cgst: Long get() = tax - tax / 2
    val sgst: Long get() = tax / 2
}

data class BillTotals(
    /** The lines added up, as priced. */
    val subtotal: Long,
    val discount: Long,
    val taxes: List<BillTax>,
    val total: Long,
) {
    val tax: Long get() = taxes.sumOf { it.tax }
    /** What was really sold for: the total without tax. This is what counts as sales. */
    val net: Long get() = total - tax
    val hasGst: Boolean get() = taxes.any { it.rate > 0 }
}

/** How much of a bill has been paid. */
data class BillPayment(val paid: Long, val total: Long) {
    val due: Long get() = (total - paid).coerceAtLeast(0)
    val isPaid: Boolean get() = due == 0L
    val isPartPaid: Boolean get() = paid > 0 && due > 0
}

object BillMath {

    /**
     * Adds up a bill. A discount is shared across the lines by their value, so each GST rate is charged on what was
     * really paid. Rounded per rate, as on a tax invoice.
     */
    fun totals(items: List<BillItem>, discount: Long = 0, pricesIncludeGst: Boolean = false): BillTotals {
        val subtotal = items.sumOf { it.amount }
        val off = discount.coerceIn(0, subtotal.coerceAtLeast(0))
        val byRate = items.groupBy { it.gstRate }.map { (rate, lines) -> rate to lines.sumOf { it.amount } }.sortedBy { it.first }
        var offLeft = off
        val taxes = byRate.mapIndexed { i, (rate, value) ->
            // The last rate takes what's left, so rounding never loses a paisa of the discount.
            val share = if (i == byRate.lastIndex) offLeft else share(off, value, subtotal)
            offLeft -= share
            val after = value - share
            if (pricesIncludeGst) {
                val tax = divide(after * rate, 10_000 + rate)
                BillTax(rate, after - tax, tax)
            } else {
                BillTax(rate, after, divide(after * rate, 10_000))
            }
        }
        val taxAdded = if (pricesIncludeGst) 0 else taxes.sumOf { it.tax }
        return BillTotals(subtotal, off, taxes, subtotal - off + taxAdded)
    }

    fun totals(bill: Bill): BillTotals = totals(bill.items, bill.discount, bill.pricesIncludeGst)

    /**
     * How much of each of a customer's bills is paid. A payment made against a bill goes to that bill. Any other
     * payment in their account ("You got") settles the oldest of what they owe, the old balance and other credit
     * included, the same way the ledger's due dates work.
     *
     * [billOf] says which bill an entry belongs to, by entry id.
     */
    fun payments(bills: List<Bill>, entries: List<LedgerEntry>, billOf: Map<Long, Long>): Map<Long, BillPayment> {
        val totals = bills.associate { it.id to totals(it).total }
        val paid = HashMap<Long, Long>()
        var pool = 0L
        class Charge(val date: LocalDate, val order: Int, val billId: Long?, var left: Long)
        val charges = mutableListOf<Charge>()
        for (e in entries) {
            val bill = e.txnId?.let { billOf[it] }
            when {
                // A bill's own sale is the bill itself, added below.
                bill != null && e.change > 0 -> Unit
                bill != null -> {
                    // More paid against a bill than it comes to is an advance for the rest.
                    val room = ((totals[bill] ?: 0) - (paid[bill] ?: 0)).coerceAtLeast(0)
                    val used = minOf(room, e.amount)
                    if (used > 0) paid[bill] = (paid[bill] ?: 0) + used
                    pool += e.amount - used
                }
                e.change > 0 -> charges += Charge(e.date, if (e.kind == LedgerKind.OPENING) 0 else 1, null, e.change)
                else -> pool += -e.change
            }
        }
        for (b in bills) {
            val left = (totals[b.id] ?: 0) - (paid[b.id] ?: 0)
            if (left > 0) charges += Charge(b.date, 1, b.id, left)
        }
        for (c in charges.sortedWith(compareBy({ it.order }, { it.date }, { it.billId ?: 0 }))) {
            if (pool <= 0) break
            val used = minOf(pool, c.left)
            pool -= used
            c.billId?.let { paid[it] = (paid[it] ?: 0) + used }
        }
        return bills.associate { it.id to BillPayment(paid[it.id] ?: 0, totals[it.id] ?: 0) }
    }

    /** The bill number after [last]: the digits at its end go up by one and keep their width, so "A-009" gives "A-010". */
    fun nextNumber(last: String?): String {
        val text = last?.trim().orEmpty()
        if (text.isEmpty()) return "1"
        val digits = text.takeLastWhile { it.isDigit() }
        if (digits.isEmpty()) return text + "1"
        val next = BigDecimal(digits).add(BigDecimal.ONE).toPlainString().padStart(digits.length, '0')
        return text.dropLast(digits.length) + next
    }

    /** "1.5" to 1500 thousandths; null when it isn't a number above zero. */
    fun parseQuantity(text: String): Long? = text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it <= BigDecimal(1_000_000) }
        ?.movePointRight(3)?.setScale(0, RoundingMode.HALF_UP)?.toLong()?.takeIf { it > 0 }

    /** 1500 as "1.5", 2000 as "2". */
    fun quantityText(quantity: Long): String = BigDecimal.valueOf(quantity, 3).stripTrailingZeros().toPlainString()

    /** 1800 as "18", 250 as "2.5". */
    fun rateText(rate: Long): String = BigDecimal.valueOf(rate, 2).stripTrailingZeros().toPlainString()

    private fun share(off: Long, value: Long, subtotal: Long): Long =
        if (subtotal <= 0) 0 else BigDecimal.valueOf(off).multiply(BigDecimal.valueOf(value)).divide(BigDecimal.valueOf(subtotal), 0, RoundingMode.HALF_UP).toLong()

    private fun divide(a: Long, b: Long): Long = BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 0, RoundingMode.HALF_UP).toLong()
}
