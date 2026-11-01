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
import com.saraltechies.saralmoneytracker.data.PartyItem
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.LedgerKind
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.PartyRole
import com.saraltechies.saralmoneytracker.model.UpiLink
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.io.OutputStream
import java.time.LocalDate

/**
 * A customer's or supplier's account as an A4 PDF: both parties, every entry (you gave / you got) with the running
 * balance, and, when they owe you, your bank details and a UPI scan-to-pay code for the balance.
 */
class PartyStatementPdf(private val context: Context, private val currency: CurrencyInfo) {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = pageWidth - 2 * margin
    private val bottomLimit = pageHeight - 56f
    private val cols = floatArrayOf(66f, 205f, 80f, 80f, 84f)

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

    private fun clip(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    fun write(item: PartyItem, me: Contact?, accountNames: Map<Long, String>, out: OutputStream, today: LocalDate = LocalDate.now()) {
        doc = PdfDocument()
        pageNumber = 0
        footer = s(R.string.pdf_footer, Dates.medium(today))
        try {
            newPage()
            header(item, me, today)
            summary(item)
            entries(item, accountNames)
            val b = item.balance
            if (b > 0 && me != null && me.hasBankDetails) payment(item, me, b)
            note(s(R.string.pdf_closing_note, me?.name?.takeIf { it.isNotBlank() } ?: s(R.string.pdf_sender_fallback)))
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

    private fun details(c: Contact): List<String> = listOfNotNull(
        c.address.takeIf { it.isNotBlank() },
        listOf(c.phone, c.email).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
        c.taxId.takeIf { it.isNotBlank() }?.let { s(R.string.pdf_tax_id, it) },
    )

    private fun header(item: PartyItem, me: Contact?, today: LocalDate) {
        val top = y
        var ly = top + 14f
        if (me != null && me.name.isNotBlank()) {
            canvas.drawText(clip(me.name, paint(15f, navy, bold = true), contentWidth * 0.58f), margin, ly, paint(15f, navy, bold = true))
            ly += 6f
            details(me).forEach {
                ly += 11f
                canvas.drawText(clip(it, paint(8.5f, slate), contentWidth * 0.58f), margin, ly, paint(8.5f, slate))
            }
        }
        val right = pageWidth - margin
        canvas.drawText(s(R.string.ledger_statement_title).uppercase(), right, top + 14f, paint(13f, brand, bold = true, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.pdf_date, Dates.medium(today)), right, top + 29f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        y = maxOf(ly, top + 29f) + 12f
        canvas.drawRect(margin, y, pageWidth - margin, y + 2f, Paint().apply { color = brand })
        y += 16f

        // Who it's for.
        val c = item.contact ?: Contact(name = item.name)
        val lines = details(c)
        val h = 30f + lines.size * 12f
        canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; style = Paint.Style.STROKE; strokeWidth = 0.8f })
        canvas.drawText(s(if (item.party.role == PartyRole.CUSTOMER) R.string.ledger_customer else R.string.ledger_supplier).uppercase(), margin + 10f, y + 13f, paint(7.5f, brand, bold = true))
        canvas.drawText(clip(c.name, paint(10f, ink, bold = true), contentWidth - 20f), margin + 10f, y + 26f, paint(10f, ink, bold = true))
        lines.forEachIndexed { i, l -> canvas.drawText(clip(l, paint(8.5f, slate), contentWidth - 20f), margin + 10f, y + 38f + i * 12f, paint(8.5f, slate)) }
        y += h + 14f
    }

    private fun summary(item: PartyItem) {
        val gave = item.entries.filter { it.change > 0 }.sumOf { it.change }
        val got = -item.entries.filter { it.change < 0 }.sumOf { it.change }
        val b = item.balance
        val h = 64f
        canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + h), 6f, 6f, Paint().apply { color = mist })
        val cells = listOf(s(R.string.ledger_total_gave) to gave, s(R.string.ledger_total_got) to got)
        cells.forEachIndexed { i, (k, v) ->
            val x = margin + 14f + i * (contentWidth / 3)
            canvas.drawText(k, x, y + 24f, paint(8.5f, slate))
            canvas.drawText(m(v), x, y + 44f, paint(13f, ink, bold = true))
        }
        val label = s(
            when {
                b > 0 -> R.string.ledger_balance_due_to_you
                b < 0 -> R.string.ledger_balance_you_owe
                else -> R.string.ledger_settled
            },
        )
        val x = margin + 14f + 2 * (contentWidth / 3)
        canvas.drawText(label, x, y + 24f, paint(8.5f, slate))
        canvas.drawText(m(kotlin.math.abs(b)), x, y + 44f, paint(15f, if (b > 0) green else if (b < 0) red else ink, bold = true))
        y += h + 18f
    }

    private fun tableHeader() {
        canvas.drawRect(margin, y, pageWidth - margin, y + 18f, Paint().apply { color = navy })
        val labels = listOf(s(R.string.pdf_col_date), s(R.string.pdf_col_particulars), s(R.string.ledger_you_gave), s(R.string.ledger_you_got), s(R.string.ledger_col_balance))
        var x = margin
        labels.forEachIndexed { i, l ->
            val right = i >= 2
            val p = paint(8f, Color.WHITE, bold = true, align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
            canvas.drawText(clip(l, p, cols[i] - 8f), if (right) x + cols[i] - 6f else x + 6f, y + 12.5f, p)
            x += cols[i]
        }
        y += 18f
    }

    private fun row(cells: List<String>, shaded: Boolean, bold: Boolean = false, colors: List<Int?> = emptyList()) {
        if (shaded || bold) canvas.drawRect(margin, y, pageWidth - margin, y + 16f, Paint().apply { color = if (bold) Color.rgb(226, 232, 240) else mist })
        var x = margin
        cells.forEachIndexed { i, text ->
            val right = i >= 2
            val p = paint(8f, colors.getOrNull(i) ?: ink, bold = bold, align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
            canvas.drawText(clip(text, p, cols[i] - 10f), if (right) x + cols[i] - 6f else x + 6f, y + 11f, p)
            x += cols[i]
        }
        canvas.drawLine(margin, y + 16f, pageWidth - margin, y + 16f, Paint().apply { color = line; strokeWidth = 0.3f })
        y += 16f
    }

    private fun balanceText(b: Long) = when {
        b > 0 -> m(b) + " " + s(R.string.ledger_dr)
        b < 0 -> m(-b) + " " + s(R.string.ledger_cr)
        else -> m(0)
    }

    private fun entries(item: PartyItem, accountNames: Map<Long, String>) {
        canvas.drawText(s(R.string.pdf_entries), margin, y + 10f, paint(10.5f, navy, bold = true))
        y += 18f
        tableHeader()
        if (item.entries.isEmpty()) row(listOf("", s(R.string.pdf_no_entries), "", "", ""), shaded = false)
        item.entries.forEachIndexed { i, e ->
            if (y + 16f > bottomLimit) {
                newPage()
                tableHeader()
            }
            val label = s(
                when (e.kind) {
                    LedgerKind.GAVE_GOODS -> R.string.ledger_kind_gave_goods
                    LedgerKind.GAVE_MONEY -> R.string.ledger_kind_gave_money
                    LedgerKind.GOT_GOODS -> R.string.ledger_kind_got_goods
                    LedgerKind.GOT_MONEY -> R.string.ledger_kind_got_money
                    LedgerKind.OPENING -> R.string.ledger_kind_opening
                },
            )
            val particulars = (listOf(label) + listOfNotNull(e.accountId?.let { accountNames[it] }, e.note.takeIf { it.isNotBlank() })).joinToString(" · ")
            row(
                listOf(if (e.kind == LedgerKind.OPENING) "" else Dates.medium(e.date), particulars, if (e.change > 0) m(e.change) else "", if (e.change < 0) m(-e.change) else "", balanceText(e.balanceAfter)),
                shaded = i % 2 == 1,
                colors = listOf(null, null, red, green, null),
            )
        }
        if (y + 16f > bottomLimit) newPage()
        val closing = maxOf(LocalDate.now(), item.entries.lastOrNull()?.date ?: LocalDate.now())
        row(listOf(Dates.medium(closing), s(R.string.pdf_closing_balance), "", "", balanceText(item.balance)), shaded = false, bold = true)
        canvas.drawText(s(R.string.ledger_dr_cr_note), margin, y + 12f, paint(7.5f, slate))
        y += 30f
    }

    private fun payment(item: PartyItem, me: Contact, due: Long) {
        val qr = me.upi.isNotBlank() && currency.code == "INR"
        val lines = listOfNotNull(
            me.bankName.takeIf { it.isNotBlank() }?.let { s(R.string.contact_bank) to it },
            me.bankAccount.takeIf { it.isNotBlank() }?.let { s(R.string.contact_bank_account) to it },
            me.ifsc.takeIf { it.isNotBlank() }?.let { s(R.string.contact_ifsc) to it },
            me.upi.takeIf { it.isNotBlank() }?.let { s(R.string.contact_upi) to it },
        )
        val qrSize = 104f
        val h = maxOf(26f + lines.size * 14f, if (qr) qrSize + 30f else 0f) + 8f
        if (y + h + 24f > bottomLimit) newPage()
        canvas.drawText(s(R.string.pdf_how_to_pay), margin, y + 10f, paint(10.5f, navy, bold = true))
        y += 18f
        canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; style = Paint.Style.STROKE; strokeWidth = 0.8f })
        canvas.drawText(s(R.string.pdf_pay_to, me.name), margin + 12f, y + 18f, paint(9f, ink, bold = true))
        lines.forEachIndexed { i, (k, v) ->
            val ly = y + 34f + i * 14f
            canvas.drawText(k, margin + 12f, ly, paint(8.5f, slate))
            canvas.drawText(v, margin + 110f, ly, paint(9f, ink, bold = true))
        }
        if (qr) {
            val link = UpiLink.build(me.upi, me.name, due, s(R.string.ledger_upi_note, item.name))
            val qx = pageWidth - margin - qrSize - 14f
            val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
            val cell = qrSize / matrix.width
            val dark = Paint().apply { color = Color.BLACK }
            for (r in 0 until matrix.height) for (c in 0 until matrix.width) {
                if (matrix.get(c, r)) canvas.drawRect(qx + c * cell, y + 8f + r * cell, qx + (c + 1) * cell + 0.2f, y + 8f + (r + 1) * cell + 0.2f, dark)
            }
            canvas.drawText(s(R.string.pdf_scan_to_pay, m(due)), qx + qrSize / 2, y + qrSize + 20f, paint(8.5f, ink, bold = true, align = Paint.Align.CENTER))
        }
        y += h + 14f
    }

    private fun note(text: String) {
        val p = paint(8f, slate)
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) > contentWidth && current.isNotEmpty()) {
                if (y + 12f > bottomLimit) newPage()
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
