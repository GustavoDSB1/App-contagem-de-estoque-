package com.estoque.contagem.excel

import com.estoque.contagem.data.CountEntry
import com.estoque.contagem.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProductImportTest {

    private fun product(name: String, qty: Long, countedAt: Long?): Product {
        val entries = if (countedAt != null) listOf(CountEntry(qty, qty, countedAt, false)) else emptyList()
        return Product(name, name, "📦", 0, qty, entries, 0L)
    }

    @Test
    fun readsSpreadsheetGeneratedByTheApp() {
        val countedAt = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 2, 23, 25, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val original = listOf(
            product("Coca 600", 18, countedAt),
            product("Sabão em pó & amaciante", 15, countedAt),
            product("Óleo de soja", 0, null)
        )
        val out = ByteArrayOutputStream()
        XlsxWriter.write(StockReport.build(original, countedAt, countedAt), out)

        val sheets = XlsxReader.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("Contagem", "Histórico"), sheets.keys.toList())

        val imported = ProductImport.parse(sheets)
        assertEquals(listOf("Coca 600", "Óleo de soja", "Sabão em pó & amaciante"), imported.map { it.name })
        assertEquals(listOf(18L, 0L, 15L), imported.map { it.quantity })
        assertEquals(countedAt, imported[0].lastCountAt)
        assertNull(imported[1].lastCountAt) // "Não contado"
        assertEquals("🥤", imported[0].emoji)
        assertEquals("🧼", imported[2].emoji)
        assertTrue(imported.none { it.name == "TOTAL" })
    }

    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            parts.forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun readsExcelStyleFileWithSharedStrings() {
        val ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
        val rns = "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
        val bytes = zip(
            "xl/workbook.xml" to "<workbook $ns $rns><sheets><sheet name=\"Lista\" sheetId=\"1\" r:id=\"rId7\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId7\" Type=\"x\" Target=\"/xl/worksheets/minha.xml\"/></Relationships>",
            "xl/sharedStrings.xml" to "<sst $ns><si><t>Nome do produto</t></si><si><t>Qtd</t></si><si><t>Preço</t></si>" +
                "<si><r><t>Guaraná </t></r><r><t>lata</t></r></si><si><t>R$ 7,00</t></si><si><t>Pipoca</t></si></sst>",
            "xl/worksheets/minha.xml" to "<worksheet $ns><sheetData>" +
                "<row r=\"2\"><c r=\"B2\" t=\"s\"><v>0</v></c><c r=\"C2\" t=\"s\"><v>1</v></c><c r=\"D2\" t=\"s\"><v>2</v></c></row>" +
                "<row r=\"3\"><c r=\"B3\" t=\"s\"><v>3</v></c><c r=\"C3\"><v>4</v></c><c r=\"D3\" t=\"s\"><v>4</v></c></row>" +
                "<row r=\"4\"><c r=\"B4\" t=\"s\"><v>5</v></c><c r=\"D4\"><v>15</v></c></row>" +
                "<row r=\"5\"><c r=\"B5\" t=\"s\"><v>3</v></c><c r=\"C5\"><v>9</v></c></row>" +
                "</sheetData></worksheet>"
        )
        val imported = ProductImport.parse(XlsxReader.read(ByteArrayInputStream(bytes)))
        assertEquals(2, imported.size) // "Guaraná lata" repetido é ignorado
        assertEquals("Guaraná lata", imported[0].name)
        assertEquals(4L, imported[0].quantity)
        assertEquals(700L, imported[0].priceCents)
        assertEquals("Pipoca", imported[1].name)
        assertEquals(0L, imported[1].quantity)
        assertEquals(1500L, imported[1].priceCents)
    }

    @Test
    fun rejectsFilesThatAreNotXlsx() {
        try {
            XlsxReader.read(ByteArrayInputStream("nome;quantidade\narroz;3".toByteArray()))
            fail("deveria falhar")
        } catch (e: XlsxReader.NotXlsxException) {
            // ok
        }
    }

    @Test
    fun explainsMissingProductColumn() {
        try {
            ProductImport.parse(mapOf("A" to listOf(listOf("x", "y"), listOf("1", "2"))))
            fail("deveria falhar")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("Produto"))
        }
    }

    @Test
    fun parsesNumbersAndEmojis() {
        assertEquals(9.0, ProductImport.parseNumber("R$ 9,00")!!, 0.0)
        assertEquals(1234.5, ProductImport.parseNumber("1.234,50")!!, 0.0)
        assertEquals(12.0, ProductImport.parseNumber("12")!!, 0.0)
        assertNull(ProductImport.parseNumber("abc"))
        assertEquals("🧴", ProductImport.guessEmoji("Água sanitária 1L"))
        assertEquals("🥤", ProductImport.guessEmoji("Água mineral"))
        assertEquals("🍞", ProductImport.guessEmoji("Pão francês"))
        assertEquals("🧼", ProductImport.guessEmoji("Sabão em pó"))
        assertEquals("📦", ProductImport.guessEmoji("Macarronada pronta x"))
        assertEquals("🍪", ProductImport.guessEmoji("Biscoitos recheados"))
        assertEquals("🥤", ProductImport.guessEmoji("coca 600"))
    }
}
