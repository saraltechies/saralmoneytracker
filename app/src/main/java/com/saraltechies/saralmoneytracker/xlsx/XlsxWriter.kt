package com.saraltechies.saralmoneytracker.xlsx

import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A money cell, in hundredths; written as a number with two decimals so Excel can sum it. */
@JvmInline
value class MoneyCell(val minor: Long)

/**
 * Writes a minimal .xlsx (Office Open XML) workbook that Excel, Google Sheets and LibreOffice open. Cells are
 * text, numbers, [LocalDate] (a real date, formatted "dd mmm yyyy") or [MoneyCell]. The first row of each
 * sheet is a bold header, frozen when scrolling.
 */
class XlsxWriter {

    private class Sheet(val name: String, val rows: List<List<Any?>>, val widths: List<Int>)

    private val sheets = mutableListOf<Sheet>()

    fun sheet(name: String, rows: List<List<Any?>>, widths: List<Int> = emptyList()) {
        sheets += Sheet(name.take(31).replace(Regex("[\\[\\]:*?/\\\\]"), " "), rows, widths)
    }

    fun write(out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(path: String, xml: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes())
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", workbook())
        entry("xl/_rels/workbook.xml.rels", workbookRels())
        entry("xl/styles.xml", STYLES)
        sheets.forEachIndexed { i, sheet -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        zip.finish()
        zip.flush()
    }

    private fun contentTypes() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels() = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach {
            append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sheetXml(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (sheet.widths.isNotEmpty()) {
            append("<cols>")
            sheet.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                if (value != null) append(cell(columnName(c) + (r + 1), value, header = r == 0))
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun cell(ref: String, value: Any, header: Boolean): String = when (value) {
        is MoneyCell -> """<c r="$ref" s="2"><v>${java.math.BigDecimal.valueOf(value.minor, 2).toPlainString()}</v></c>"""
        is LocalDate -> """<c r="$ref" s="1"><v>${ExcelDates.toSerial(value)}</v></c>"""
        is Number -> """<c r="$ref"><v>$value</v></c>"""
        else -> {
            val style = if (header) """ s="3"""" else ""
            """<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${esc(value.toString())}</t></is></c>"""
        }
    }

    companion object {
        fun columnName(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append('A' + rem)
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        /** Escapes text for XML and drops control characters XML 1.0 can't hold. */
        fun esc(s: String): String = buildString {
            for (ch in s) when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }

        private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

        // Style 0 default, 1 date, 2 money (#,##0.00), 3 bold header.
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd mmm yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>"""
    }
}

/** Excel stores dates as days since 30 December 1899 (counting its 1900 leap-year bug). */
object ExcelDates {
    private val EPOCH = LocalDate.of(1899, 12, 30)

    fun toSerial(date: LocalDate): Long = date.toEpochDay() - EPOCH.toEpochDay()

    fun fromSerial(serial: Double): LocalDate? =
        if (serial < 1 || serial > 2_958_465) null else EPOCH.plusDays(serial.toLong())
}
