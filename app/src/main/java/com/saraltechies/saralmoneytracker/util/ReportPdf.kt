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
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.BookKind
import com.saraltechies.saralmoneytracker.model.BookReport
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.io.OutputStream
import java.time.LocalDate

/**
 * A book's report as an A4 PDF: for a business, its profit and loss for the period, capital put in and drawings,
 * account balances and loans; for a personal book, income and expenses. For the proprietor, an accountant or a bank.
 */
class ReportPdf(private val context: Context, private val currency: CurrencyInfo) {

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

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var pageNumber = 0
    private var y = 0f
    private var footer = ""

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun m(v: Long) = Money.format(v, currency)

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = align
    }

    fun write(report: BookReport, me: Contact?, out: OutputStream, today: LocalDate = LocalDate.now()) {
        doc = PdfDocument()
        pageNumber = 0
        footer = s(R.string.pdf_footer, Dates.medium(today))
        val business = report.book?.kind == BookKind.BUSINESS
        try {
            newPage()
            header(report, me, today, business)
            summary(report, business)
            section(s(if (business) R.string.report_sales_income else R.string.report_income), report.income.map { it.name to it.amount }, report.totalIncome, green)
            section(s(R.string.report_expenses), report.expenses.map { it.name to it.amount }, report.totalExpenses, red)
            if (report.book != null && (report.fromOtherBooks != 0L || report.toOtherBooks != 0L)) {
                section(
                    s(R.string.report_between_books),
                    listOf(
                        s(if (business) R.string.report_capital_in else R.string.report_from_business) to report.fromOtherBooks,
                        s(if (business) R.string.report_drawings else R.string.report_to_business) to report.toOtherBooks,
                    ),
                    null, ink,
                )
            }
            section(s(R.string.report_balances, Dates.medium(report.to)), report.balances.map { it.first.name to it.second }, report.totalBalance, navy)
            if (report.loansToReceive != 0L || report.loansToPay != 0L) {
                section(
                    s(R.string.loans_title),
                    listOf(s(R.string.loans_to_receive) to report.loansToReceive, s(R.string.loans_to_pay) to report.loansToPay),
                    null, ink,
                )
            }
            note(s(R.string.report_note))
            finishPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun newPage() {
        finishPage()
        pageNumber++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun finishPage() {
        val p = page ?: return
        canvas.drawLine(margin, pageHeight - 40f, pageWidth - margin, pageHeight - 40f, Paint().apply { color = line; strokeWidth = 0.5f })
        canvas.drawText(footer, margin, pageHeight - 28f, paint(7.5f, slate))
        canvas.drawText(s(R.string.pdf_page, pageNumber), pageWidth - margin, pageHeight - 28f, paint(7.5f, slate, align = Paint.Align.RIGHT))
        doc.finishPage(p)
        page = null
    }

    private fun ensure(h: Float) {
        if (y + h > bottomLimit) newPage()
    }

    private fun clip(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    private fun header(report: BookReport, me: Contact?, today: LocalDate, business: Boolean) {
        val top = y
        val name = me?.name?.takeIf { it.isNotBlank() } ?: report.book?.name ?: s(R.string.books_all)
        canvas.drawText(clip(name, paint(15f, navy, bold = true), contentWidth * 0.58f), margin, top + 14f, paint(15f, navy, bold = true))
        var ly = top + 20f
        if (me != null) {
            listOfNotNull(
                me.address.takeIf { it.isNotBlank() },
                listOf(me.phone, me.email).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
                me.taxId.takeIf { it.isNotBlank() }?.let { s(R.string.pdf_tax_id, it) },
            ).forEach {
                ly += 11f
                canvas.drawText(clip(it, paint(8.5f, slate), contentWidth * 0.58f), margin, ly, paint(8.5f, slate))
            }
        }
        val right = pageWidth - margin
        canvas.drawText(s(if (business) R.string.report_title_business else R.string.report_title_personal).uppercase(), right, top + 14f, paint(13f, brand, bold = true, align = Paint.Align.RIGHT))
        report.book?.let { canvas.drawText(it.name, right, top + 29f, paint(9f, ink, bold = true, align = Paint.Align.RIGHT)) }
        canvas.drawText(s(R.string.pdf_period, Dates.medium(report.from), Dates.medium(report.to)), right, top + 41f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.pdf_date, Dates.medium(today)), right, top + 53f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        y = maxOf(ly, top + 53f) + 12f
        canvas.drawRect(margin, y, pageWidth - margin, y + 2f, Paint().apply { color = brand })
        y += 18f
    }

    private fun summary(report: BookReport, business: Boolean) {
        val h = 64f
        canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + h), 6f, 6f, Paint().apply { color = mist })
        val cells = listOf(
            s(if (business) R.string.report_sales_income else R.string.report_income) to report.totalIncome,
            s(R.string.report_expenses) to report.totalExpenses,
            s(if (report.net >= 0) (if (business) R.string.report_profit else R.string.report_saved) else (if (business) R.string.report_loss else R.string.report_overspent)) to report.net,
        )
        val col = contentWidth / 3
        cells.forEachIndexed { i, (k, v) ->
            val x = margin + 14f + i * col
            canvas.drawText(k, x, y + 24f, paint(8.5f, slate))
            val color = if (i == 2) (if (v >= 0) green else red) else ink
            canvas.drawText(m(if (i == 2) kotlin.math.abs(v) else v), x, y + 44f, paint(if (i == 2) 15f else 13f, color, bold = true))
        }
        y += h + 18f
    }

    /** A titled list of name and amount, with an optional total. */
    private fun section(title: String, rows: List<Pair<String, Long>>, total: Long?, totalColor: Int) {
        ensure(48f)
        canvas.drawText(title, margin, y + 10f, paint(10.5f, navy, bold = true))
        y += 16f
        canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply { color = navy; strokeWidth = 1f })
        if (rows.isEmpty()) {
            y += 16f
            canvas.drawText(s(R.string.report_nothing), margin + 6f, y - 4f, paint(8.5f, slate))
        }
        rows.forEachIndexed { i, (name, amount) ->
            if (y + 16f > bottomLimit) newPage()
            if (i % 2 == 1) canvas.drawRect(margin, y, pageWidth - margin, y + 16f, Paint().apply { color = mist })
            canvas.drawText(clip(name, paint(8.5f), contentWidth - 140f), margin + 6f, y + 11.5f, paint(8.5f))
            canvas.drawText(m(amount), pageWidth - margin - 6f, y + 11.5f, paint(8.5f, align = Paint.Align.RIGHT))
            y += 16f
        }
        if (total != null) {
            if (y + 18f > bottomLimit) newPage()
            canvas.drawRect(margin, y, pageWidth - margin, y + 18f, Paint().apply { color = Color.rgb(226, 232, 240) })
            canvas.drawText(s(R.string.report_total), margin + 6f, y + 12.5f, paint(9f, ink, bold = true))
            canvas.drawText(m(total), pageWidth - margin - 6f, y + 12.5f, paint(9f, totalColor, bold = true, align = Paint.Align.RIGHT))
            y += 18f
        }
        y += 16f
    }

    private fun note(text: String) {
        ensure(30f)
        val p = paint(8f, slate)
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) > contentWidth && current.isNotEmpty()) {
                y += 11f
                canvas.drawText(current, margin, y, p)
                current = word
            } else current = candidate
        }
        if (current.isNotEmpty()) {
            y += 11f
            canvas.drawText(current, margin, y, p)
        }
    }
}
