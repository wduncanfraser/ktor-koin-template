package com.example

import com.example.config.configureStatusPages
import com.example.config.monitoringModule
import com.example.config.warmupDatabase
import com.example.generated.api.controllers.TodosController.Companion.todosRoutes
import com.example.todo.api.TodoController
import com.example.todo.todoModule
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
import org.jooq.DSLContext
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.getValue

fun Application.integrationTestModule(
    dslContext: DSLContext,
    redisHost: String,
    redisPort: Int,
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) { configureStatusPages() }

    val testDatabaseModule = module {
        single<DSLContext> { dslContext }
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
