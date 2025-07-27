package com.example.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.dsl.module
import org.koin.ktor.ext.inject

/**
 * Method to run a noop query in jooq. This triggers all jooq class loading
 */
fun Application.warmupDatabase() {
    val ctx by inject<DSLContext>()
    ctx.selectOne().fetch()
}

fun Application.databaseModule() = module {
    single<HikariDataSource> { buildDataSource() }
    single<DSLContext> {
        // Disable logo and tips before configuring jooq
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        DSL.using(get<HikariDataSource>(), SQLDialect.POSTGRES)
    }
}

@Serializable
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
)

fun Application.buildDataSource(): HikariDataSource {
    val databaseConfig: DatabaseConfig = property("database")
    val prometheusRegistry by inject<PrometheusMeterRegistry>()
    val hikariConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = databaseConfig.url
        username = databaseConfig.user
        password = databaseConfig.password
        maximumPoolSize = databaseConfig.poolSize
        metricRegistry = prometheusRegistry
    }

    return HikariDataSource(hikariConfig)
}
