package com.example.config

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.ext.inject

val monitoringModule = module {
    single<PrometheusMeterRegistry> { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}

fun Application.configureMonitoring() {
    val prometheusRegistry by inject<PrometheusMeterRegistry>()
    val hikariDataSource by inject<HikariDataSource>()

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate(32, "0123456789abcdef")
    }

    install(CallLogging) {
        callIdMdc("call-id")
    }

    // Health checks for health endpoint
    val healthChecks = HealthCheckRegistry(Dispatchers.Default) {
        // Alert if we have no open connections
        register(HikariConnectionsHealthCheck(hikariDataSource, 1))
        // Ensure threads aren't deadlocked
        register(ThreadDeadlockHealthCheck())
    }

    // Bind our healthchecks to prometheus so we get metrics on failing checks
    CohortMetrics(healthChecks).bindTo(prometheusRegistry)

    install(Cohort) {
        healthcheck("/health", healthChecks)
    }

    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }

    routing {
        get("/metrics") {
            call.respond(prometheusRegistry.scrape())
        }
    }
}
