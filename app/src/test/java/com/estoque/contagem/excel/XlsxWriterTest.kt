package com.estoque.contagem.excel

import com.estoque.contagem.data.CountEntry
import com.estoque.contagem.data.MeasureUnit
import com.estoque.contagem.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun product(name: String, vararg amounts: Long): Product =
        productDec(name, *amounts.map { it.toDouble() }.toDoubleArray())

    private fun productDec(name: String, vararg amounts: Double, unit: MeasureUnit = MeasureUnit.UN): Product {
        var total = 0.0
        val entries = amounts.mapIndexed { i, a ->
            total += a
            CountEntry(a, total, 1_700_000_000_000L + i * 60_000L, isCorrection = false)
        }
        return Product(name, name, "📦", 0, total, entries, 1_700_000_000_000L, unit = unit)
    }

    @Test
    fun decimalQuantitiesAndUnitColumn() {
        val sheets = StockReport.build(
            listOf(productDec("Queijo", 1.25, 0.5, unit = MeasureUnit.KG), product("Arroz", 10)),
            1_700_000_000_000L, 1_700_000_500_000L
        )
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        val files = unzip(out.toByteArray())
        val count = files.getValue("xl/worksheets/sheet1.xml")
        val decStyle = CellStyle.NUMBER_DEC.ordinal
        val decAltStyle = CellStyle.NUMBER_DEC_ALT.ordinal
        // Queijo (2ª linha, alternada) com 1,75 kg em formato decimal; Arroz inteiro
        assertTrue(count.contains("s=\"$decAltStyle\"><v>1.75</v>") || count.contains("s=\"$decStyle\"><v>1.75</v>"))
        assertTrue(count.contains("<v>10</v>"))
        assertTrue(count.contains(">kg<"))
        assertTrue(count.contains(">Unidade<"))
        // Total soma decimais e usa formato decimal
        assertTrue(count.contains("<f>SUM(C7:C8)</f><v>11.75</v>"))
        // Estilo decimal usa o formato #,##0.00 (numFmtId 4)
        assertTrue(files.getValue("xl/styles.xml").contains("numFmtId=\"4\""))
        assertTrue(files.getValue("xl/worksheets/sheet2.xml").contains("<v>0.5</v>"))
    }

    @Test
    fun categoriesGroupAndFilter() {
        val products = listOf(
            product("Sabão", 2).copy(category = "Limpeza"),
            product("Coca", 5).copy(category = "Bebidas"),
            product("Arroz", 10),
            product("Água", 3).copy(category = "Bebidas")
        )
        assertEquals(listOf("Bebidas", "Limpeza"), StockReport.categories(products))
        // Por categoria (sem categoria por último) e depois por nome
        assertEquals(
            listOf("Água", "Coca", "Sabão", "Arroz"),
            StockReport.sortByCategoryAndName(products).map { it.name }
        )

        val out = ByteArrayOutputStream()
        XlsxWriter.write(StockReport.build(products, 1_700_000_000_000L, 1_700_000_500_000L), out)
        val count = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(count.contains(">Categoria<"))
        assertTrue(count.indexOf("Água") < count.indexOf("Coca"))
        assertTrue(count.indexOf("Coca") < count.indexOf("Sabão"))
        assertTrue(count.indexOf("Sabão") < count.indexOf("Arroz"))
        // Filtros do Excel no cabeçalho (6 colunas: A até F), linhas 6 a 10
        assertTrue(count.contains("<autoFilter ref=\"A6:F10\"/>"))
        assertTrue(count.contains("<f>SUM(C7:C10)</f><v>20</v>"))
    }

    @Test
    fun barcodeColumnWhenAnyProductHasCode() {
        val products = listOf(product("Coca", 5).copy(barcode = "7894900011517"), product("Arroz", 1))
        val out = ByteArrayOutputStream()
        XlsxWriter.write(StockReport.build(products, 0L, 0L), out)
        val count = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(count.contains(">Código<"))
        // Código como texto (não vira número/notação científica no Excel)
        assertTrue(count.contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">7894900011517</t>"))
        assertTrue(count.contains("<autoFilter ref=\"A6:F8\"/>"))
    }

    @Test
    fun noCategoryColumnWhenNoneIsUsed() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(StockReport.build(listOf(product("Arroz", 1)), 0L, 0L), out)
        val count = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(!count.contains(">Categoria<"))
        assertTrue(count.contains("<autoFilter ref=\"A6:E7\"/>"))
    }

    @Test
    fun numberTextHasNoScientificNotation() {
        assertEquals("12", XlsxWriter.numberText(12.0))
        assertEquals("2.5", XlsxWriter.numberText(2.5))
        assertEquals("0.001", XlsxWriter.numberText(0.001))
        assertEquals("9999999", XlsxWriter.numberText(9_999_999.0))
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

    /** PNG mínimo válido (RGBA transparente), montado só com java.util.zip (sem java.awt, que não existe no Android). */
    private fun tinyPng(width: Int, height: Int): ByteArray {
        fun chunk(out: java.io.DataOutputStream, type: String, data: ByteArray) {
            val crc = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }
            out.writeInt(data.size)
            out.write(type.toByteArray())
            out.write(data)
            out.writeInt(crc.value.toInt())
        }
        val header = java.io.ByteArrayOutputStream().apply {
            java.io.DataOutputStream(this).apply {
                writeInt(width); writeInt(height)
                write(byteArrayOf(8, 6, 0, 0, 0)) // 8 bits, RGBA
            }
        }.toByteArray()
        val raw = ByteArray(height * (1 + width * 4)) // linhas com filtro 0 e pixels transparentes
        val deflater = java.util.zip.Deflater().apply { setInput(raw); finish() }
        val compressed = java.io.ByteArrayOutputStream().apply {
            val buf = ByteArray(4096)
            while (!deflater.finished()) write(buf, 0, deflater.deflate(buf))
        }.toByteArray()
        val out = ByteArrayOutputStream()
        java.io.DataOutputStream(out).apply {
            write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
            chunk(this, "IHDR", header)
            chunk(this, "IDAT", compressed)
            chunk(this, "IEND", ByteArray(0))
        }
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
