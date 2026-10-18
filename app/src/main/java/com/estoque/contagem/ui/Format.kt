package com.estoque.contagem.ui

import java.text.Normalizer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BR: Locale = Locale.forLanguageTag("pt-BR")

fun formatQty(value: Long): String = NumberFormat.getIntegerInstance(BR).format(value)

fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", BR).format(Date(millis))

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", BR).format(Date(millis))

/** "hoje às 14:32" ou "02/10 às 14:32". */
fun formatWhen(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "hoje às ${formatTime(millis)}"
    else "${SimpleDateFormat("dd/MM", BR).format(Date(millis))} às ${formatTime(millis)}"
}

/** Remove acentos e deixa minúsculo, para a busca achar "agua" em "Água". */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(BR)

/** Só dígitos, no máximo 7 (até 9.999.999). */
fun String.onlyDigits(): String = filter { it.isDigit() }.take(7)
