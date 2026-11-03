package com.estoque.contagem.data

/** Funcionário ou diarista cadastrado, para escolher na aba Consumo sem digitar. */
data class Person(
    val id: String,
    val name: String,
    /** Id do grupo ([PersonType.id]). */
    val typeId: String
)
