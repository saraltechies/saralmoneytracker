package com.saraltechies.saralmoneytracker.ui.loans

import android.content.Context
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Pledge
import com.saraltechies.saralmoneytracker.model.PledgeKind
import com.saraltechies.saralmoneytracker.model.PledgeTotals
import com.saraltechies.saralmoneytracker.ui.components.Dates

/** How pledges (girvi) read on screen and in statements. */
object Pledges {

    fun kindLabel(kind: PledgeKind): Int = when (kind) {
        PledgeKind.GOLD -> R.string.pledge_gold
        PledgeKind.SILVER -> R.string.pledge_silver
        PledgeKind.OTHER -> R.string.pledge_other
    }

    /** "Gold 22K", "Silver", "Other". */
    fun kindAndPurity(context: Context, p: Pledge): String =
        listOf(context.getString(kindLabel(p.kind)), p.purity).filter { it.isNotBlank() }.joinToString(" ")

    /** "Gold 22K · 10.5 g · 1 chain, 2 bangles" (the items only [withItems]). */
    fun summary(context: Context, p: Pledge, withItems: Boolean = true): String = listOfNotNull(
        kindAndPurity(context, p),
        p.weightMg.takeIf { it > 0 }?.let { context.getString(R.string.pledge_grams, Pledge.grams(it)) },
        p.items.takeIf { withItems && it.isNotBlank() },
    ).joinToString(" · ")

    /** Where the items are now: "With you", "With the lender", "Returned on 5 Mar 2026". */
    fun status(context: Context, p: Pledge, lent: Boolean): String = p.returnedOn?.let { context.getString(R.string.pledge_returned_on, Dates.medium(it)) }
        ?: context.getString(if (lent) R.string.pledge_with_you else R.string.pledge_with_lender)

    /** "Gold 45.5 g · Silver 250 g · 1 other item". */
    fun totals(context: Context, t: PledgeTotals): String = listOfNotNull(
        t.goldMg.takeIf { it > 0 }?.let { context.getString(R.string.pledge_total_gold, Pledge.grams(it)) },
        t.silverMg.takeIf { it > 0 }?.let { context.getString(R.string.pledge_total_silver, Pledge.grams(it)) },
        t.others.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.pledge_total_other, it, it) },
    ).joinToString(" · ").ifEmpty { context.resources.getQuantityString(R.plurals.pledge_total_items, t.count, t.count) }
}
