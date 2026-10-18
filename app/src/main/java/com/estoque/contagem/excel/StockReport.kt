package com.estoque.contagem.excel

import com.estoque.contagem.data.Product
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Monta as abas da planilha de contagem a partir dos produtos. */
object StockReport {

    private val BR: Locale = Locale.forLanguageTag("pt-BR")

    /** Produtos da lista de exclusão ("fora da planilha") não entram em nenhuma aba. */
    fun build(products: List<Product>, countStartedAt: Long, generatedAt: Long): List<Sheet> {
        val sorted = sortByName(products.filterNot { it.excludeFromExcel })
        return listOf(countSheet(sorted, countStartedAt, generatedAt), historySheet(sorted, generatedAt))
    }

    fun sortByName(products: List<Product>): List<Product> {
        val collator = Collator.getInstance(BR).apply { strength = Collator.PRIMARY }
        return products.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private fun dateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR).format(Date(millis))

    private fun shortDateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", BR).format(Date(millis))

    private fun countSheet(products: List<Product>, countStartedAt: Long, generatedAt: Long): Sheet {
        val total = products.sumOf { it.quantity }
        val counted = products.count { it.wasCounted }
        val rows = mutableListOf<List<Cell?>>()

        rows += listOf(Cell.Text("Contagem de Estoque", CellStyle.TITLE))
        rows += listOf(Cell.Text("Data da contagem: ${dateTime(generatedAt)}", CellStyle.INFO))
        rows += listOf(Cell.Text("Contagem iniciada em: ${dateTime(countStartedAt)}", CellStyle.SUBTITLE))
        rows += listOf(
            Cell.Text(
                "Produtos: ${products.size}   •   Contados: $counted   •   Itens no total: $total",
                CellStyle.SUBTITLE
            )
        )
        rows += emptyList<Cell?>()
        rows += listOf("#", "Produto", "Quantidade", "Última contagem").map { Cell.Text(it, CellStyle.HEADER) }

        val firstDataRow = rows.size + 1 // número da linha no Excel (começa em 1)
        products.forEachIndexed { i, p ->
            val alt = i % 2 == 1
            rows += listOf(
                Cell.Text("${i + 1}", if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER),
                Cell.Text(p.name, if (alt) CellStyle.TEXT_ALT else CellStyle.TEXT),
                Cell.Number(p.quantity, if (alt) CellStyle.NUMBER_ALT else CellStyle.NUMBER),
                p.lastCountAt?.let { Cell.Text(shortDateTime(it), if (alt) CellStyle.CENTER_ALT else CellStyle.CENTER) }
                    ?: Cell.Text("Não contado", if (alt) CellStyle.WARN_ALT else CellStyle.WARN)
            )
        }
        val lastDataRow = rows.size

        rows += listOf(
            Cell.Blank(CellStyle.TOTAL_LABEL),
            Cell.Text("TOTAL", CellStyle.TOTAL_LABEL),
            if (products.isEmpty()) Cell.Number(0, CellStyle.TOTAL_NUMBER)
            else Cell.Formula("SUM(C$firstDataRow:C$lastDataRow)", total),
            Cell.Blank(CellStyle.TOTAL_LABEL)
        )

        return Sheet(
            name = "Contagem",
            columnWidths = listOf(6.0, 40.0, 16.0, 22.0),
            rows = rows,
            merges = listOf("A1:D1", "A2:D2", "A3:D3", "A4:D4"),
            rowHeights = mapOf(0 to 32.0, 5 to 24.0),
            frozenRows = 6
        )
    }

    private fun historySheet(products: List<Product>, generatedAt: Long): Sheet {
        data class Line(val name: String, val entry: com.estoque.contagem.data.CountEntry)

        val lines = products
            .flatMap { p -> p.entries.map { Line(p.name, it) } }
            .sortedBy { it.entry.timestamp }

        val rows = mutableListOf<List<Cell?>>()
        rows += listOf(Cell.Text("Histórico de lançamentos", CellStyle.TITLE))
        rows += listOf(Cell.Text("Gerado em: ${dateTime(generatedAt)}", CellStyle.SUBTITLE))
        rows += emptyList<Cell?>()
        rows += listOf("Data e hora", "Produto", "Operação", "Quantidade", "Total após")
            .map { Cell.Text(it, CellStyle.HEADER) }

        if (lines.isEmpty()) {
            rows += listOf(
                Cell.Text("Nenhum lançamento nesta contagem", CellStyle.TEXT),
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
                Cell.Number(e.amount, if (alt) CellStyle.NUMBER_ALT else CellStyle.NUMBER),
                Cell.Number(e.totalAfter, if (alt) CellStyle.NUMBER_ALT else CellStyle.NUMBER)
            )
        }

        return Sheet(
            name = "Histórico",
            columnWidths = listOf(20.0, 40.0, 14.0, 14.0, 14.0),
            rows = rows,
            merges = listOf("A1:E1", "A2:E2"),
            rowHeights = mapOf(0 to 32.0, 3 to 24.0),
            frozenRows = 4
        )
    }
}
