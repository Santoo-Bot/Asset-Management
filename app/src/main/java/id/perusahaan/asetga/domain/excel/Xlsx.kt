package id.perusahaan.asetga.domain.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

/** Nilai sel: teks atau angka (angka disimpan numerik agar bisa dihitung di Excel). */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: Double) : Cell
}

fun text(v: String?) = Cell.Text(v.orEmpty())
fun num(v: Number) = Cell.Number(v.toDouble())

/** Dropdown pilihan pada kolom [column] (0-based) untuk baris data 2..[lastRow]. */
data class ListValidation(val column: Int, val values: List<String>, val lastRow: Int = 5000)

data class Sheet(
    val name: String,
    val header: List<String>,
    val rows: List<List<Cell>>,
    /** Lebar kolom (karakter); default dihitung dari header. */
    val widths: List<Int>? = null,
    val validations: List<ListValidation> = emptyList(),
)

/**
 * Penulis XLSX minimal (Office Open XML) tanpa dependensi: header tebal + beku, sel teks inline
 * dan angka. Cukup untuk export laporan dan template import.
 */
object XlsxWriter {
    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty())
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", buildString {
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
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s ->
                    append("""<sheet name="${esc(sheetName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach {
                    append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
                }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            })
            // Style 0 = normal, 1 = header tebal berlatar biru muda, 2 = angka ribuan.
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.##"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD7E5FF"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>""")
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        val widths = s.widths ?: s.header.map { (it.length + 4).coerceIn(10, 40) }
        append("<cols>")
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        append("""<row r="1">""")
        s.header.forEachIndexed { c, h -> append("""<c r="${ref(c, 1)}" t="inlineStr" s="1"><is><t>${esc(h)}</t></is></c>""") }
        append("</row>")
        s.rows.forEachIndexed { r, row ->
            val rowNum = r + 2
            append("""<row r="$rowNum">""")
            row.forEachIndexed { c, cell ->
                when (cell) {
                    is Cell.Text -> if (cell.value.isNotEmpty()) {
                        append("""<c r="${ref(c, rowNum)}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                    }
                    is Cell.Number -> {
                        val v = if (cell.value == Math.rint(cell.value) && kotlin.math.abs(cell.value) < 1e15) cell.value.toLong().toString() else cell.value.toString()
                        append("""<c r="${ref(c, rowNum)}" s="2"><v>$v</v></c>""")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.validations.isNotEmpty()) {
            append("""<dataValidations count="${s.validations.size}">""")
            s.validations.forEach { v ->
                val col = columnName(v.column)
                val list = v.values.joinToString(",") { it.replace("\"", "").replace(",", " ") }
                require(list.length <= 255) { "Daftar dropdown terlalu panjang" }
                append(
                    """<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" """ +
                        """errorTitle="Nilai tidak valid" error="Pilih dari daftar" sqref="${col}2:$col${v.lastRow}">""" +
                        """<formula1>&quot;${esc(list)}&quot;</formula1></dataValidation>""",
                )
            }
            append("</dataValidations>")
        }
        append("</worksheet>")
    }

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

    private fun ref(col: Int, row: Int) = columnName(col) + row

    private fun sheetName(name: String) = name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31)

    private val invalidXml = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    fun esc(s: String): String = s.replace(invalidXml, "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/** Pembaca XLSX minimal: lembar pertama, mengembalikan baris berisi teks (sel kosong = ""). */
object XlsxReader {
    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
    /** Batas lembar kerja Excel: 1.048.576 baris × 16.384 kolom. Nomor di luar itu = file rusak/berbahaya. */
    const val MAX_ROW = 1_048_576
    const val MAX_COLUMN = 16_384

    fun readFirstSheet(input: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" || name.startsWith("xl/worksheets/sheet")) {
                    val bytes = readLimited(zip)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_TOTAL_BYTES) throw IllegalArgumentException("File terlalu besar")
                    entries[name] = bytes
                }
                e = zip.nextEntry
            }
        }
        val sheetPath = firstSheetPath(entries) ?: throw IllegalArgumentException("File bukan XLSX yang valid (lembar kerja tidak ditemukan)")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheet = entries[sheetPath] ?: throw IllegalArgumentException("Lembar kerja kosong")
        return parseSheet(sheet, shared)
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalArgumentException("File terlalu besar")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Lembar pertama sesuai urutan di workbook.xml (bukan sekadar sheet1.xml). */
    private fun firstSheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries["xl/workbook.xml"]
        val rels = entries["xl/_rels/workbook.xml.rels"]
        if (workbook != null && rels != null) {
            var firstRid: String? = null
            parse(workbook, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    if (firstRid == null && local(qName) == "sheet") {
                        firstRid = (0 until attrs.length).firstOrNull { local(attrs.getQName(it)) == "id" }?.let { attrs.getValue(it) }
                    }
                }
            })
            var target: String? = null
            parse(rels, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    if (local(qName) == "Relationship" && attrs.getValue("Id") == firstRid) target = attrs.getValue("Target")
                }
            })
            target?.let { t ->
                val path = if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("./")
                if (entries.containsKey(path)) return path
            }
        }
        return entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.minOrNull()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        parse(bytes, object : DefaultHandler() {
            var current: StringBuilder? = null
            var inT = false
            var inRph = false
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (local(qName)) {
                    "si" -> current = StringBuilder()
                    "t" -> inT = true
                    "rPh" -> inRph = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "si" -> { list += current.toString(); current = null }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && !inRph) current?.appendRange(ch, start, start + length)
            }
        })
        return list
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        parse(bytes, object : DefaultHandler() {
            var rowIndex = 0
            var col = 0
            var type: String? = null
            val value = StringBuilder()
            var capture = false
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (local(qName)) {
                    "row" -> {
                        rowIndex = attrs.getValue("r")?.toIntOrNull() ?: (rowIndex + 1)
                        if (rowIndex !in 1..MAX_ROW) throw IllegalArgumentException("File tidak valid: nomor baris di luar batas Excel")
                    }
                    "c" -> {
                        col = attrs.getValue("r")?.let(::columnIndex) ?: (col + 1)
                        if (col !in 0 until MAX_COLUMN) throw IllegalArgumentException("File tidak valid: nomor kolom di luar batas Excel")
                        type = attrs.getValue("t")
                        value.setLength(0)
                    }
                    "v", "t" -> capture = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (text.isNotEmpty()) rows.getOrPut(rowIndex) { mutableMapOf() }[col] = text
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) value.appendRange(ch, start, start + length)
            }
        })
        if (rows.isEmpty()) return emptyList()
        val lastRow = rows.lastKey()
        return (1..lastRow).map { r ->
            val cells = rows[r] ?: return@map emptyList()
            val maxCol = cells.keys.maxOrNull() ?: -1
            (0..maxCol).map { cells[it].orEmpty() }
        }
    }

    /** "B12" → 1 (0-based). */
    fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
            if (n > MAX_COLUMN) return MAX_COLUMN // di luar batas; cegah overflow pada referensi sangat panjang
        }
        return n - 1
    }

    private fun local(qName: String) = qName.substringAfter(':')

    /** Bagian XLSX tidak pernah memakai DTD. Penolakan di sini tidak bergantung pada dukungan fitur parser. */
    private fun rejectDtd(bytes: ByteArray) {
        // XML UTF-16 (BOM FE FF / FF FE) bisa menyembunyikan "<!DOCTYPE"; Excel tidak pernah menulisnya.
        if (bytes.size >= 2 && ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) || (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))) {
            throw IllegalArgumentException("File tidak valid: pengodean XML tidak didukung")
        }
        val pattern = "<!doctype".toByteArray(Charsets.US_ASCII)
        val last = bytes.size - pattern.size
        var i = 0
        while (i <= last) {
            if (bytes[i] == '<'.code.toByte()) {
                var k = 1
                while (k < pattern.size && (bytes[i + k].toInt() or 0x20).toByte() == pattern[k]) k++
                if (k == pattern.size) throw IllegalArgumentException("File tidak valid: DOCTYPE/ENTITY tidak diizinkan")
            }
            i++
        }
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        rejectDtd(bytes)
        val factory = SAXParserFactory.newInstance()
        // Cegah XXE; tidak semua parser mendukung fitur ini, jadi diabaikan bila tidak tersedia.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }
}

object CsvWriter {
    /** Pemisah titik koma agar langsung terbaca rapi oleh Excel berbahasa Indonesia. BOM UTF-8 disertakan. */
    fun write(header: List<String>, rows: List<List<String>>, out: OutputStream, separator: Char = ';') {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("﻿")
        (listOf(header) + rows).forEach { row ->
            w.write(row.joinToString(separator.toString()) { field(it, separator) })
            w.write("\r\n")
        }
        w.flush()
    }

    private fun field(v: String, sep: Char): String {
        // Cegah formula injection saat dibuka di spreadsheet.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@" && v.toDoubleOrNull() == null) "'$v" else v
        return if (safe.any { it == sep || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
