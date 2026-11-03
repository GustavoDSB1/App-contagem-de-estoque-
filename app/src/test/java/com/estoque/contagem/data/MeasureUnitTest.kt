package com.estoque.contagem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasureUnitTest {

    @Test
    fun readsUnitFromTextOrCode() {
        assertEquals(MeasureUnit.KG, MeasureUnit.fromText("kg"))
        assertEquals(MeasureUnit.KG, MeasureUnit.fromText("Quilos"))
        assertEquals(MeasureUnit.L, MeasureUnit.fromText("litros"))
        assertEquals(MeasureUnit.L, MeasureUnit.fromText("L"))
        assertEquals(MeasureUnit.CX, MeasureUnit.fromText("Caixa"))
        assertEquals(MeasureUnit.DZ, MeasureUnit.fromText("dúzias"))
        assertEquals(MeasureUnit.UN, MeasureUnit.fromText(""))
        assertEquals(MeasureUnit.UN, MeasureUnit.fromText(null))
        assertEquals(MeasureUnit.UN, MeasureUnit.fromText("xyz"))
    }

    @Test
    fun formatsAndParsesQuantities() {
        assertEquals("12", Qty.format(12.0))
        assertEquals("2,5", Qty.format(2.5))
        assertEquals("1.234,75", Qty.format(1234.75))
        assertEquals("0,3", Qty.format(0.1 + 0.2)) // sem 0,30000000000000004
        assertEquals(2.5, Qty.parse("2,5")!!, 0.0)
        assertEquals(2.5, Qty.parse("2.5")!!, 0.0)
        assertEquals(1234.5, Qty.parse("1.234,5")!!, 0.0)
        assertEquals(0.333, Qty.parse("0,3333")!!, 0.0)
        assertNull(Qty.parse(""))
        assertNull(Qty.parse("abc"))
        assertNull(Qty.parse("-3"))
        assertEquals("1234,5", Qty.toInput(1234.5))
    }

    @Test
    fun cleansTypedInput() {
        // Unidade inteira: só números
        assertEquals("12", Qty.cleanInput("1,2", allowsDecimals = false))
        // kg: uma vírgula e até 3 casas
        assertEquals("1,255", Qty.cleanInput("1,2555", allowsDecimals = true))
        assertEquals("1,5", Qty.cleanInput("1.5", allowsDecimals = true))
        assertEquals("1,5", Qty.cleanInput("1,5,3", allowsDecimals = true).take(3))
        assertEquals("0,5", Qty.cleanInput(",5", allowsDecimals = true))
    }

    @Test
    fun unitLabel() {
        assertEquals("unidade", MeasureUnit.UN.label(1.0))
        assertEquals("unidades", MeasureUnit.UN.label(2.0))
        assertEquals("litros", MeasureUnit.L.label(1.5))
    }
}
