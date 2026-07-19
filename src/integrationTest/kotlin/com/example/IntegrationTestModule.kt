package com.example

import com.example.authn.authNModule
import com.example.config.AuthenticationConfig
import com.example.config.CorsConfig
import com.example.config.DatabaseConfig
import com.example.config.OpenFgaConfig
import com.example.config.RedisConfig
import com.example.config.apiRoutes
import com.example.config.TRACER_INSTRUMENTATION_SCOPE
import com.example.config.configureCors
import com.example.config.configureMonitoring
import com.example.config.configureSecurity
import com.example.config.configureStatusPages
import com.example.config.configureTracing
import com.example.config.databaseModule
import com.example.config.httpClientModule
import com.example.config.monitoringModule
import com.example.config.openFgaModule
import com.example.config.redisModule
import com.example.config.warmupDatabase
import com.example.todo.todoModule
import com.example.todolist.todoListModule
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// Tracing wired to the supplied [OpenTelemetry] — [OpenTelemetry.noop] by default so tests need no
// collector, or an in-memory-exporter SDK when a test asserts on spans (see TracingIntegrationTest).
private fun tracingTestModule(openTelemetry: OpenTelemetry) = module {
    single<OpenTelemetry> { openTelemetry }
    single<Tracer> { get<OpenTelemetry>().getTracer(TRACER_INSTRUMENTATION_SCOPE) }
}

fun Application.integrationTestModule(
    databaseConfig: DatabaseConfig,
    redisConfig: RedisConfig,
    openFgaConfig: OpenFgaConfig,
    authConfig: AuthenticationConfig,
    corsConfig: CorsConfig,
    openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    install(ContentNegotiation) { json() }

    configureCors(corsConfig)

    // Test specific koin setup
    install(Koin) {
        slf4jLogger()
        modules(
            tracingTestModule(openTelemetry),
            monitoringModule,
            httpClientModule,
            authNModule,
            databaseModule(databaseConfig),
            redisModule(redisConfig),
            openFgaModule(openFgaConfig),
            todoModule,
            todoListModule,
        )
    }
    configureTracing()
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
