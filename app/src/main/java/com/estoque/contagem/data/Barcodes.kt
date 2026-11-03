package com.estoque.contagem.data

import java.math.BigDecimal

/** Regras simples para códigos de barras (sem dependências do Android, para testar). */
object Barcodes {

    /** Remove espaços/quebras e limita o tamanho. */
    fun clean(raw: String): String = raw.trim().replace(Regex("\\s+"), "").take(64)

    /**
     * Código vindo de planilha: o Excel pode guardar como número
     * ("7891234567890.0" ou "7.89123456789E12"). Converte de volta para os dígitos.
     */
    fun fromSpreadsheet(raw: String?): String {
        val t = clean(raw ?: "")
        if (t.isEmpty()) return ""
        val numeric = Regex("^[0-9]+(\\.[0-9]+)?([eE][+]?[0-9]+)?$")
        if (numeric.matches(t) && (t.contains('.') || t.contains('e') || t.contains('E'))) {
            return runCatching {
                val bd = BigDecimal(t)
                if (bd.stripTrailingZeros().scale() <= 0) bd.toBigInteger().toString() else t
            }.getOrDefault(t)
        }
        return t
    }

    /** Procura o produto com esse código (ignora diferença de espaços). */
    fun find(products: List<Product>, code: String): Product? {
        val c = clean(code)
        if (c.isEmpty()) return null
        return products.firstOrNull { clean(it.barcode) == c }
    }
}
