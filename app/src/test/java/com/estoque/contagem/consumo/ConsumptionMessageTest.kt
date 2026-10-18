package com.estoque.contagem.consumo

import com.estoque.contagem.data.Consumption
import com.estoque.contagem.data.PersonType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ConsumptionMessageTest {

    private val day = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 2, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private var seq = 0L

    private fun c(person: String, product: String, qty: Long, price: Long, type: PersonType = PersonType.EMPLOYEE) =
        Consumption("id$seq", person, type, "p", product, qty, price, day, day + (seq++) * 1000)

    @Test
    fun formatsMoney() {
        assertEquals("9,00", Money.format(900))
        assertEquals("0,50", Money.format(50))
        assertEquals("1.234,50", Money.format(123450))
    }

    @Test
    fun employeeMessage() {
        val msg = ConsumptionMessage.build(
            PersonType.EMPLOYEE, day,
            listOf(c("Ricardo", "coca 600", 1, 900), c("Leonardo", "guaraná lata", 1, 700))
        )
        assertEquals(
            "Consumo funcionário 02/10\nRicardo 1 coca 600 9,00\n\nLeonardo 1 guaraná lata 7,00",
            msg
        )
    }

    @Test
    fun dayWorkerMessageMultipliesQuantity() {
        val one = ConsumptionMessage.build(
            PersonType.DAY_WORKER, day,
            listOf(c("Andressa", "pipoca", 1, 1500, PersonType.DAY_WORKER))
        )
        assertEquals("Consumo diarista 02/10\nAndressa 1 pipoca 15,00", one)

        val two = ConsumptionMessage.build(
            PersonType.DAY_WORKER, day,
            listOf(c("Andressa", "pipoca", 2, 1500, PersonType.DAY_WORKER))
        )
        assertEquals("Consumo diarista 02/10\nAndressa 2 pipoca 30,00", two)
    }

    @Test
    fun groupsSamePersonTogether() {
        val msg = ConsumptionMessage.build(
            PersonType.EMPLOYEE, day,
            listOf(c("Ricardo", "coca 600", 1, 900), c("Leonardo", "água", 1, 300), c("ricardo ", "pão", 2, 100))
        )
        assertEquals(
            "Consumo funcionário 02/10\nRicardo 1 coca 600 9,00\nricardo 2 pão 2,00\n\nLeonardo 1 água 3,00",
            msg
        )
    }
}
