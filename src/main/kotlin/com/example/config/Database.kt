package com.example.config

import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
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
    runBlocking { ctx.selectOne().awaitSingle() }
}

fun Application.databaseModule(): Module {
    val databaseConfig: DatabaseConfig = property("database")
    return databaseModule(databaseConfig)
}

fun databaseModule(databaseConfig: DatabaseConfig) = module {
    single<ConnectionPool> {
        val options = ConnectionFactoryOptions.builder()
            .from(ConnectionFactoryOptions.parse(databaseConfig.url))
            .option(ConnectionFactoryOptions.USER, databaseConfig.user)
            .option(ConnectionFactoryOptions.PASSWORD, databaseConfig.password)
            .build()
        val connectionFactory = ConnectionFactories.get(options)
        ConnectionPool(
            ConnectionPoolConfiguration.builder(connectionFactory)
                .maxSize(databaseConfig.poolSize)
                .build()
        )
    }
    single<DSLContext> {
        // Disable logo and tips before configuring jooq
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        val config = DefaultConfiguration().apply {
            setConnectionFactory(get<ConnectionPool>())
            setSQLDialect(SQLDialect.POSTGRES)
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
