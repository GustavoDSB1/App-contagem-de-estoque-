package com.estoque.contagem.excel

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Estilos de célula disponíveis na planilha.
 * A posição (ordinal) de cada item é o índice usado no styles.xml.
 */
enum class CellStyle(
    val font: Int,
    val fill: Int,
    val border: Int,
    val numFmt: Int = 0,
    val hAlign: String? = null
) {
    DEFAULT(0, 0, 0),
    TITLE(1, 0, 0),
    SUBTITLE(2, 0, 0),
    INFO(5, 0, 0),
    HEADER(3, 2, 1, hAlign = "center"),
    TEXT(0, 0, 1),
    TEXT_ALT(0, 3, 1),
    CENTER(0, 0, 1, hAlign = "center"),
    CENTER_ALT(0, 3, 1, hAlign = "center"),
    NUMBER(4, 0, 1, numFmt = 3, hAlign = "center"),
    NUMBER_ALT(4, 3, 1, numFmt = 3, hAlign = "center"),
    WARN(6, 0, 1, hAlign = "center"),
    WARN_ALT(6, 3, 1, hAlign = "center"),
    GOOD(7, 0, 1, hAlign = "center"),
    GOOD_ALT(7, 3, 1, hAlign = "center"),
    TOTAL_LABEL(4, 4, 1),
    TOTAL_NUMBER(4, 4, 1, numFmt = 3, hAlign = "center"),
    // Com casas decimais (#,##0.00) para kg, litros etc.
    NUMBER_DEC(4, 0, 1, numFmt = 4, hAlign = "center"),
    NUMBER_DEC_ALT(4, 3, 1, numFmt = 4, hAlign = "center"),
    TOTAL_NUMBER_DEC(4, 4, 1, numFmt = 4, hAlign = "center"),
    // Dinheiro: "R$ 1.234,50" (formato próprio 164)
    MONEY(0, 0, 1, numFmt = 164, hAlign = "right"),
    MONEY_ALT(0, 3, 1, numFmt = 164, hAlign = "right"),
    TOTAL_MONEY(4, 4, 1, numFmt = 164, hAlign = "right")
}

sealed class Cell {
    abstract val style: CellStyle

    data class Text(val value: String, override val style: CellStyle = CellStyle.TEXT) : Cell()
    data class Number(val value: Double, override val style: CellStyle = CellStyle.NUMBER) : Cell() {
        constructor(value: Long, style: CellStyle = CellStyle.NUMBER) : this(value.toDouble(), style)
    }
    data class Formula(
        val formula: String,
        val cachedValue: Double,
        override val style: CellStyle = CellStyle.TOTAL_NUMBER
    ) : Cell()
    data class Blank(override val style: CellStyle) : Cell()
}

/** Imagem (ex.: logo da empresa) posicionada sobre a aba. Tamanho em pixels. */
data class SheetImage(
    val bytes: ByteArray,
    /** "png" ou "jpeg" */
    val extension: String,
    val column: Int,
    val row: Int,
    val widthPx: Int,
    val heightPx: Int,
    val offsetXPx: Int = 0,
    val offsetYPx: Int = 0
)

data class Sheet(
    val name: String,
    val columnWidths: List<Double>,
    /** Cada linha é uma lista de células; null deixa a célula vazia. */
    val rows: List<List<Cell?>>,
    /** Faixas mescladas, ex.: "A1:D1". */
    val merges: List<String> = emptyList(),
    /** Altura personalizada por linha (índice começando em 0). */
    val rowHeights: Map<Int, Double> = emptyMap(),
    /** Quantidade de linhas fixas no topo ao rolar. */
    val frozenRows: Int = 0,
    val image: SheetImage? = null,
    /** Faixa com filtros do Excel no cabeçalho (ex.: "A6:F20"). */
    val autoFilter: String? = null
)

/**
 * Gera arquivos .xlsx (Excel) sem bibliotecas externas.
 * O formato .xlsx é um ZIP com alguns arquivos XML dentro.
 */
object XlsxWriter {

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "A planilha precisa de pelo menos uma aba" }
        val zip = ZipOutputStream(out)
        zip.put("[Content_Types].xml", contentTypes(sheets))
        zip.put("_rels/.rels", rootRels())
        zip.put("xl/workbook.xml", workbook(sheets))
        zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        zip.put("xl/styles.xml", styles())
        sheets.forEachIndexed { i, sheet ->
            val n = i + 1
            zip.put("xl/worksheets/sheet$n.xml", worksheet(sheet))
            sheet.image?.let { img ->
                zip.putBytes("xl/media/image$n.${img.extension}", img.bytes)
                zip.put("xl/drawings/drawing$n.xml", drawing(img))
                zip.put(
                    "xl/drawings/_rels/drawing$n.xml.rels",
                    relationships("rId1", "$NS_REL/image", "../media/image$n.${img.extension}")
                )
                zip.put(
                    "xl/worksheets/_rels/sheet$n.xml.rels",
                    relationships("rId1", "$NS_REL/drawing", "../drawings/drawing$n.xml")
                )
            }
        }
        zip.finish()
        zip.flush()
    }

    /** Converte índice de coluna (0 = A) para letra(s) do Excel. */
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

    private fun ZipOutputStream.put(path: String, content: String) = putBytes(path, content.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun relationships(id: String, type: String, target: String) = HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"$id\" Type=\"$type\" Target=\"$target\"/>" +
        "</Relationships>"

    private const val EMU_PER_PX = 9525L

    private fun drawing(img: SheetImage): String {
        val cx = img.widthPx * EMU_PER_PX
        val cy = img.heightPx * EMU_PER_PX
        return HEADER +
            "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"$NS_REL\">" +
            "<xdr:oneCellAnchor>" +
            "<xdr:from><xdr:col>${img.column}</xdr:col><xdr:colOff>${img.offsetXPx * EMU_PER_PX}</xdr:colOff>" +
            "<xdr:row>${img.row}</xdr:row><xdr:rowOff>${img.offsetYPx * EMU_PER_PX}</xdr:rowOff></xdr:from>" +
            "<xdr:ext cx=\"$cx\" cy=\"$cy\"/>" +
            "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"2\" name=\"Logo\"/>" +
            "<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>" +
            "<xdr:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>" +
            "<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>" +
            "<xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>"
    }

    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun contentTypes(sheets: List<Sheet>) = buildString {
        val sheetCount = sheets.size
        append(HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sheets.mapNotNull { it.image?.extension }.distinct().forEach { ext ->
            append("<Default Extension=\"$ext\" ContentType=\"image/$ext\"/>")
        }
        sheets.forEachIndexed { i, sheet ->
            if (sheet.image != null) {
                append("<Override PartName=\"/xl/drawings/drawing${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            }
        }
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRels() = HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(HEADER)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        sheets.forEachIndexed { i, sheet ->
            append("<sheet name=\"${escape(sheetName(sheet.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append(HEADER)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            append("<Relationship Id=\"rId$i\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun font(color: String, size: Int = 11, bold: Boolean = false, italic: Boolean = false) =
        "<font>" + (if (bold) "<b/>" else "") + (if (italic) "<i/>" else "") +
            "<sz val=\"$size\"/><color rgb=\"$color\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"

    private fun solidFill(color: String) =
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$color\"/><bgColor indexed=\"64\"/></patternFill></fill>"

    private fun styles() = buildString {
        val fonts = listOf(
            font("FF1F2937"),                          // 0 normal
            font("FF4338CA", size = 18, bold = true),  // 1 título
            font("FF6B7280", italic = true),           // 2 subtítulo
            font("FFFFFFFF", bold = true),             // 3 cabeçalho
            font("FF111827", size = 12, bold = true),  // 4 negrito
            font("FF374151", bold = true),             // 5 informação
            font("FFD97706", bold = true),             // 6 laranja (atenção)
            font("FF059669", bold = true)              // 7 verde (ok)
        )
        val fills = listOf(
            "<fill><patternFill patternType=\"none\"/></fill>",
            "<fill><patternFill patternType=\"gray125\"/></fill>",
            solidFill("FF4F46E5"), // 2 cabeçalho
            solidFill("FFF5F7FF"), // 3 linha alternada
            solidFill("FFE0E7FF")  // 4 total
        )
        val side = { tag: String -> "<$tag style=\"thin\"><color rgb=\"FFD1D5DB\"/></$tag>" }
        val borders = listOf(
            "<border><left/><right/><top/><bottom/><diagonal/></border>",
            "<border>${side("left")}${side("right")}${side("top")}${side("bottom")}<diagonal/></border>"
        )

        append(HEADER)
        append("<styleSheet xmlns=\"$NS_MAIN\">")
        // Formato de dinheiro em reais (id 164 = primeiro formato próprio)
        append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;R\$&quot;\\ #,##0.00\"/></numFmts>")
        append("<fonts count=\"${fonts.size}\">${fonts.joinToString("")}</fonts>")
        append("<fills count=\"${fills.size}\">${fills.joinToString("")}</fills>")
        append("<borders count=\"${borders.size}\">${borders.joinToString("")}</borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"${CellStyle.entries.size}\">")
        for (s in CellStyle.entries) {
            append("<xf numFmtId=\"${s.numFmt}\" fontId=\"${s.font}\" fillId=\"${s.fill}\" borderId=\"${s.border}\" xfId=\"0\"")
            if (s.numFmt != 0) append(" applyNumberFormat=\"1\"")
            if (s.font != 0) append(" applyFont=\"1\"")
            if (s.fill != 0) append(" applyFill=\"1\"")
            if (s.border != 0) append(" applyBorder=\"1\"")
            append(" applyAlignment=\"1\"><alignment vertical=\"center\"")
            if (s.hAlign != null) append(" horizontal=\"${s.hAlign}\"")
            append("/></xf>")
        }
        append("</cellXfs>")
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }

    private fun worksheet(sheet: Sheet) = buildString {
        append(HEADER)
        append("<worksheet xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">")
        append("<sheetViews><sheetView showGridLines=\"0\" workbookViewId=\"0\">")
        if (sheet.frozenRows > 0) {
            append("<pane ySplit=\"${sheet.frozenRows}\" topLeftCell=\"A${sheet.frozenRows + 1}\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        }
        append("</sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"18\"/>")
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { i, w ->
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 1
            append("<row r=\"$rowNum\"")
            sheet.rowHeights[r]?.let { append(" ht=\"$it\" customHeight=\"1\"") }
            append(">")
            row.forEachIndexed { c, cell ->
                if (cell != null) append(cellXml("${columnName(c)}$rowNum", cell))
            }
            append("</row>")
        }
        append("</sheetData>")
        sheet.autoFilter?.let { append("<autoFilter ref=\"$it\"/>") }
        if (sheet.merges.isNotEmpty()) {
            append("<mergeCells count=\"${sheet.merges.size}\">")
            sheet.merges.forEach { append("<mergeCell ref=\"$it\"/>") }
            append("</mergeCells>")
        }
        append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>")
        if (sheet.image != null) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }

    private fun cellXml(ref: String, cell: Cell): String {
        val s = cell.style.ordinal
        return when (cell) {
            is Cell.Text ->
                "<c r=\"$ref\" s=\"$s\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>"
            is Cell.Number -> "<c r=\"$ref\" s=\"$s\"><v>${numberText(cell.value)}</v></c>"
            is Cell.Formula -> "<c r=\"$ref\" s=\"$s\"><f>${escape(cell.formula)}</f><v>${numberText(cell.cachedValue)}</v></c>"
            is Cell.Blank -> "<c r=\"$ref\" s=\"$s\"/>"
        }
    }

    /** Número no formato do XML: 12 -> "12", 2.5 -> "2.5" (sem notação científica). */
    fun numberText(value: Double): String =
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
        else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** Nome de aba válido: até 31 caracteres e sem : \ / ? * [ ] */
    private fun sheetName(name: String): String =
        name.replace(Regex("[:\\\\/?*\\[\\]]"), " ").take(31).ifBlank { "Planilha" }

    /** Escapa caracteres especiais e remove os que o XML não aceita. */
    fun escape(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
