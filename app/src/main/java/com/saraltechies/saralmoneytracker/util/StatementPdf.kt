package com.saraltechies.saralmoneytracker.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.data.LoanItem
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.ContactKind
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.LoanDirection
import com.saraltechies.saralmoneytracker.model.LoanEventKind
import com.saraltechies.saralmoneytracker.model.LoanMath
import com.saraltechies.saralmoneytracker.model.LoanPlan
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.RateUnit
import com.saraltechies.saralmoneytracker.model.StatementData
import com.saraltechies.saralmoneytracker.model.UpiLink
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A loan statement as an A4 PDF, drawn with Android's own PDF support (no library, nothing leaves the phone):
 * your details and theirs, the loan's terms, a summary, every entry with the running balance, the EMI plan, and how
 * to pay (bank details and a UPI "scan to pay" code) when they owe you.
 */
class StatementPdf(private val context: Context, private val currency: CurrencyInfo) {

    data class Options(val includePlan: Boolean, val includeQr: Boolean)

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = pageWidth - 2 * margin
    private val bottomLimit = pageHeight - 56f

    private val brand = Color.rgb(13, 110, 253)
    private val navy = Color.rgb(13, 27, 74)
    private val ink = Color.rgb(26, 26, 46)
    private val slate = Color.rgb(100, 116, 139)
    private val line = Color.rgb(203, 213, 225)
    private val mist = Color.rgb(240, 246, 255)
    private val green = Color.rgb(21, 128, 61)
    private val red = Color.rgb(220, 38, 38)

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = align
    }

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var pageNumber = 0
    private var y = 0f
    private var footer = ""

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun m(v: Long) = Money.format(v, currency)

    fun write(item: LoanItem, me: Contact?, data: StatementData, options: Options, out: OutputStream, today: LocalDate = LocalDate.now()) {
        doc = PdfDocument()
        pageNumber = 0
        footer = s(R.string.pdf_footer, Dates.medium(today))
        try {
            newPage()
            header(me, data, today)
            parties(item, me)
            summary(item, data)
            entries(item, data)
            if (options.includePlan && item.loan.plan == LoanPlan.MONTHLY && item.loan.installments > 0) plan(item, today)
            if (item.loan.direction == LoanDirection.LENT && me != null && (me.hasBankDetails)) payment(item, me, data, options.includeQr)
            closingNote(item, me)
            finishPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ---- Pages ------------------------------------------------------------------------------------------------

    private fun newPage() {
        finishPage()
        pageNumber++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        page = p
        canvas = p.canvas
        y = margin
        if (pageNumber > 1) y += 4f
    }

    private fun finishPage() {
        val p = page ?: return
        val f = paint(7.5f, slate)
        canvas.drawLine(margin, pageHeight - 40f, pageWidth - margin, pageHeight - 40f, Paint().apply { color = line; strokeWidth = 0.5f })
        canvas.drawText(footer, margin, pageHeight - 28f, f)
        canvas.drawText(s(R.string.pdf_page, pageNumber), pageWidth - margin, pageHeight - 28f, paint(7.5f, slate, align = Paint.Align.RIGHT))
        doc.finishPage(p)
        page = null
    }

    private fun ensure(height: Float) {
        if (y + height > bottomLimit) newPage()
    }

    // ---- Sections ---------------------------------------------------------------------------------------------

    private fun header(me: Contact?, data: StatementData, today: LocalDate) {
        val top = y
        // Left: who is sending it.
        var ly = top + 14f
        if (me != null && me.name.isNotBlank()) {
            canvas.drawText(clip(me.name, paint(15f, navy, bold = true), contentWidth * 0.58f), margin, ly, paint(15f, navy, bold = true))
            ly += 6f
            for (l in detailLines(me, withBank = false)) {
                for (w in wrap(l, paint(8.5f, slate), contentWidth * 0.58f)) {
                    ly += 11f
                    canvas.drawText(w, margin, ly, paint(8.5f, slate))
                }
            }
        }
        // Right: what it is.
        val right = pageWidth - margin
        canvas.drawText(s(R.string.pdf_title).uppercase(), right, top + 14f, paint(14f, brand, bold = true, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.pdf_period, Dates.medium(data.from), Dates.medium(data.to)), right, top + 29f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.pdf_date, Dates.medium(today)), right, top + 41f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        y = maxOf(ly, top + 41f) + 12f
        canvas.drawRect(margin, y, pageWidth - margin, y + 2f, Paint().apply { color = brand })
        y += 16f
    }

    private fun parties(item: LoanItem, me: Contact?) {
        val loan = item.loan
        val lent = loan.direction == LoanDirection.LENT
        val half = (contentWidth - 16f) / 2
        val contact = item.contact ?: Contact(name = item.name)

        val left = mutableListOf<Pair<String, Boolean>>()
        left += contact.name to true
        if (contact.kind != ContactKind.PERSON) left += s(kindLabel(contact.kind)) to false
        detailLines(contact, withBank = !lent).forEach { left += it to false }

        val right = mutableListOf<Pair<String, String>>()
        right += s(R.string.pdf_loan_type) to s(if (lent) R.string.pdf_money_lent else R.string.pdf_money_borrowed)
        right += s(R.string.pdf_start_date) to Dates.medium(loan.startDate)
        right += s(R.string.pdf_interest) to if (loan.rate > 0) rate(loan.rate, loan.rateUnit) else s(R.string.loan_no_interest)
        right += s(R.string.pdf_repayment) to if (loan.plan == LoanPlan.MONTHLY) s(R.string.pdf_emi_plan, m(loan.installment), loan.installments) else s(R.string.loan_any_time)
        if (contact.reference.isNotBlank()) right += s(R.string.contact_reference) to contact.reference

        val leftHeight = 18f + left.sumOf { (t, _) -> wrap(t, paint(9f), half - 20f).size } * 12f
        val rightHeight = 18f + right.size * 13f
        val boxHeight = maxOf(leftHeight, rightHeight) + 10f
        ensure(boxHeight + 10f)

        box(margin, y, half, boxHeight)
        label(s(if (lent) R.string.pdf_borrower else R.string.pdf_lender), margin + 10f, y + 14f)
        var ly = y + 16f
        for ((text, bold) in left) {
            for (w in wrap(text, paint(9f), half - 20f)) {
                ly += 12f
                canvas.drawText(w, margin + 10f, ly, paint(if (bold) 10f else 8.5f, if (bold) ink else slate, bold = bold))
            }
        }

        val rx = margin + half + 16f
        box(rx, y, half, boxHeight)
        label(s(R.string.pdf_loan_details), rx + 10f, y + 14f)
        var ry = y + 16f
        for ((k, v) in right) {
            ry += 13f
            canvas.drawText(k, rx + 10f, ry, paint(8.5f, slate))
            canvas.drawText(clip(v, paint(8.5f, ink, bold = true), half * 0.55f), rx + half - 10f, ry, paint(8.5f, ink, bold = true, align = Paint.Align.RIGHT))
        }
        y += boxHeight + 14f
    }

    private fun summary(item: LoanItem, data: StatementData) {
        val c = data.closing
        val lent = item.loan.direction == LoanDirection.LENT
        val cells = mutableListOf(
            s(if (lent) R.string.loan_given else R.string.loan_taken) to m(c.given),
            s(R.string.loan_paid_back) to m(c.repaid),
            s(R.string.pdf_interest_charged) to m(c.interestAccrued),
            s(if (lent) R.string.loan_interest_received else R.string.loan_interest_paid) to m(c.interestPaid),
            s(R.string.loan_principal_left) to m(c.principalLeft.coerceAtLeast(0)),
            s(R.string.loan_interest_due) to m(c.interestDue),
        )
        val h = 92f
        ensure(h + 10f)
        val bg = Paint().apply { color = mist }
        canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + h), 6f, 6f, bg)
        val colW = contentWidth / 3
        cells.forEachIndexed { i, (k, v) ->
            val cx = margin + 12f + (i % 3) * colW
            val cy = y + 18f + (i / 3) * 30f
            canvas.drawText(k, cx, cy, paint(8f, slate))
            canvas.drawText(v, cx, cy + 12f, paint(10f, ink, bold = true))
        }
        // The bottom line: what is owed now.
        val by = y + h - 14f
        canvas.drawText(s(if (lent) R.string.statement_total_to_receive_label else R.string.statement_total_to_pay_label), margin + 12f, by, paint(10f, navy, bold = true))
        canvas.drawText(m(c.outstanding), pageWidth - margin - 12f, by, paint(13f, if (lent) green else red, bold = true, align = Paint.Align.RIGHT))
        y += h + 8f
        c.nextDueDate?.takeIf { !item.loan.closed && c.nextDueAmount > 0 }?.let {
            canvas.drawText(
                s(if (c.overdue > 0) R.string.pdf_overdue_line else R.string.pdf_next_due_line, m(c.nextDueAmount), Dates.medium(it)),
                margin, y + 8f, paint(9f, if (c.overdue > 0) red else ink, bold = true),
            )
            y += 16f
        }
        y += 8f
    }

    private val entryCols = floatArrayOf(66f, 185f, 84f, 92f, 88f)

    private fun entryHeader() {
        val labels = listOf(s(R.string.pdf_col_date), s(R.string.pdf_col_particulars), s(R.string.pdf_col_amount), s(R.string.loan_principal_left), s(R.string.loan_interest_due))
        tableHeader(labels, entryCols, rightAlignedFrom = 2)
    }

    private fun entries(item: LoanItem, data: StatementData) {
        val lent = item.loan.direction == LoanDirection.LENT
        ensure(70f)
        sectionTitle(s(R.string.pdf_entries))
        entryHeader()
        data.opening?.let { o ->
            row(listOf(Dates.medium(data.from), s(R.string.pdf_brought_forward), "", m(o.principalLeft.coerceAtLeast(0)), m(o.interestDue)), entryCols, bold = true)
        }
        if (data.rows.isEmpty()) {
            row(listOf("", s(R.string.pdf_no_entries), "", "", ""), entryCols)
        }
        data.rows.forEachIndexed { i, r ->
            if (y + 16f > bottomLimit) {
                newPage()
                entryHeader()
            }
            val particulars = eventLabel(r.kind, lent) + listOfNotNull(r.account, r.note.takeIf { it.isNotBlank() }).joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
            row(listOf(Dates.medium(r.date), particulars, m(r.amount), m(r.principalLeft.coerceAtLeast(0)), m(r.interestDue)), entryCols, shaded = i % 2 == 1)
        }
        if (y + 16f > bottomLimit) newPage()
        val c = data.closing
        row(listOf(Dates.medium(data.to), s(R.string.pdf_closing_balance), "", m(c.principalLeft.coerceAtLeast(0)), m(c.interestDue)), entryCols, bold = true)
        y += 14f
    }

    private val planCols = floatArrayOf(34f, 96f, 95f, 95f, 95f, 100f)

    private fun plan(item: LoanItem, today: LocalDate) {
        val loan = item.loan
        val s0 = item.summary
        val rows = LoanMath.schedule(s0.given, LoanMath.monthlyRate(loan), loan.installments, loan.installment, loan.startDate)
        if (rows.isEmpty()) return
        val paid = s0.totalPaid
        ensure(70f)
        sectionTitle(s(R.string.loan_plan_title))
        val header = listOf("#", s(R.string.pdf_col_due_date), s(R.string.pdf_col_emi), s(R.string.pdf_col_principal), s(R.string.pdf_col_interest), s(R.string.pdf_col_status))
        tableHeader(header, planCols, rightAlignedFrom = 2)
        var cumulative = 0L
        rows.forEachIndexed { i, r ->
            if (y + 16f > bottomLimit) {
                newPage()
                tableHeader(header, planCols, rightAlignedFrom = 2)
            }
            cumulative += r.amount
            val status = when {
                cumulative <= paid -> s(R.string.loan_paid)
                !r.date.isAfter(today) -> s(R.string.pdf_status_late)
                else -> s(R.string.pdf_status_upcoming)
            }
            val color = when {
                cumulative <= paid -> green
                !r.date.isAfter(today) -> red
                else -> slate
            }
            row(listOf(r.number.toString(), Dates.medium(r.date), m(r.amount), m(r.principal), m(r.interest), status), planCols, shaded = i % 2 == 1, lastColor = color)
        }
        y += 14f
    }

    private fun payment(item: LoanItem, me: Contact, data: StatementData, includeQr: Boolean) {
        val due = data.closing.let { if (it.nextDueAmount > 0) it.nextDueAmount else it.outstanding }
        val qr = includeQr && me.upi.isNotBlank() && currency.code == "INR" && due > 0
        val lines = mutableListOf<Pair<String, String>>()
        if (me.bankName.isNotBlank()) lines += s(R.string.contact_bank) to me.bankName
        if (me.bankAccount.isNotBlank()) lines += s(R.string.contact_bank_account) to me.bankAccount
        if (me.ifsc.isNotBlank()) lines += s(R.string.contact_ifsc) to me.ifsc
        if (me.upi.isNotBlank()) lines += s(R.string.contact_upi) to me.upi
        val qrSize = 104f
        val h = maxOf(26f + lines.size * 14f, if (qr) qrSize + 30f else 0f) + 8f
        ensure(h + 20f)
        sectionTitle(s(R.string.pdf_how_to_pay))
        box(margin, y, contentWidth, h)
        var ly = y + 6f
        canvas.drawText(s(R.string.pdf_pay_to, me.name), margin + 12f, ly + 12f, paint(9f, ink, bold = true))
        ly += 14f
        for ((k, v) in lines) {
            ly += 14f
            canvas.drawText(k, margin + 12f, ly, paint(8.5f, slate))
            canvas.drawText(v, margin + 110f, ly, paint(9f, ink, bold = true))
        }
        if (qr) {
            val link = UpiLink.build(me.upi, me.name, due, s(R.string.pdf_upi_note, item.name))
            val qx = pageWidth - margin - qrSize - 14f
            drawQr(link, qx, y + 8f, qrSize)
            canvas.drawText(s(R.string.pdf_scan_to_pay, m(due)), qx + qrSize / 2, y + qrSize + 20f, paint(8.5f, ink, bold = true, align = Paint.Align.CENTER))
        }
        y += h + 14f
    }

    private fun closingNote(item: LoanItem, me: Contact?) {
        val text = s(R.string.pdf_closing_note, me?.name?.takeIf { it.isNotBlank() } ?: s(R.string.pdf_sender_fallback))
        val lines = wrap(text, paint(8.5f, slate), contentWidth)
        ensure(lines.size * 12f + 8f)
        for (l in lines) {
            y += 12f
            canvas.drawText(l, margin, y, paint(8.5f, slate))
        }
    }

    // ---- Drawing helpers ----------------------------------------------------------------------------------------

    private fun sectionTitle(text: String) {
        canvas.drawText(text, margin, y + 10f, paint(10.5f, navy, bold = true))
        y += 18f
    }

    private fun label(text: String, x: Float, baseline: Float) {
        canvas.drawText(text.uppercase(), x, baseline, paint(7.5f, brand, bold = true))
    }

    private fun box(x: Float, top: Float, w: Float, h: Float) {
        canvas.drawRoundRect(RectF(x, top, x + w, top + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; style = Paint.Style.STROKE; strokeWidth = 0.8f })
    }

    private fun tableHeader(labels: List<String>, cols: FloatArray, rightAlignedFrom: Int) {
        canvas.drawRect(margin, y, pageWidth - margin, y + 18f, Paint().apply { color = navy })
        var x = margin
        labels.forEachIndexed { i, l ->
            val right = i >= rightAlignedFrom
            val p = paint(8f, Color.WHITE, bold = true, align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
            canvas.drawText(clip(l, p, cols[i] - 8f), if (right) x + cols[i] - 6f else x + 6f, y + 12.5f, p)
            x += cols[i]
        }
        y += 18f
    }

    private fun row(cells: List<String>, cols: FloatArray, bold: Boolean = false, shaded: Boolean = false, lastColor: Int? = null) {
        val h = 16f
        if (shaded || bold) canvas.drawRect(margin, y, pageWidth - margin, y + h, Paint().apply { color = if (bold) Color.rgb(226, 232, 240) else mist })
        var x = margin
        cells.forEachIndexed { i, text ->
            // Dates and descriptions read left to right; amounts line up on the right.
            val right = cols === entryCols && i >= 2 || cols === planCols && i >= 2
            val color = if (i == cells.lastIndex && lastColor != null) lastColor else ink
            val p = paint(8f, color, bold = bold || (i == cells.lastIndex && lastColor != null), align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
            canvas.drawText(clip(text, p, cols[i] - 10f), if (right) x + cols[i] - 6f else x + 6f, y + 11f, p)
            x += cols[i]
        }
        canvas.drawLine(margin, y + h, pageWidth - margin, y + h, Paint().apply { color = line; strokeWidth = 0.3f })
        y += h
    }

    private fun drawQr(text: String, x: Float, top: Float, size: Float) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val cell = size / matrix.width
        val dark = Paint().apply { color = Color.BLACK }
        canvas.drawRect(x, top, x + size, top + size, Paint().apply { color = Color.WHITE })
        for (row in 0 until matrix.height) for (col in 0 until matrix.width) {
            if (matrix.get(col, row)) canvas.drawRect(x + col * cell, top + row * cell, x + (col + 1) * cell + 0.2f, top + (row + 1) * cell + 0.2f, dark)
        }
    }

    private fun clip(text: String, p: TextPaint, width: Float): String = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    private fun wrap(text: String, p: TextPaint, width: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            var current = ""
            for (word in para.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (p.measureText(candidate) <= width || current.isEmpty()) current = candidate
                else {
                    out += current
                    current = word
                }
            }
            if (current.isNotEmpty()) out += clip(current, p, width)
        }
        return out
    }

    private fun detailLines(c: Contact, withBank: Boolean): List<String> = buildList {
        if (c.address.isNotBlank()) add(c.address)
        listOfNotNull(c.phone.takeIf { it.isNotBlank() }, c.email.takeIf { it.isNotBlank() }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let(::add)
        if (c.taxId.isNotBlank()) add(s(R.string.pdf_tax_id, c.taxId))
        if (withBank) {
            val bank = listOfNotNull(
                c.bankName.takeIf { it.isNotBlank() },
                c.bankAccount.takeIf { it.isNotBlank() }?.let { s(R.string.pdf_account_short, it) },
                c.ifsc.takeIf { it.isNotBlank() }?.let { s(R.string.pdf_ifsc_short, it) },
            ).joinToString(" · ")
            if (bank.isNotEmpty()) add(bank)
            if (c.upi.isNotBlank()) add(s(R.string.pdf_upi_short, c.upi))
        }
    }

    private fun rate(rate: Long, unit: RateUnit) =
        s(if (unit == RateUnit.MONTH) R.string.rate_per_month else R.string.rate_per_year, BigDecimal.valueOf(rate, 2).stripTrailingZeros().toPlainString())

    private fun eventLabel(kind: LoanEventKind, lent: Boolean) = s(
        when (kind) {
            LoanEventKind.GIVEN -> if (lent) R.string.loan_event_given else R.string.loan_event_taken
            LoanEventKind.REPAID -> if (lent) R.string.loan_event_received else R.string.loan_event_repaid
            LoanEventKind.INTEREST -> if (lent) R.string.loan_event_interest_received else R.string.loan_event_interest_paid
        },
    )

    companion object {
        fun kindLabel(kind: ContactKind): Int = when (kind) {
            ContactKind.PERSON -> R.string.contact_kind_person
            ContactKind.BANK -> R.string.contact_kind_bank
            ContactKind.COMPANY -> R.string.contact_kind_company
            ContactKind.VENDOR -> R.string.contact_kind_vendor
            ContactKind.SELF -> R.string.contact_kind_self
        }
    }
}
