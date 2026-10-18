package com.estoque.contagem.data

/** Um lançamento feito na contagem: somou unidades ou corrigiu o total. */
data class CountEntry(
    val amount: Long,
    val totalAfter: Long,
    val timestamp: Long,
    val isCorrection: Boolean
)

data class Product(
    val id: String,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val quantity: Long,
    val entries: List<CountEntry>,
    val createdAt: Long,
    /** Valor de venda em centavos (usado só na aba Consumo, não vai para o Excel). */
    val priceCents: Long = 0,
    /** Nome do arquivo da foto (em filesDir/fotos). Sem foto, usa o [emoji]. */
    val imageFile: String? = null,
    /** Na lista de exclusão: continua no app, mas não vai para a planilha Excel de contagem. */
    val excludeFromExcel: Boolean = false
) {
    val wasCounted: Boolean get() = entries.isNotEmpty()
    val lastCountAt: Long? get() = entries.lastOrNull()?.timestamp
}

/** Tudo o que fica salvo no celular. */
data class StockData(
    val products: List<Product>,
    val countStartedAt: Long,
    val consumptions: List<Consumption> = emptyList(),
    val people: List<Person> = emptyList()
)
