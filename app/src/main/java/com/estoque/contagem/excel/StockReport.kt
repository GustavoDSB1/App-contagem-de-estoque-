package com.estoque.contagem.excel

import com.estoque.contagem.data.Product
import com.estoque.contagem.data.Qty
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dados da empresa que aparecem no cabeçalho da planilha. */
data class CompanyInfo(
    val name: String = "",
    /** CNPJ ou CPF, como o usuário digitou. */
    val document: String = "",
    /** Logo em PNG (opcional). */
    val logoPng: ByteArray? = null
)

/** Monta as abas da planilha de contagem a partir dos produtos. */
object StockReport {

    private val BR: Locale = Locale.forLanguageTag("pt-BR")

    /** Produtos da lista de exclusão ("fora da planilha") não entram em nenhuma aba. */
    fun build(
        products: List<Product>,
        countStartedAt: Long,
        generatedAt: Long,
        company: CompanyInfo = CompanyInfo()
    ): List<Sheet> {
        val sorted = sortByName(products.filterNot { it.excludeFromExcel })
        return listOf(
            countSheet(sorted, countStartedAt, generatedAt, company),
            historySheet(sorted, generatedAt, company)
        )
    }

    /** Largura e altura de um PNG, lidas do cabeçalho do arquivo. */
    fun pngSize(png: ByteArray): Pair<Int, Int>? {
        if (png.size < 24 || png[1] != 'P'.code.toByte() || png[2] != 'N'.code.toByte()) return null
        fun int(at: Int) = ((png[at].toInt() and 0xFF) shl 24) or ((png[at + 1].toInt() and 0xFF) shl 16) or
            ((png[at + 2].toInt() and 0xFF) shl 8) or (png[at + 3].toInt() and 0xFF)
        val w = int(16)
        val h = int(20)
        return if (w > 0 && h > 0) w to h else null
    }

    /** Logo no canto direito do cabeçalho, com no máximo 56 px de altura. */
    private fun logoImage(company: CompanyInfo, column: Int, columnWidthChars: Double): SheetImage? {
        val png = company.logoPng ?: return null
        val (w, h) = pngSize(png) ?: return null
        val height = minOf(56, h)
        val width = minOf(150, (w.toDouble() * height / h).toInt().coerceAtLeast(1))
        val columnPx = (columnWidthChars * 7 + 5).toInt()
        return SheetImage(
            bytes = png,
            extension = "png",
            column = column,
            row = 0,
            widthPx = width,
            heightPx = height,
            offsetXPx = (columnPx - width - 4).coerceAtLeast(0),
            offsetYPx = 4
        )
    }

    fun sortByName(products: List<Product>): List<Product> {
        val collator = Collator.getInstance(BR).apply { strength = Collator.PRIMARY }
        return products.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Por categoria (sem categoria por último) e depois por nome. */
    fun sortByCategoryAndName(products: List<Product>): List<Product> {
        val collator = Collator.getInstance(BR).apply { strength = Collator.PRIMARY }
        return products.sortedWith { a, b ->
            val ca = a.category.trim()
            val cb = b.category.trim()
            when {
                ca.isEmpty() && cb.isNotEmpty() -> 1
                ca.isNotEmpty() && cb.isEmpty() -> -1
                else -> collator.compare(ca, cb).takeIf { it != 0 } ?: collator.compare(a.name, b.name)
            }
        }
    }

    /** Categorias usadas nos produtos, em ordem alfabética. */
    fun categories(products: List<Product>): List<String> {
        val collator = Collator.getInstance(BR).apply { strength = Collator.PRIMARY }
        return products.map { it.category.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(BR) }
            .sortedWith { a, b -> collator.compare(a, b) }
    }

    private fun dateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR).format(Date(millis))

    private fun shortDateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", BR).format(Date(millis))

    /** Número inteiro (#,##0) ou com casas decimais (#,##0.00) conforme a unidade/valor. */
    private fun numberStyle(p: Product, alt: Boolean): CellStyle {
        val decimal = p.unit.allowsDecimals || p.quantity != Math.rint(p.quantity) ||
            p.entries.any { it.amount != Math.rint(it.amount) }
        return when {
            decimal && alt -> CellStyle.NUMBER_DEC_ALT
            decimal -> CellStyle.NUMBER_DEC
            alt -> CellStyle.NUMBER_ALT
            else -> CellStyle.NUMBER
        }
    }

    private fun countSheet(
        products: List<Product>,
        countStartedAt: Long,
        generatedAt: Long,
        company: CompanyInfo
    ): Sheet {
        val hasCategories = products.any { it.category.isNotBlank() }
        val hasBarcodes = products.any { it.barcode.isNotBlank() }
        val ordered = if (hasCategories) sortByCategoryAndName(products) else products
        // Colunas: #, Produto, Quantidade, Unidade, [Categoria], [Código], Última contagem
        val extraHeaders = (if (hasCategories) listOf("Categoria") else emptyList()) +
            (if (hasBarcodes) listOf("Código") else emptyList())
        val extraWidths = (if (hasCategories) listOf(20.0) else emptyList()) +
            (if (hasBarcodes) listOf(17.0) else emptyList())
        val lastColIndex = 4 + extraHeaders.size
        val lastCol = XlsxWriter.columnName(lastColIndex)
        val total = Qty.round(products.sumOf { it.quantity })
        val anyDecimal = products.any { it.unit.allowsDecimals || it.quantity != Math.rint(it.quantity) }
        val counted = products.count { it.wasCounted }
        val rows = mutableListOf<List<Cell?>>()

        val companyName = company.name.trim()
        rows += listOf(Cell.Text(companyName.ifEmpty { "Contagem de Estoque" }, CellStyle.TITLE))
        if (company.document.isNotBlank()) {
            rows += listOf(Cell.Text("CNPJ/CPF: ${company.document.trim()}", CellStyle.SUBTITLE))
        }
        rows += listOf(
            Cell.Text(
                (if (companyName.isNotEmpty()) "Contagem de estoque • " else "") +
                    "Data da contagem: ${dateTime(generatedAt)}",
                CellStyle.INFO
            )
        )
        rows += listOf(Cell.Text("Contagem iniciada em: ${dateTime(countStartedAt)}", CellStyle.SUBTITLE))
        rows += listOf(
            Cell.Text(
                "Produtos: ${products.size}   •   Contados: $counted   •   Quantidade total: ${Qty.format(total)}",
                CellStyle.SUBTITLE
            )
        )
        val infoRows = rows.size
        rows += emptyList<Cell?>()
        rows += (listOf("#", "Produto", "Quantidade", "Unidade") + extraHeaders + listOf("Última contagem"))
            .map { Cell.Text(it, CellStyle.HEADER) }
        val headerRow = rows.size // número da linha do cabeçalho da tabela

        val firstDataRow = rows.size + 1 // número da linha no Excel (começa em 1)
        ordered.forEachIndexed { i, p ->
            val alt = i % 2 == 1
            rows += listOf(
                Cell.Text("${i + 1}", if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER),
                Cell.Text(p.name, if (alt) CellStyle.TEXT_ALT else CellStyle.TEXT),
                Cell.Number(p.quantity, numberStyle(p, alt)),
                Cell.Text(p.unit.code, if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER)
            ) + (if (hasCategories) listOf(Cell.Text(p.category.trim(), if (alt) CellStyle.TEXT_ALT else CellStyle.TEXT)) else emptyList()) +
                (if (hasBarcodes) listOf(Cell.Text(p.barcode.trim(), if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER)) else emptyList()) +
                listOf(
                p.lastCountAt?.let { Cell.Text(shortDateTime(it), if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER) }
                    ?: Cell.Text("Não contado", if (alt) CellStyle.WARN_ALT else CellStyle.WARN)
            )
        }
        val lastDataRow = rows.size

        rows += listOf(
            Cell.Blank(CellStyle.TOTAL_LABEL),
            Cell.Text("TOTAL", CellStyle.TOTAL_LABEL),
            if (products.isEmpty()) Cell.Number(0, CellStyle.TOTAL_NUMBER)
            else Cell.Formula(
                "SUM(C$firstDataRow:C$lastDataRow)",
                total,
                if (anyDecimal) CellStyle.TOTAL_NUMBER_DEC else CellStyle.TOTAL_NUMBER
            ),
            Cell.Blank(CellStyle.TOTAL_LABEL),
            Cell.Blank(CellStyle.TOTAL_LABEL)
        ) + extraHeaders.map { Cell.Blank(CellStyle.TOTAL_LABEL) }

        return Sheet(
            name = "Contagem",
            columnWidths = listOf(6.0, 40.0, 14.0, 10.0) + extraWidths + listOf(22.0),
            rows = rows,
            merges = (1..infoRows).map { "A$it:$lastCol$it" },
            rowHeights = mapOf(0 to 32.0, headerRow - 1 to 24.0),
            frozenRows = headerRow,
            image = logoImage(company, column = lastColIndex, columnWidthChars = 22.0),
            autoFilter = if (products.isEmpty()) null else "A$headerRow:$lastCol$lastDataRow"
        )
    }

    private fun historySheet(products: List<Product>, generatedAt: Long, company: CompanyInfo): Sheet {
        data class Line(val product: Product, val entry: com.estoque.contagem.data.CountEntry) {
            val name: String get() = product.name
        }

        val lines = products
            .flatMap { p -> p.entries.map { Line(p, it) } }
            .sortedBy { it.entry.timestamp }

        val rows = mutableListOf<List<Cell?>>()
        rows += listOf(Cell.Text("Histórico de lançamentos", CellStyle.TITLE))
        val companyPrefix = company.name.trim().let { if (it.isEmpty()) "" else "$it • " }
        rows += listOf(Cell.Text("${companyPrefix}Gerado em: ${dateTime(generatedAt)}", CellStyle.SUBTITLE))
        rows += emptyList<Cell?>()
        rows += listOf("Data e hora", "Produto", "Operação", "Quantidade", "Total após", "Unidade")
            .map { Cell.Text(it, CellStyle.HEADER) }

        if (lines.isEmpty()) {
            rows += listOf(
                Cell.Text("Nenhum lançamento nesta contagem", CellStyle.TEXT),
                Cell.Blank(CellStyle.TEXT),
                Cell.Blank(CellStyle.TEXT),
                Cell.Blank(CellStyle.TEXT),
                Cell.Blank(CellStyle.TEXT),
                Cell.Blank(CellStyle.TEXT)
            )
        }
        lines.forEachIndexed { i, line ->
            val alt = i % 2 == 1
            val e = line.entry
            rows += listOf(
                Cell.Text(shortDateTime(e.timestamp), if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER),
                Cell.Text(line.name, if (alt) CellStyle.TEXT_ALT else CellStyle.TEXT),
                if (e.isCorrection) Cell.Text("Corrigido", if (alt) CellStyle.WARN_ALT else CellStyle.WARN)
                else Cell.Text("Somado", if (alt) CellStyle.GOOD_ALT else CellStyle.GOOD),
                Cell.Number(e.amount, numberStyle(line.product, alt)),
                Cell.Number(e.totalAfter, numberStyle(line.product, alt)),
                Cell.Text(line.product.unit.code, if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER)
            )
        }

        return Sheet(
            name = "Histórico",
            columnWidths = listOf(20.0, 40.0, 14.0, 14.0, 14.0, 10.0),
            rows = rows,
            merges = listOf("A1:F1", "A2:F2"),
            rowHeights = mapOf(0 to 32.0, 3 to 24.0),
            frozenRows = 4,
            image = logoImage(company, column = 5, columnWidthChars = 10.0)
        )
    }
}
