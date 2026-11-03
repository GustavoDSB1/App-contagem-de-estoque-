package com.estoque.contagem.data

/** Dados do negócio: aparecem no topo do app e no cabeçalho da planilha. */
data class CompanySettings(
    val name: String = "",
    /** CNPJ ou CPF (texto livre). */
    val document: String = "",
    /** Arquivo PNG do logo (em filesDir/fotos). */
    val logoFile: String? = null
) {
    val isEmpty: Boolean get() = name.isBlank() && document.isBlank() && logoFile == null
}
