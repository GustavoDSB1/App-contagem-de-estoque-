package com.estoque.contagem.excel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.FileProvider
import com.estoque.contagem.consumo.Money
import com.estoque.contagem.data.Product
import com.estoque.contagem.data.Qty
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Relatório da contagem em PDF (A4), pronto para imprimir ou enviar. */
object PdfExporter {

    const val MIME = "application/pdf"

    private val BR: Locale = Locale.forLanguageTag("pt-BR")
    private const val PAGE_W = 595 // A4 em pontos
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val ROW_H = 20f
    private const val FOOTER_H = 28f

    private val INDIGO = Color.rgb(79, 70, 229)
    private val INDIGO_LIGHT = Color.rgb(224, 231, 255)
    private val ZEBRA = Color.rgb(245, 247, 255)
    private val TEXT = Color.rgb(17, 24, 39)
    private val GRAY = Color.rgb(107, 114, 128)
    private val ORANGE = Color.rgb(217, 119, 6)

    private class Col(
        val title: String,
        val weight: Float,
        val align: Paint.Align,
        val value: (Product) -> String
    )

    /** Linha do relatório: título de categoria ou produto. */
    private sealed class Line {
        class Category(val name: String) : Line()
        class Item(val product: Product, val index: Int) : Line()
    }

    fun export(
        context: Context,
        products: List<Product>,
        countStartedAt: Long,
        company: CompanyInfo,
        withValues: Boolean,
        now: Long = System.currentTimeMillis()
    ): ExportResult {
        val items = StockReport.reportProducts(products)
        val hasCategories = items.any { it.category.isNotBlank() }
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", BR)

        val cols = buildList {
            add(Col("Produto", 3.2f, Paint.Align.LEFT) { it.name })
            add(Col("Qtd", 1.1f, Paint.Align.RIGHT) { Qty.format(it.quantity) })
            add(Col("Un", 0.7f, Paint.Align.CENTER) { it.unit.code })
            if (withValues) {
                add(Col("Valor unit.", 1.4f, Paint.Align.RIGHT) { Money.formatWithSymbol(it.priceCents) })
                add(Col("Valor total", 1.5f, Paint.Align.RIGHT) { Money.formatWithSymbol(StockReport.valueCents(it)) })
            }
            add(Col("Última contagem", 1.7f, Paint.Align.CENTER) { p ->
                p.lastCountAt?.let { dateFmt.format(Date(it)) } ?: "Não contado"
            })
        }

        // Monta as linhas (com títulos de categoria) antes de paginar
        val lines = mutableListOf<Line>()
        var lastCategory: String? = null
        items.forEachIndexed { i, p ->
            val cat = p.category.trim()
            if (hasCategories && cat != lastCategory) {
                lines += Line.Category(cat.ifEmpty { "Sem categoria" })
                lastCategory = cat
            }
            lines += Line.Item(p, i)
        }

        val logo: Bitmap? = company.logoPng?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        val title = company.name.trim().ifEmpty { "Contagem de Estoque" }
        val infoLines = buildList {
            if (company.document.isNotBlank()) add("CNPJ/CPF: ${company.document.trim()}")
            add("Contagem de estoque • ${SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", BR).format(Date(now))}")
            add("Iniciada em ${SimpleDateFormat("dd/MM/yyyy", BR).format(Date(countStartedAt))}" +
                "  •  ${items.size} produto(s)  •  ${items.count { it.wasCounted }} contado(s)")
            if (withValues) add("Valor total do estoque: ${Money.formatWithSymbol(StockReport.totalValueCents(items))}")
        }

        // Paginação: a 1ª página tem o cabeçalho completo
        val firstHeaderH = 24f + 16f * infoLines.size + 18f
        val otherHeaderH = 34f
        val tableHeaderH = ROW_H + 4f
        val totalRowH = ROW_H + 6f
        val pages = mutableListOf<List<Line>>()
        var current = mutableListOf<Line>()
        var available = PAGE_H - MARGIN * 2 - FOOTER_H - firstHeaderH - tableHeaderH
        for (line in lines) {
            if (available < ROW_H) {
                pages += current
                current = mutableListOf()
                available = PAGE_H - MARGIN * 2 - FOOTER_H - otherHeaderH - tableHeaderH
            }
            current += line
            available -= ROW_H
        }
        if (available < totalRowH && current.isNotEmpty()) {
            pages += current
            current = mutableListOf()
        }
        pages += current

        val doc = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT; textSize = 9.5f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint().apply { style = Paint.Style.FILL }
        val contentW = PAGE_W - MARGIN * 2
        val unit = contentW / cols.sumOf { it.weight.toDouble() }.toFloat()

        fun drawCell(canvas: Canvas, value: String, x: Float, w: Float, baseline: Float, align: Paint.Align, paint: Paint) {
            val pad = 4f
            val shown = ellipsize(value, paint, w - pad * 2)
            val px = when (align) {
                Paint.Align.LEFT -> x + pad
                Paint.Align.RIGHT -> x + w - pad - paint.measureText(shown)
                else -> x + (w - paint.measureText(shown)) / 2
            }
            canvas.drawText(shown, px, baseline, paint)
        }

        pages.forEachIndexed { pageIndex, pageLines ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
            val c = page.canvas
            var y = MARGIN

            if (pageIndex == 0) {
                var textX = MARGIN
                if (logo != null && logo.height > 0) {
                    // Até 48 pt de altura e 120 pt de largura, mantendo a proporção
                    val aspect = logo.width.toFloat() / logo.height
                    var h = 48f
                    var w = aspect * h
                    if (w > 120f) {
                        w = 120f
                        h = w / aspect
                    }
                    c.drawBitmap(logo, null, RectF(MARGIN, y, MARGIN + w, y + h), null)
                    textX = MARGIN + w + 12f
                }
                c.drawText(title, textX, y + 18f, Paint(bold).apply { textSize = 18f; color = INDIGO })
                var infoY = y + 18f
                infoLines.forEachIndexed { i, l ->
                    infoY += 16f
                    c.drawText(l, textX, infoY, Paint(text).apply {
                        textSize = 10f
                        color = if (i == infoLines.lastIndex && withValues) TEXT else GRAY
                        typeface = if (i == infoLines.lastIndex && withValues) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    })
                }
                y += maxOf(firstHeaderH, if (logo != null) 60f else 0f)
            } else {
                c.drawText("$title — contagem de estoque (continuação)", MARGIN, y + 14f, Paint(bold).apply { textSize = 11f; color = INDIGO })
                y += otherHeaderH
            }

            // Cabeçalho da tabela
            fill.color = INDIGO
            c.drawRoundRect(RectF(MARGIN, y, MARGIN + contentW, y + ROW_H), 4f, 4f, fill)
            val headerPaint = Paint(bold).apply { color = Color.WHITE }
            var x = MARGIN
            cols.forEach { col ->
                val w = col.weight * unit
                drawCell(c, col.title, x, w, y + 13.5f, col.align, headerPaint)
                x += w
            }
            y += tableHeaderH

            pageLines.forEach { line ->
                when (line) {
                    is Line.Category -> {
                        fill.color = INDIGO_LIGHT
                        c.drawRect(MARGIN, y + 2f, MARGIN + contentW, y + ROW_H - 1f, fill)
                        c.drawText(line.name.uppercase(BR), MARGIN + 6f, y + 14f, Paint(bold).apply { color = INDIGO; textSize = 9f })
                    }
                    is Line.Item -> {
                        if (line.index % 2 == 1) {
                            fill.color = ZEBRA
                            c.drawRect(MARGIN, y, MARGIN + contentW, y + ROW_H, fill)
                        }
                        x = MARGIN
                        cols.forEachIndexed { ci, col ->
                            val w = col.weight * unit
                            val value = col.value(line.product)
                            val paint = when {
                                ci == 0 || ci == 1 -> bold
                                value == "Não contado" -> Paint(bold).apply { color = ORANGE }
                                else -> text
                            }
                            drawCell(c, value, x, w, y + 13.5f, col.align, paint)
                            x += w
                        }
                    }
                }
                y += ROW_H
            }

            // Total na última página
            if (pageIndex == pages.lastIndex) {
                fill.color = INDIGO_LIGHT
                c.drawRect(MARGIN, y + 2f, MARGIN + contentW, y + 2f + ROW_H, fill)
                x = MARGIN
                cols.forEachIndexed { ci, col ->
                    val w = col.weight * unit
                    val value = when {
                        ci == 0 -> "TOTAL"
                        ci == 1 -> Qty.format(Qty.round(items.sumOf { it.quantity }))
                        col.title == "Valor total" -> Money.formatWithSymbol(StockReport.totalValueCents(items))
                        else -> ""
                    }
                    if (value.isNotEmpty()) drawCell(c, value, x, w, y + 15.5f, col.align, bold)
                    x += w
                }
            }

            // Rodapé
            val footer = Paint(text).apply { textSize = 8.5f; color = GRAY }
            val pageText = "Página ${pageIndex + 1} de ${pages.size}"
            c.drawText("Gerado pelo app Contagem de Estoque", MARGIN, PAGE_H - MARGIN + 8f, footer)
            c.drawText(pageText, PAGE_W - MARGIN - footer.measureText(pageText), PAGE_H - MARGIN + 8f, footer)
            doc.finishPage(page)
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(now))
        val fileName = "${ExcelExporter.baseFileName(company, stamp)}.pdf"
        val dir = File(context.cacheDir, "planilhas").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, fileName)
        try {
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val saved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            runCatching { ExcelExporter.saveToDownloads(context, file, fileName, MIME) }.isSuccess
        return ExportResult(fileName, uri, saved, items.size, MIME)
    }

    /** Corta o texto com "…" para caber na largura. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ell = "…"
        val n = paint.breakText(text, true, maxWidth - paint.measureText(ell), null)
        return text.take(n).trimEnd() + ell
    }
}
