package com.example

import kotlinx.serialization.Serializable

@Serializable
data class Usuario(
    val id: Int,
    val nombre: String,
    val creado: String
)

@Serializable
data class ItemRequest(val nombre: String)
