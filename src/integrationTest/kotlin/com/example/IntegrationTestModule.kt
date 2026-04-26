package com.example

import com.example.authn.authNModule
import com.example.config.AuthenticationConfig
import com.example.config.CorsConfig
import com.example.config.DatabaseConfig
import com.example.config.RedisConfig
import com.example.config.apiRoutes
import com.example.config.configureCors
import com.example.config.configureMonitoring
import com.example.config.configureSecurity
import com.example.config.configureStatusPages
import com.example.config.databaseModule
import com.example.config.httpClientModule
import com.example.config.monitoringModule
import com.example.config.redisModule
import com.example.config.warmupDatabase
import com.example.todo.todoModule
import com.example.todolist.todoListModule
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
    authConfig: AuthenticationConfig,
    corsConfig: CorsConfig,
) {
    install(ContentNegotiation) { json() }

    configureCors(corsConfig)

    // Test specific koin setup
    install(Koin) {
        slf4jLogger()
        modules(
            monitoringModule,
            httpClientModule,
            authNModule,
            databaseModule(databaseConfig),
            redisModule(redisConfig),
            todoModule,
            todoListModule,
        )
    }
    configureMonitoring()
    configureSecurity(authConfig)

    install(StatusPages) {
        configureStatusPages()
    }

    // Warmup the database
    warmupDatabase()

    routing {
        apiRoutes()
    }
}
