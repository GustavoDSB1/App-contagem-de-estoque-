package com.estoque.contagem.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

/** Unidade de medida do produto. Algumas aceitam casas decimais (ex.: 2,5 kg). */
enum class MeasureUnit(
    val code: String,
    val singular: String,
    val plural: String,
    val allowsDecimals: Boolean
) {
    UN("un", "unidade", "unidades", false),
    KG("kg", "kg", "kg", true),
    G("g", "grama", "gramas", false),
    L("L", "litro", "litros", true),
    ML("ml", "ml", "ml", false),
    CX("cx", "caixa", "caixas", false),
    PCT("pct", "pacote", "pacotes", false),
    FD("fd", "fardo", "fardos", false),
    DZ("dz", "dúzia", "dúzias", true),
    M("m", "metro", "metros", true);

    fun label(quantity: Double): String = if (quantity == 1.0) singular else plural

    companion object {
        private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT).trim().trimEnd('.')

        /** Aceita o código ("kg"), o nome ("quilo", "litros") ou vazio (unidade). */
        fun fromText(text: String?): MeasureUnit {
            val t = norm(text ?: "")
            if (t.isEmpty()) return UN
            entries.firstOrNull { norm(it.code) == t || norm(it.singular) == t || norm(it.plural) == t }?.let { return it }
            return when (t) {
                "und", "unid", "unidade(s)", "u", "pc", "peca", "pecas" -> UN
                "quilo", "quilos", "kilo", "kilos", "quilograma", "quilogramas" -> KG
                "lt", "lts", "l" -> L
                "caixas", "cxs" -> CX
                "pacotes", "pc.", "pcts" -> PCT
                "fardos" -> FD
                "duzia", "duzias" -> DZ
                else -> UN
            }
        }
    }
}

/** Quantidades com até 3 casas decimais. */
object Qty {
    private val BR: Locale = Locale.forLanguageTag("pt-BR")
    const val MAX = 9_999_999.0

    /** Arredonda para 3 casas (evita 0,30000000000000004). */
    fun round(value: Double): Double = (value * 1000).roundToLong() / 1000.0

    /** 12 -> "12"; 2.5 -> "2,5"; 1234.75 -> "1.234,75" */
    fun format(value: Double): String = DecimalFormat("#,##0.###", DecimalFormatSymbols(BR)).format(round(value))

    /** Texto digitado ("2,5", "2.5", "1.234,5") -> número; null se inválido. */
    fun parse(text: String): Double? {
        var s = text.trim().replace(" ", "")
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        val v = s.toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite() || v < 0) null else round(v)
    }

    /** Filtra o que o usuário digita: só números e, se a unidade aceitar, uma vírgula com até 3 casas. */
    fun cleanInput(text: String, allowsDecimals: Boolean): String {
        if (!allowsDecimals) return text.filter { it.isDigit() }.take(7)
        val sb = StringBuilder()
        var hasComma = false
        var decimals = 0
        for (ch in text) {
            when {
                ch.isDigit() && !hasComma && sb.length < 7 -> sb.append(ch)
                ch.isDigit() && hasComma && decimals < 3 -> { sb.append(ch); decimals++ }
                (ch == ',' || ch == '.') && !hasComma -> { hasComma = true; sb.append(if (sb.isEmpty()) "0," else ",") }
            }
        }
        return sb.toString()
    }

    /** Valor para mostrar num campo de digitação (vírgula, sem separador de milhar). */
    fun toInput(value: Double): String = format(value).replace(".", "")
}
