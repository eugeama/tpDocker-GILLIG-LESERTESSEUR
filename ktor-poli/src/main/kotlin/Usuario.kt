package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDate

@Serializable
data class Usuario(
    val id: Int,
    val nombre: String,
    @Contextual
    val creado: LocalDate = LocalDate.now()
)
