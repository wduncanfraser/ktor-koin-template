package com.example

import com.example.config.*
import com.example.todo.todoModule
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.integrationTestModule(
    databaseConfig: DatabaseConfig,
    redisConfig: RedisConfig,
) {
    install(ContentNegotiation) { json() }

    // Test specific koin setup
    install(Koin) {
        slf4jLogger()
        modules(
            monitoringModule,
            httpClientModule,
            databaseModule(databaseConfig),
            redisModule(redisConfig),
            todoModule,
        )
    }
    configureMonitoring()

    install(StatusPages) {
        configureStatusPages()
    }

    // Warmup the database
    warmupDatabase()

    routing {
        // TODO: Real session auth in tests
        apiRoutes()
    }
}
