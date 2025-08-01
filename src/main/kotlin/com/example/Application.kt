package com.example

import com.example.config.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CORS) {
        configureCors()
    }

    configureKoin()
    configureMonitoring()
    configureSecurity()

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
