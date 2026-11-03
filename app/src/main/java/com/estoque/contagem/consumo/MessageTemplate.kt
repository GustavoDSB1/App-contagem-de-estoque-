package com.estoque.contagem.consumo

import org.json.JSONObject

/**
 * Modelo da mensagem do WhatsApp da aba Consumo.
 *
 * Campos disponíveis:
 * - cabeçalho e rodapé: {tipo} {data} {empresa} {total}
 * - cada linha: {nome} {qtd} {produto} {valor} {unitario}
 */
data class MessageTemplate(
    val header: String = DEFAULT_HEADER,
    val line: String = DEFAULT_LINE,
    /** Linha em branco entre uma pessoa e outra. */
    val blankLineBetweenPeople: Boolean = true,
    /** Opcional (ex.: "Total: {total}"); vazio = sem rodapé. */
    val footer: String = ""
) {
    companion object {
        const val DEFAULT_HEADER = "Consumo {tipo} {data}"
        const val DEFAULT_LINE = "{nome} {qtd} {produto} {valor}"
        val DEFAULT = MessageTemplate()

        val HEADER_FIELDS = listOf("{tipo}", "{data}", "{empresa}", "{total}")
        val LINE_FIELDS = listOf("{nome}", "{qtd}", "{produto}", "{valor}", "{unitario}")

        fun fromJson(o: JSONObject) = MessageTemplate(
            header = o.optString("header", DEFAULT_HEADER),
            line = o.optString("line", DEFAULT_LINE).ifBlank { DEFAULT_LINE },
            blankLineBetweenPeople = o.optBoolean("blankLine", true),
            footer = o.optString("footer", "")
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("header", header)
        .put("line", line)
        .put("blankLine", blankLineBetweenPeople)
        .put("footer", footer)
}

/** Configurações gerais do app. */
data class AppSettings(
    /** Mostra a aba Consumo (quem não usa pode esconder). */
    val consumptionEnabled: Boolean = true,
    val messageTemplate: MessageTemplate = MessageTemplate.DEFAULT,
    /** Já viu a tela de boas-vindas. */
    val onboardingDone: Boolean = false,
    /** Tema: "system", "light" ou "dark". */
    val theme: String = "system",
    /** Última escolha: relatório com valores em R$. */
    val exportWithValues: Boolean = false
)
