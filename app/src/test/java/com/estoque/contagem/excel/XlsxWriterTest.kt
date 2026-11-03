package com.estoque.contagem.excel

import com.estoque.contagem.data.CountEntry
import com.estoque.contagem.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun product(name: String, vararg amounts: Long): Product {
        var total = 0L
        val entries = amounts.mapIndexed { i, a ->
            total += a
            CountEntry(a, total, 1_700_000_000_000L + i * 60_000L, isCorrection = false)
        }
        return Product(name, name, "📦", 0, total, entries, 1_700_000_000_000L)
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return files
    }

    @Test
    fun excludedProductsDoNotGoToTheSpreadsheet() {
        val products = listOf(
            product("Arroz", 10),
            product("Coca", 5).copy(excludeFromExcel = true),
            product("Feijão", 3)
        )
        val out = ByteArrayOutputStream()
        XlsxWriter.write(StockReport.build(products, 1_700_000_000_000L, 1_700_000_500_000L), out)
        val files = unzip(out.toByteArray())
        val count = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(count.contains("Arroz"))
        assertTrue(count.contains("Feijão"))
        assertTrue(!count.contains("Coca"))
        assertTrue(count.contains("<f>SUM(C7:C8)</f><v>13</v>"))
        assertTrue(!files.getValue("xl/worksheets/sheet2.xml").contains("Coca"))
    }

    private fun tinyPng(width: Int, height: Int): ByteArray {
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Test
    fun companyHeaderAndLogo() {
        val logo = tinyPng(200, 100)
        assertEquals(200 to 100, StockReport.pngSize(logo))

        val sheets = StockReport.build(
            listOf(product("Arroz", 10), product("Feijão", 3)),
            1_700_000_000_000L, 1_700_000_500_000L,
            CompanyInfo(name = "Mercadinho Bom Preço", document = "12.345.678/0001-90", logoPng = logo)
        )
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        val files = unzip(out.toByteArray())

        val count = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(count.contains("Mercadinho Bom Preço"))
        assertTrue(count.contains("CNPJ/CPF: 12.345.678/0001-90"))
        // Linha extra do CNPJ: cabeçalho na linha 7, dados a partir da 8
        assertTrue(count.contains("<f>SUM(C8:C9)</f><v>13</v>"))
        assertTrue(count.contains("ySplit=\"7\""))
        assertTrue(count.contains("<drawing r:id=\"rId1\"/>"))

        assertTrue(files.containsKey("xl/media/image1.png"))
        assertTrue(files.containsKey("xl/drawings/drawing1.xml"))
        assertTrue(files.containsKey("xl/worksheets/_rels/sheet1.xml.rels"))
        // Logo reduzido para 56 px de altura mantendo a proporção (112 x 56)
        assertTrue(files.getValue("xl/drawings/drawing1.xml").contains("cx=\"${112 * 9525}\" cy=\"${56 * 9525}\""))
        assertTrue(files.getValue("[Content_Types].xml").contains("Extension=\"png\""))
    }

    @Test
    fun columnNames() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
    }

    @Test
    fun escapesSpecialCharacters() {
        assertEquals("A &amp; B &lt;1&gt; &quot;x&quot;", XlsxWriter.escape("A & B <1> \"x\""))
        assertEquals("ok", XlsxWriter.escape("o\u0001k"))
    }

    @Test
    fun generatesWorkbookWithCountAndHistory() {
        val products = listOf(product("Sabão & Cia", 5, 7), product("Arroz", 10), product("Feijão"))
        val sheets = StockReport.build(products, 1_700_000_000_000L, 1_700_000_500_000L)
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        val files = unzip(out.toByteArray())

        assertTrue(files.containsKey("[Content_Types].xml"))
        assertTrue(files.containsKey("xl/workbook.xml"))
        assertTrue(files.containsKey("xl/styles.xml"))

        val count = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(count.contains("Sabão &amp; Cia"))
        assertTrue(count.contains("Não contado"))
        assertTrue(count.contains("<f>SUM(C7:C9)</f><v>22</v>"))
        // Ordem alfabética: Arroz, Feijão, Sabão
        assertTrue(count.indexOf("Arroz") < count.indexOf("Feijão"))
        assertTrue(count.indexOf("Feijão") < count.indexOf("Sabão"))

        val history = files.getValue("xl/worksheets/sheet2.xml")
        assertEquals(3, Regex("Somado").findAll(history).count())
    }
}
