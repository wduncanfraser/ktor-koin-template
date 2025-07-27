package com.example

import com.example.config.apiRoutes
import com.example.config.configureCors
import com.example.config.configureKoin
import com.example.config.configureMonitoring
import com.example.config.configureStatusPages
import com.example.config.warmupDatabase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }

    install(CORS) {
        configureCors()
    }

    configureKoin()
    configureMonitoring()

    install(StatusPages) {
        configureStatusPages()
    }

    // Warmup the database
    warmupDatabase()

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/todo.yaml")
        apiRoutes()
    }
}
