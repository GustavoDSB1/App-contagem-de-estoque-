package com.estoque.contagem.excel

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lê planilhas .xlsx (geradas por este app, pelo Excel, Google Planilhas, WPS...)
 * e devolve o texto de cada célula, aba por aba.
 */
object XlsxReader {

    class NotXlsxException : Exception("Esse arquivo não é uma planilha Excel (.xlsx)")

    private const val MAX_PART_BYTES = 30L * 1024 * 1024

    /** Abas na ordem da planilha: nome da aba -> linhas -> células (texto). */
    fun read(input: InputStream): LinkedHashMap<String, List<List<String>>> {
        val parts = unzip(input)
        if (!parts.containsKey("xl/workbook.xml")) throw NotXlsxException()

        val sharedStrings = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val rels = parts["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()

        val result = LinkedHashMap<String, List<List<String>>>()
        val workbook = parse(parts.getValue("xl/workbook.xml"))
        elements(workbook.documentElement, "sheet").forEachIndexed { i, sheet ->
            val name = sheet.getAttribute("name").ifBlank { "Planilha${i + 1}" }
            val relId = attributeByLocalName(sheet, "id")
            val path = relId?.let { rels[it] } ?: "xl/worksheets/sheet${i + 1}.xml"
            val bytes = parts[path] ?: return@forEachIndexed
            result[name] = parseSheet(bytes, sharedStrings)
        }
        return result
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.trimStart('/')
                    val wanted = name.endsWith(".xml") || name.endsWith(".rels")
                    if (!entry.isDirectory && wanted && name.startsWith("xl/")) {
                        parts[name] = readLimited(zip)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: NotXlsxException) {
            throw e
        } catch (e: Exception) {
            throw NotXlsxException()
        }
        return parts
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_PART_BYTES) throw IllegalStateException("Planilha grande demais")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun Node.local(): String = localName ?: nodeName.substringAfter(':')

    /** Todos os descendentes com esse nome (ignorando prefixo de namespace). */
    private fun elements(root: Element, local: String): List<Element> {
        val list = root.getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun children(parent: Element, local: String): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && it.local() == local }
            .map { it as Element }
    }

    private fun attributeByLocalName(element: Element, local: String): String? {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.local() == local && a.nodeName.contains(':')) return a.nodeValue
        }
        return null
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val doc = parse(bytes)
        return elements(doc.documentElement, "Relationship").associate { rel ->
            val target = rel.getAttribute("Target")
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/" + target.removePrefix("./")
            rel.getAttribute("Id") to path
        }
    }

    /** Texto de um <si> ou <is>, juntando as partes formatadas e ignorando a fonética. */
    private fun richText(element: Element): String =
        elements(element, "t")
            .filter { it.parentNode?.local() != "rPh" }
            .joinToString("") { it.textContent ?: "" }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parse(bytes)
        return children(doc.documentElement, "si").map { richText(it) }
    }

    fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = parse(bytes)
        val rows = mutableListOf<List<String>>()
        elements(doc.documentElement, "row").forEach { row ->
            val rowNumber = row.getAttribute("r").toIntOrNull()
            // Linhas vazias puladas no XML continuam existindo na planilha
            if (rowNumber != null) while (rows.size < rowNumber - 1) rows += emptyList<String>()

            val cells = mutableListOf<String>()
            children(row, "c").forEach { c ->
                val col = columnIndex(c.getAttribute("r")) ?: cells.size
                val value = cellValue(c, sharedStrings)
                while (cells.size < col) cells += ""
                if (col < cells.size) cells[col] = value else cells += value
            }
            rows += cells
        }
        return rows
    }

    private fun cellValue(c: Element, sharedStrings: List<String>): String {
        val v = children(c, "v").firstOrNull()?.textContent?.trim() ?: ""
        return when (c.getAttribute("t")) {
            "s" -> v.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
            "inlineStr" -> children(c, "is").firstOrNull()?.let { richText(it) } ?: ""
            "e" -> ""
            else -> v // número, fórmula de texto ("str") ou booleano
        }
    }
}
