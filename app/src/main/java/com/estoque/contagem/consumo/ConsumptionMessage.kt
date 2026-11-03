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
 * Monta a mensagem para o WhatsApp a partir do [MessageTemplate]. Com o modelo padrão:
 *
 * Consumo funcionário 02/10
 * Ricardo 1 coca 600 9,00
 *
 * Leonardo 1 guaraná lata 7,00
 */
object ConsumptionMessage {

    fun build(
        type: PersonType,
        dayMillis: Long,
        entries: List<Consumption>,
        template: MessageTemplate = MessageTemplate.DEFAULT,
        companyName: String = ""
    ): String {
        val total = entries.sumOf { it.totalCents }
        val common = mapOf(
            "{tipo}" to type.label,
            "{data}" to Days.shortDate(dayMillis),
            "{empresa}" to companyName.trim(),
            "{total}" to Money.format(total)
        )
        val lines = mutableListOf<String>()
        fill(template.header, common).takeIf { it.isNotEmpty() }?.let { lines += it }

        val byPerson = entries
            .sortedBy { it.timestamp }
            .groupBy { it.personName.trim().lowercase(BR) }
        byPerson.values.forEachIndexed { i, list ->
            if (i > 0 && template.blankLineBetweenPeople) lines += ""
            list.forEach { lines += line(it, template) }
        }

        fill(template.footer, common).takeIf { it.isNotEmpty() }?.let {
            lines += ""
            lines += it
        }
        return lines.joinToString("\n")
    }

    fun line(e: Consumption, template: MessageTemplate = MessageTemplate.DEFAULT): String = fill(
        template.line,
        mapOf(
            "{nome}" to e.personName.trim(),
            "{qtd}" to e.quantity.toString(),
            "{produto}" to e.productName.trim(),
            "{valor}" to Money.format(e.totalCents),
            "{unitario}" to Money.format(e.unitPriceCents)
        )
    )

    /** Troca os campos pelo valor e tira espaços sobrando (ex.: campo vazio no meio). */
    private fun fill(text: String, values: Map<String, String>): String {
        var out = text
        values.forEach { (key, value) -> out = out.replace(key, value, ignoreCase = true) }
        return out.replace(Regex("[ \\t]+"), " ").trim()
    }
}
