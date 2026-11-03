package com.estoque.contagem.ui

import com.estoque.contagem.data.MeasureUnit
import com.estoque.contagem.data.PhotoChange

/** O que foi preenchido na janela de cadastro/edição de produto. */
data class ProductForm(
    val name: String,
    val emoji: String,
    /** Quantidade já contada (só no cadastro). */
    val initialQuantity: Double = 0.0,
    val priceCents: Long = 0,
    val photo: PhotoChange = PhotoChange.Keep,
    val unit: MeasureUnit = MeasureUnit.UN,
    val category: String = "",
    /** Código de barras (opcional). */
    val barcode: String = ""
)
