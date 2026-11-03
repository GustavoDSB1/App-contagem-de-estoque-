package com.estoque.contagem.data

/**
 * Grupo de pessoas da aba Consumo (ex.: funcionário, diarista, cliente).
 * Criado pelo usuário; [id] é fixo e guardado nas pessoas e nas baixas.
 */
data class PersonType(
    val id: String,
    /** Nome no singular, como aparece na mensagem ("Consumo funcionário 02/10"). */
    val label: String,
    val emoji: String
) {
    companion object {
        // Ids iguais aos da versão antiga (enum), para os dados salvos continuarem valendo
        val EMPLOYEE = PersonType("EMPLOYEE", "funcionário", "👷")
        val DAY_WORKER = PersonType("DAY_WORKER", "diarista", "🧹")
        val DEFAULTS = listOf(EMPLOYEE, DAY_WORKER)

        val EMOJIS = listOf("👷", "🧹", "🧑‍🍳", "🧑‍💼", "🙋", "👤", "🪑", "🏠", "🚚", "🛒", "⭐", "🎓")

        /** Grupo com esse id; se foi apagado, um genérico para não quebrar telas antigas. */
        fun resolve(groups: List<PersonType>, id: String): PersonType =
            groups.firstOrNull { it.id == id } ?: PersonType(id, id.lowercase(), "👤")
    }
}

/**
 * Baixa de consumo de uma pessoa de um grupo (funcionário, diarista...).
 * Não altera o estoque nem a contagem.
 */
data class Consumption(
    val id: String,
    val personName: String,
    /** Id do grupo ([PersonType.id]). */
    val personTypeId: String,
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
