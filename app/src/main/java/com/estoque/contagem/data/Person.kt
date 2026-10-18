package com.estoque.contagem.data

/** Funcionário ou diarista cadastrado, para escolher na aba Consumo sem digitar. */
data class Person(
    val id: String,
    val name: String,
    val type: PersonType
)
