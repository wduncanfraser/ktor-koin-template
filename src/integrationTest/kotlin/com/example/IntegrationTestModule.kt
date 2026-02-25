package com.example

import com.example.config.*
import com.example.todo.todoModule
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.integrationTestModule(
    databaseConfig: DatabaseConfig,
    redisConfig: RedisConfig,
    sessionCookieName: String,
    sessionSigningKey: String,
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

    val authConfig = com.example.config.AuthenticationConfig(
        sessionCookieName = sessionCookieName,
        sessionSigningKey = sessionSigningKey,
        oAuth = OAuthConfig(
            callbackUrl = "",
            clientId = "",
            clientSecret = ""
        )
    )
    configureSecurity(authConfig)

    install(StatusPages) {
        configureStatusPages()
    }

    // Warmup the database
    warmupDatabase()

    routing {
        authenticate("auth-session") {
            apiRoutes()
        }
    }
}
