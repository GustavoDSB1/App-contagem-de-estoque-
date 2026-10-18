package com.estoque.contagem.data

enum class PersonType(val label: String, val emoji: String) {
    EMPLOYEE("funcionário", "👷"),
    DAY_WORKER("diarista", "🧹")
}

/**
 * Baixa de consumo de um funcionário ou diarista.
 * Não altera o estoque nem a contagem.
 */
data class Consumption(
    val id: String,
    val personName: String,
    val personType: PersonType,
    val productId: String,
    /** Nome do produto no momento da baixa (continua certo se o produto for renomeado/excluído). */
    val productName: String,
    val quantity: Long,
    val unitPriceCents: Long,
    /** Início (00:00) do dia ao qual a baixa pertence. */
    val dayMillis: Long,
    val timestamp: Long
) {
    val totalCents: Long get() = quantity * unitPriceCents
}
