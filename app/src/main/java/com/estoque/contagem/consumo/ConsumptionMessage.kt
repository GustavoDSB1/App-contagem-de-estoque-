package com.estoque.contagem.consumo

import com.estoque.contagem.data.Consumption
import com.estoque.contagem.data.PersonType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BR: Locale = Locale.forLanguageTag("pt-BR")

object Money {
    /** 900 -> "9,00"; 123450 -> "1.234,50" */
    fun format(cents: Long): String {
        val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(BR))
        return df.format(cents / 100.0)
    }

    fun formatWithSymbol(cents: Long): String = "R$ ${format(cents)}"
}

object Days {
    /** Meia-noite do dia de [millis]. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(dayMillis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayMillis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis.let(::startOfDay)

    fun shortDate(millis: Long): String = SimpleDateFormat("dd/MM", BR).format(Date(millis))
}

/**
 * Monta a mensagem para o WhatsApp, no modelo:
 *
 * Consumo funcionário 02/10
 * Ricardo 1 coca 600 9,00
 *
 * Leonardo 1 guaraná lata 7,00
 */
object ConsumptionMessage {

    fun build(type: PersonType, dayMillis: Long, entries: List<Consumption>): String {
        val sb = StringBuilder("Consumo ${type.label} ${Days.shortDate(dayMillis)}")
        val byPerson = entries
            .sortedBy { it.timestamp }
            .groupBy { it.personName.trim().lowercase(BR) }
        byPerson.values.forEachIndexed { i, list ->
            sb.append(if (i == 0) "\n" else "\n\n")
            sb.append(list.joinToString("\n") { line(it) })
        }
        return sb.toString()
    }

    fun line(e: Consumption): String =
        "${e.personName.trim()} ${e.quantity} ${e.productName.trim()} ${Money.format(e.totalCents)}"
}
