package com.example.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

/**
 * Method to run a noop query in jooq. This triggers all jooq class loading
 */
fun Application.warmupDatabase() {
    val ctx by inject<DSLContext>()
    ctx.selectOne().fetch()
}

fun Application.databaseModule(): Module {
    val databaseConfig: DatabaseConfig = property("database")
    return this.databaseModule(databaseConfig)
}

fun Application.databaseModule(databaseConfig: DatabaseConfig) = module {
    single<HikariDataSource> { buildDataSource(databaseConfig) }
    single<DSLContext> {
        // Disable logo and tips before configuring jooq
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        val config = DefaultConfiguration().apply {
            setDataSource(get<HikariDataSource>())
            setSQLDialect(SQLDialect.POSTGRES)
            // Wire up Jooq to use the IO Dispatcher for coroutines
            setExecutorProvider {  Dispatchers.IO.asExecutor() }
        }
        DSL.using(config)
    }
}

@Serializable
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
)

fun Application.buildDataSource(databaseConfig: DatabaseConfig): HikariDataSource {
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
