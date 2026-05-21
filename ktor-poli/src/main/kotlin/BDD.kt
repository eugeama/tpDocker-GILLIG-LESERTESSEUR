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