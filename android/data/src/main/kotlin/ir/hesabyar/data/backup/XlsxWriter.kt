package ir.hesabyar.data.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal real OOXML (.xlsx) writer. No HTML/CSV masquerading as Excel. */
object XlsxWriter {
    data class Sheet(val name: String, val rows: List<List<Any?>>)

    fun write(file: File, sheets: List<Sheet>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            sheets.forEachIndexed { index, sheet -> entry("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet)) }
        }
    }

    private fun contentTypes(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (i in 1..count) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${escape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("</Relationships>")
    }

    private fun worksheet(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = "${columnName(c)}${r + 1}"
                when (value) {
                    is Long, is Int -> append("""<c r="$ref" t="n"><v>$value</v></c>""")
                    null -> append("""<c r="$ref"/>""")
                    else -> append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
