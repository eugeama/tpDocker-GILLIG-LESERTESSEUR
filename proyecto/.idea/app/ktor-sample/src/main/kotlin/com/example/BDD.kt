package com.example

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*

fun Application.configureDatabase() {
    lateinit var dataSource: HikariDataSource
    val config = HikariConfig().apply {
        jdbcUrl         = System.getenv("DB_URL")      ?: environment.config.property("db.url").getString()
        driverClassName = "com.mysql.cj.jdbc.Driver"
        username        = System.getenv("DB_USER")     ?: environment.config.property("db.user").getString()
        password        = System.getenv("DB_PASSWORD") ?: environment.config.property("db.password").getString()
        maximumPoolSize = 10
        isAutoCommit    = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    dataSource = HikariDataSource(config)
}

fun checkDatabaseConnection(): String {
    lateinit var dataSource: HikariDataSource
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT NOW()")
            rs.next()
            return rs.getString(1)   // ej: "2026-05-19 14:32:00"
        }
    }
}