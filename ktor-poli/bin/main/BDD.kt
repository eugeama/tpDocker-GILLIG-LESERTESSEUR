package com.example

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*

lateinit var dataSource: HikariDataSource

fun Application.configureDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl         = environment.config.property("db.url").getString()
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = environment.config.property("db.user").getString()
        password        = environment.config.property("db.password").getString()
        maximumPoolSize = 10
        isAutoCommit    = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    dataSource = HikariDataSource(config)
}

fun checkDatabaseConnection(): String {
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT NOW()")
            rs.next()
            return rs.getString(1)
        }
    }
}

fun insertUsuario(nombre: String) {
    dataSource.connection.use { conn ->
        conn.autoCommit = true
        conn.prepareStatement("INSERT INTO usuarios (nombre, creado) VALUES (?, CURDATE())").use { stmt ->
            stmt.setString(1, nombre)
            stmt.executeUpdate()
        }
    }
}

fun getAllUsuarios(): List<Usuario> {
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, nombre, creado FROM usuarios")
            val list = mutableListOf<Usuario>()
            while (rs.next()) {
                list.add(
                    Usuario(
                        id     = rs.getInt("id"),
                        nombre = rs.getString("nombre"),
                        creado = rs.getDate("creado").toString()
                    )
                )
            }
            return list
        }
    }
}
