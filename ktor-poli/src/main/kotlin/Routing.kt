package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.receive
import kotlinx.serialization.Serializable

private val lista = mutableListOf<Usuario>()

@Serializable
data class HealthResponse(val status: String, val message: String)

@Serializable
data class DbStatusResponse(val status: String, val connected: Boolean, val detail: String? = null)

fun Application.configureRouting() {

    @Serializable
    data class DbStatusResponse(val status: String, val connected: Boolean, val detail: String? = null)

    routing {
        get("/") {
            call.respond(HttpStatusCode.OK)
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(status = "UP", message = "API activa")
            )
        }

        get("/db-status") {
            try {
                val result = checkDatabaseConnection()
                call.respond(
                    HttpStatusCode.OK,
                    DbStatusResponse(status = "UP", connected = true, detail = result)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    DbStatusResponse(status = "DOWN", connected = false, detail = e.message)
                )
            }
        }

        post("/items") {
            val nuevoUsuario = call.receive<Usuario>()
            lista.add(nuevoUsuario)
            call.respond(HttpStatusCode.OK)
        }

        get("/items") {
            call.respond(lista)
        }
    }
}