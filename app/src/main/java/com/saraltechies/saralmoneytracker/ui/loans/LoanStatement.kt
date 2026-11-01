package com.saraltechies.saralmoneytracker.ui.loans

import android.content.Context
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.time.LocalDate

/** A plain-text account of a loan to send to the other person (WhatsApp, SMS, email), so both sides agree. */
object LoanStatement {

    fun text(context: Context, item: LoanItem, currency: CurrencyInfo, today: LocalDate = LocalDate.now()): String {
        fun m(v: Long) = Money.format(v, currency)
        val loan = item.loan
        val s = item.summary
        val lent = loan.direction == LoanDirection.LENT
        val lines = mutableListOf<String>()
        lines += context.getString(R.string.statement_title, item.name, Dates.medium(today))
        lines += ""
        for (e in item.events) {
            val label = context.getString(
                when (e.kind) {
                    LoanEventKind.GIVEN -> if (lent) R.string.loan_event_given else R.string.loan_event_taken
                    LoanEventKind.REPAID -> if (lent) R.string.loan_event_received else R.string.loan_event_repaid
                    LoanEventKind.INTEREST -> if (lent) R.string.loan_event_interest_received else R.string.loan_event_interest_paid
                },
            )
            lines += "${Dates.medium(e.date)}  $label  ${m(e.amount)}"
        }
        lines += ""
        if (loan.rate > 0) {
            val rate = context.getString(if (loan.rateUnit == RateUnit.MONTH) R.string.rate_per_month else R.string.rate_per_year, ratePlain(loan.rate))
            lines += context.getString(R.string.statement_rate, rate)
        }
        if (loan.plan == LoanPlan.MONTHLY) lines += context.getString(R.string.statement_installment, m(loan.installment), loan.installments)
        loan.pledge?.let { p ->
            lines += context.getString(R.string.pdf_security) + ": " + Pledges.summary(context, p) + " (" + Pledges.status(context, p, lent) + ")"
        }
        lines += context.getString(R.string.statement_principal_left, m(s.principalLeft.coerceAtLeast(0)))
        if (loan.rate > 0 || s.interestPaid > 0) lines += context.getString(R.string.statement_interest_due, m(s.interestDue))
        lines += context.getString(if (lent) R.string.statement_total_to_receive else R.string.statement_total_to_pay, m(s.outstanding))
        lines += ""
        lines += context.getString(R.string.statement_footer)
        return lines.joinToString("\n")
    }
}
