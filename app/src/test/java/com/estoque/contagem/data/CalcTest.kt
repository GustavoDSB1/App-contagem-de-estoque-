package com.estoque.contagem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {

    private fun eval(s: String) = Calc.evaluate(s)

    @Test
    fun basicOperations() {
        assertEquals(84.0, eval("7×12")!!, 0.0)
        assertEquals(84.0, eval("7x12")!!, 0.0)
        assertEquals(84.0, eval("7*12")!!, 0.0)
        assertEquals(89.0, eval("7×12+5")!!, 0.0)
        assertEquals(15.0, eval("20-5")!!, 0.0)
        assertEquals(4.0, eval("12÷3")!!, 0.0)
        assertEquals(4.0, eval("12/3")!!, 0.0)
        assertEquals(42.0, eval("42")!!, 0.0)
    }

    @Test
    fun multiplicationBeforeSum() {
        // 3 caixas de 12 + 2 caixas de 6 + 5 soltas
        assertEquals(53.0, eval("3×12+2×6+5")!!, 0.0)
        assertEquals(2.0, eval("10-4×2")!!, 0.0)
    }

    @Test
    fun decimals() {
        assertEquals(7.5, eval("2,5×3")!!, 0.0)
        assertEquals(1.25, eval("0,75+0,5")!!, 0.0)
        assertEquals(0.333, eval("1÷3")!!, 0.0)
    }

    @Test
    fun partialAndInvalid() {
        assertEquals(7.0, eval("7×")!!, 0.0) // operador no fim: resultado parcial
        assertNull(eval(""))
        assertNull(eval("×5"))
        assertNull(eval("5÷0"))
        assertNull(eval("5××2"))
        assertEquals(-3.0, eval("2-5")!!, 0.0) // negativo: a tela não deixa salvar
    }

    @Test
    fun typingWithKeys() {
        var e = ""
        for (k in "7×12+5") e = Calc.press(e, k, allowsDecimals = false)
        assertEquals("7×12+5", e)
        assertEquals("7×12−", Calc.press("7×12+", Calc.MINUS, false)) // troca o operador
        assertEquals("", Calc.press("", Calc.TIMES, false)) // não começa com operador
        assertEquals("7", Calc.press("7", ',', allowsDecimals = false)) // unidade sem vírgula
        assertEquals("7,", Calc.press("7", ',', allowsDecimals = true))
        assertEquals("7×0,", Calc.press("7×", ',', allowsDecimals = true))
        assertEquals("2,5", Calc.press("2,5", ',', allowsDecimals = true)) // só uma vírgula por número
        assertEquals("1,255", Calc.press("1,255", '9', allowsDecimals = true)) // até 3 casas
        assertEquals("5", Calc.press("0", '5', allowsDecimals = false)) // sem zero à esquerda
        assertEquals("9999999", Calc.press("9999999", '1', allowsDecimals = false)) // até 7 dígitos
    }

    @Test
    fun quickButtonsAddToTheExpression() {
        assertEquals("5", Calc.addQuick("", 5.0))
        assertEquals("7×12+5", Calc.addQuick("7×12", 5.0))
        assertEquals("7×10", Calc.addQuick("7×", 10.0))
        assertEquals("3+0,5", Calc.addQuick("3", 0.5))
    }

    @Test
    fun prettyAndHasOperation() {
        assertEquals("7 × 12 + 5", Calc.pretty("7×12+5"))
        assertTrue(Calc.hasOperation("7×12"))
        assertFalse(Calc.hasOperation("84"))
        assertFalse(Calc.hasOperation(""))
    }
}
