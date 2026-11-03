package com.estoque.contagem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodesTest {

    private fun p(name: String, code: String) = Product(name, name, "📦", 0, 0.0, emptyList(), 0L, barcode = code)

    @Test
    fun cleansScannedOrTypedCodes() {
        assertEquals("7891234567890", Barcodes.clean(" 789 1234 567890\n"))
        assertEquals("", Barcodes.clean("   "))
    }

    @Test
    fun convertsNumbersSavedByExcel() {
        assertEquals("7891234567890", Barcodes.fromSpreadsheet("7891234567890"))
        assertEquals("7891234567890", Barcodes.fromSpreadsheet("7891234567890.0"))
        assertEquals("7891234567890", Barcodes.fromSpreadsheet("7.89123456789E12"))
        assertEquals("ABC-123", Barcodes.fromSpreadsheet("ABC-123"))
        assertEquals("", Barcodes.fromSpreadsheet(null))
    }

    @Test
    fun findsProductByCode() {
        val list = listOf(p("Coca", "7894900011517"), p("Arroz", ""), p("Feijão", "123"))
        assertEquals("Coca", Barcodes.find(list, "7894900011517")?.name)
        assertEquals("Feijão", Barcodes.find(list, " 123 ")?.name)
        assertNull(Barcodes.find(list, "999"))
        assertNull(Barcodes.find(list, "")) // produto sem código não casa com vazio
    }
}
