package com.saraltechies.saralmoneytracker.xlsx

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/** A cell's shown text, and its number when it holds one. */
data class XCell(val text: String, val number: Double?)

/** One worksheet: rows and columns are 0-based. */
class XSheet(val name: String, val rows: Map<Int, Map<Int, XCell>>) {
    fun cell(row: Int, col: Int): XCell? = rows[row]?.get(col)
    val lastRow: Int get() = rows.keys.maxOrNull() ?: -1
}

class NotAnXlsxException(message: String) : IOException(message)

/**
 * Reads the cell values of an .xlsx workbook (Excel 2007+, Google Sheets and LibreOffice exports). Formulas are
 * read as their last calculated value. Only plain JDK classes, so it works on Android and in JVM tests.
 */
object XlsxReader {

    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    fun read(input: InputStream): List<XSheet> {
        val parts = unzip(input)
        val workbook = parts["xl/workbook.xml"] ?: throw NotAnXlsxException("No workbook in this file")
        val builder = builder()
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(parse(builder, it)) } ?: emptyList()
        val rels = parts["xl/_rels/workbook.xml.rels"]?.let { relationships(parse(builder, it)) } ?: emptyMap()

        val sheets = mutableListOf<XSheet>()
        val sheetNodes = parse(builder, workbook).getElementsByTagName("sheet")
        for (i in 0 until sheetNodes.length) {
            val el = sheetNodes.item(i) as Element
            val name = el.getAttribute("name")
            val target = rels[el.getAttribute("r:id")] ?: "worksheets/sheet${i + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val xml = parts[path] ?: continue
            sheets += XSheet(name, cells(parse(builder, xml), shared))
        }
        return sheets
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        var total = 0L
        val zip = ZipInputStream(input)
        var sawEntry = false
        while (true) {
            val entry = zip.nextEntry ?: break
            sawEntry = true
            val name = entry.name.removePrefix("/")
            // Only the parts we read; images, charts and printer settings are skipped.
            val wanted = name == "xl/workbook.xml" || name == "xl/sharedStrings.xml" ||
                name == "xl/_rels/workbook.xml.rels" || name.startsWith("xl/worksheets/") && name.endsWith(".xml")
            if (!wanted) continue
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var size = 0L
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                size += n
                total += n
                if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw IOException("This workbook is too large")
                out.write(buf, 0, n)
            }
            parts[name] = out.toByteArray()
        }
        if (!sawEntry) throw NotAnXlsxException("Not an .xlsx file")
        return parts
    }

    private fun builder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        // Spreadsheets never need a DTD; refusing one blocks entity-expansion tricks. Not every parser has the feature.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder()
    }

    private fun parse(builder: DocumentBuilder, bytes: ByteArray): Element =
        builder.parse(InputSource(ByteArrayInputStream(bytes))).documentElement

    private fun sharedStrings(root: Element): List<String> {
        val items = root.getElementsByTagName("si")
        return List(items.length) { i -> textOf(items.item(i) as Element) }
    }

    /** Text of every <t> inside, skipping phonetic hints (<rPh>). */
    private fun textOf(el: Element): String {
        val sb = StringBuilder()
        val ts = el.getElementsByTagName("t")
        for (j in 0 until ts.length) {
            val t = ts.item(j)
            if (t.parentNode?.nodeName == "rPh") continue
            sb.append(t.textContent)
        }
        return sb.toString()
    }

    private fun relationships(root: Element): Map<String, String> {
        val map = HashMap<String, String>()
        val nodes = root.getElementsByTagName("Relationship")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            map[el.getAttribute("Id")] = el.getAttribute("Target")
        }
        return map
    }

    private fun cells(root: Element, shared: List<String>): Map<Int, Map<Int, XCell>> {
        val rows = HashMap<Int, HashMap<Int, XCell>>()
        val rowNodes = root.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as Element
            val rowIndex = rowEl.getAttribute("r").toIntOrNull()?.minus(1) ?: i
            val cs = rowEl.getElementsByTagName("c")
            for (j in 0 until cs.length) {
                val c = cs.item(j) as Element
                val ref = c.getAttribute("r")
                val col = if (ref.isNotEmpty()) column(ref) else j
                val type = c.getAttribute("t")
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val cell = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }?.let { XCell(it, null) }
                    "inlineStr" -> XCell(textOf(c), null)
                    "str", "e" -> v?.let { XCell(it, null) }
                    "b" -> v?.let { XCell(if (it == "1") "TRUE" else "FALSE", null) }
                    else -> v?.let { XCell(it, it.toDoubleOrNull()) }
                } ?: continue
                if (cell.text.isEmpty() && cell.number == null) continue
                rows.getOrPut(rowIndex) { HashMap() }[col] = cell
            }
        }
        return rows
    }

    /** "C12" → 2. */
    fun column(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }
}
