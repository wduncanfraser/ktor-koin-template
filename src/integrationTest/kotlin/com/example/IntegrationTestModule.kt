package com.example

import com.example.config.configureStatusPages
import com.example.config.monitoringModule
import com.example.config.warmupDatabase
import com.example.generated.api.controllers.TodosController.Companion.todosRoutes
import com.example.todo.api.TodoController
import com.example.todo.todoModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.getValue

fun Application.integrationTestModule(
    jdbcUrl: String,
    dbUser: String,
    dbPassword: String,
    redisHost: String,
    redisPort: Int,
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) { configureStatusPages() }

    val testDatabaseModule = module {
        single<HikariDataSource> {
            val prometheusRegistry = get<PrometheusMeterRegistry>()
            val hikariConfig = HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                this.jdbcUrl = jdbcUrl
                username = dbUser
                password = dbPassword
                maximumPoolSize = 5
                metricRegistry = prometheusRegistry
            }
            HikariDataSource(hikariConfig)
        }
        single<DSLContext> {
            System.setProperty("org.jooq.no-logo", "true")
            System.setProperty("org.jooq.no-tips", "true")
            val config = DefaultConfiguration().apply {
                setDataSource(get<HikariDataSource>())
                setSQLDialect(SQLDialect.POSTGRES)
                setExecutorProvider { Dispatchers.IO.asExecutor() }
            }
            DSL.using(config)
        }
    }

    val testRedisModule = module {
        single<RedisClient> {
            val redisUri = RedisURI.Builder
                .redis(redisHost, redisPort)
                .build()
            RedisClient.create(redisUri).apply {
                options = ClientOptions.builder()
                    .autoReconnect(true)
                    .pingBeforeActivateConnection(true)
                    .build()
            }
        }
        single<StatefulRedisConnection<String, String>> { get<RedisClient>().connect() }
    }

    install(Koin) {
        slf4jLogger()
        modules(
            monitoringModule,
            testDatabaseModule,
            testRedisModule,
            todoModule,
        )
    }

    warmupDatabase()

    val todoController by inject<TodoController>()

    routing {
        route("/api/v1") {
            todosRoutes(todoController)
        }
    }
}
