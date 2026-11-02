package com.saraltechies.saralmoneytracker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import com.saraltechies.saralmoneytracker.data.BillView
import com.saraltechies.saralmoneytracker.model.BillMath
import com.saraltechies.saralmoneytracker.model.Contact
import com.saraltechies.saralmoneytracker.model.CurrencyInfo
import com.saraltechies.saralmoneytracker.model.Money
import com.saraltechies.saralmoneytracker.model.UpiLink
import com.saraltechies.saralmoneytracker.ui.components.Dates
import java.io.File
import java.io.OutputStream

/**
 * A bill as an A4 PDF, drawn with Android's own PDF support: your shop's details, the customer's, every item, the
 * totals with GST, what's been paid, and, when something is due, your bank details and a UPI scan-to-pay code.
 */
class BillPdf(private val context: Context, private val currency: CurrencyInfo) {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val right = pageWidth - margin
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

    private fun clip(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

    fun write(view: BillView, me: Contact?, out: OutputStream) {
        doc = PdfDocument()
        pageNumber = 0
        footer = s(R.string.bill_pdf_footer)
        try {
            newPage()
            header(view, me)
            customer(view)
            items(view)
            totals(view)
            if (view.payment.due > 0 && me != null && me.hasBankDetails) payment(view, me)
            if (view.bill.note.isNotBlank()) text(view.bill.note)
            text(s(R.string.bill_pdf_thanks))
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
        canvas.drawLine(margin, pageHeight - 40f, right, pageHeight - 40f, Paint().apply { color = line; strokeWidth = 0.5f })
        canvas.drawText(footer, margin, pageHeight - 28f, paint(7.5f, slate))
        canvas.drawText(s(R.string.pdf_page, pageNumber), right, pageHeight - 28f, paint(7.5f, slate, align = Paint.Align.RIGHT))
        doc.finishPage(p)
        page = null
    }

    private fun details(c: Contact): List<String> = listOfNotNull(
        c.address.takeIf { it.isNotBlank() },
        listOf(c.phone, c.email).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
        c.taxId.takeIf { it.isNotBlank() }?.let { s(R.string.pdf_tax_id, it) },
    )

    private fun header(view: BillView, me: Contact?) {
        val top = y
        var ly = top + 14f
        if (me != null && me.name.isNotBlank()) {
            canvas.drawText(clip(me.name, paint(15f, navy, bold = true), contentWidth * 0.6f), margin, ly, paint(15f, navy, bold = true))
            ly += 6f
            for (l in details(me)) {
                ly += 11f
                canvas.drawText(clip(l, paint(8.5f, slate), contentWidth * 0.6f), margin, ly, paint(8.5f, slate))
            }
        }
        val title = s(if (view.totals.hasGst && me?.taxId?.isNotBlank() == true) R.string.bill_pdf_tax_invoice else R.string.bill_pdf_title)
        canvas.drawText(title.uppercase(), right, top + 14f, paint(14f, brand, bold = true, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.bill_pdf_number, view.bill.number), right, top + 29f, paint(9f, ink, bold = true, align = Paint.Align.RIGHT))
        canvas.drawText(s(R.string.pdf_date, Dates.medium(view.bill.date)), right, top + 41f, paint(8.5f, slate, align = Paint.Align.RIGHT))
        y = maxOf(ly, top + 41f) + 12f
        canvas.drawRect(margin, y, right, y + 2f, Paint().apply { color = brand })
        y += 14f
    }

    private fun customer(view: BillView) {
        val lines = view.contact?.let { details(it) }.orEmpty()
        canvas.drawText(s(R.string.bill_pdf_to).uppercase(), margin, y + 8f, paint(7.5f, brand, bold = true))
        y += 12f
        canvas.drawText(clip(view.customerName.ifBlank { s(R.string.bill_walk_in) }, paint(10f), contentWidth), margin, y + 10f, paint(10f, ink, bold = true))
        y += 12f
        for (l in lines) {
            canvas.drawText(clip(l, paint(8.5f), contentWidth), margin, y + 10f, paint(8.5f, slate))
            y += 11f
        }
        y += 12f
    }

    private fun columns(gst: Boolean) = if (gst) floatArrayOf(22f, 175f, 56f, 62f, 70f, 40f, 90f) else floatArrayOf(22f, 245f, 0f, 76f, 72f, 0f, 100f)

    private fun itemHeader(cols: FloatArray, gst: Boolean) {
        canvas.drawRect(margin, y, right, y + 18f, Paint().apply { color = navy })
        val labels = listOf("#", s(R.string.bill_item), s(R.string.bill_hsn), s(R.string.bill_quantity), s(R.string.bill_rate), s(R.string.bill_gst), s(R.string.amount))
        var x = margin
        labels.forEachIndexed { i, l ->
            if (cols[i] > 0) {
                val alignRight = i >= 3
                val p = paint(8f, Color.WHITE, bold = true, align = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
                canvas.drawText(clip(l, p, cols[i] - 8f), if (alignRight) x + cols[i] - 6f else x + 6f, y + 12.5f, p)
            }
            x += cols[i]
        }
        y += 18f
    }

    private fun items(view: BillView) {
        val gst = view.totals.hasGst
        val cols = columns(gst)
        itemHeader(cols, gst)
        view.bill.items.forEachIndexed { i, item ->
            if (y + 16f > bottomLimit) {
                newPage()
                itemHeader(cols, gst)
            }
            if (i % 2 == 1) canvas.drawRect(margin, y, right, y + 16f, Paint().apply { color = mist })
            val qty = BillMath.quantityText(item.quantity) + if (item.unit.isBlank()) "" else " " + item.unit
            val cells = listOf(
                (i + 1).toString(), item.name, item.hsn, qty, m(item.rate), if (item.gstRate > 0) BillMath.rateText(item.gstRate) + "%" else "-", m(item.amount),
            )
            var x = margin
            cells.forEachIndexed { c, text ->
                if (cols[c] > 0) {
                    val alignRight = c >= 3
                    val p = paint(8.5f, ink, align = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
                    canvas.drawText(clip(text, p, cols[c] - 10f), if (alignRight) x + cols[c] - 6f else x + 6f, y + 11f, p)
                }
                x += cols[c]
            }
            canvas.drawLine(margin, y + 16f, right, y + 16f, Paint().apply { color = line; strokeWidth = 0.3f })
            y += 16f
        }
        y += 8f
    }

    private fun totalRow(label: String, value: String, bold: Boolean = false, color: Int = ink) {
        if (y + 15f > bottomLimit) newPage()
        val x = margin + contentWidth * 0.5f
        canvas.drawText(label, x, y + 10f, paint(if (bold) 10f else 8.5f, if (bold) navy else slate, bold = bold))
        canvas.drawText(value, right - 6f, y + 10f, paint(if (bold) 11f else 9f, color, bold = bold, align = Paint.Align.RIGHT))
        y += if (bold) 18f else 14f
    }

    private fun totals(view: BillView) {
        val t = view.totals
        totalRow(s(R.string.bill_subtotal), m(t.subtotal))
        if (t.discount > 0) totalRow(s(R.string.bill_discount), "-" + m(t.discount))
        for (tax in t.taxes.filter { it.rate > 0 }) {
            val rate = BillMath.rateText(tax.rate)
            if (view.bill.interState) {
                totalRow(s(R.string.bill_igst, rate), m(tax.tax))
            } else {
                val half = BillMath.rateText(tax.rate / 2)
                totalRow(s(R.string.bill_cgst, half), m(tax.cgst))
                totalRow(s(R.string.bill_sgst, half), m(tax.sgst))
            }
        }
        if (t.hasGst && view.bill.pricesIncludeGst) totalRow(s(R.string.bill_includes_gst), m(t.tax))
        canvas.drawLine(margin + contentWidth * 0.5f, y + 1f, right, y + 1f, Paint().apply { color = line; strokeWidth = 0.6f })
        y += 3f
        totalRow(s(R.string.bill_total), m(t.total), bold = true)
        if (view.payment.paid > 0 && view.payment.due > 0) totalRow(s(R.string.bill_paid), m(view.payment.paid))
        if (view.payment.isPaid) totalRow(s(R.string.bill_status_paid), m(view.payment.paid), bold = true, color = green)
        else totalRow(s(R.string.bill_due), m(view.payment.due), bold = true, color = red)
        y += 12f
    }

    private fun payment(view: BillView, me: Contact) {
        val due = view.payment.due
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
        canvas.drawRoundRect(RectF(margin, y, right, y + h), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = line; style = Paint.Style.STROKE; strokeWidth = 0.8f })
        canvas.drawText(s(R.string.pdf_pay_to, me.name), margin + 12f, y + 18f, paint(9f, ink, bold = true))
        lines.forEachIndexed { i, (k, v) ->
            val ly = y + 34f + i * 14f
            canvas.drawText(k, margin + 12f, ly, paint(8.5f, slate))
            canvas.drawText(v, margin + 110f, ly, paint(9f, ink, bold = true))
        }
        if (qr) {
            val link = UpiLink.build(me.upi, me.name, due, s(R.string.bill_upi_note, view.bill.number))
            val qx = right - qrSize - 14f
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

    private fun text(value: String) {
        val p = paint(8.5f, slate)
        var current = ""
        fun flush() {
            if (y + 12f > bottomLimit) newPage()
            y += 12f
            canvas.drawText(current, margin, y, p)
        }
        for (word in value.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) > contentWidth && current.isNotEmpty()) {
                flush()
                current = word
            } else current = candidate
        }
        if (current.isNotEmpty()) flush()
        y += 4f
    }

    companion object {
        fun fileName(context: Context, view: BillView): String {
            val safe = view.bill.number.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "bill" }
            return context.getString(R.string.bill_file_name, safe) + ".pdf"
        }

        /** Makes the PDF in the cache and opens the share sheet: WhatsApp, Gmail, or a printing app. */
        fun shareIntent(context: Context, view: BillView, me: Contact?, currency: CurrencyInfo): Intent {
            val dir = File(context.cacheDir, "statements").apply { mkdirs() }
            val file = File(dir, fileName(context, view))
            file.outputStream().use { BillPdf(context, currency).write(view, me, it) }
            val uri = StatementFiles.uri(context, file)
            val shop = me?.name?.takeIf { it.isNotBlank() }
            val total = Money.format(view.totals.total, currency)
            val body = if (view.payment.isPaid) context.getString(R.string.bill_share_paid, view.bill.number, total)
            else context.getString(R.string.bill_share_due, view.bill.number, total, Money.format(view.payment.due, currency))
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.bill_share_subject, view.bill.number, shop.orEmpty()).trim())
                .putExtra(Intent.EXTRA_TEXT, listOfNotNull(body, shop).joinToString("\n"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.contact?.email?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            send.clipData = ClipData.newRawUri(file.name, uri)
            return Intent.createChooser(send, context.getString(R.string.bill_share_title)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
