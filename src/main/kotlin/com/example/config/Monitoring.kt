package com.example.config

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.ext.inject

private const val CALL_ID_LENGTH = 32

val monitoringModule = module {
    single<PrometheusMeterRegistry> { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}

fun Application.configureMonitoring() {
    val prometheusRegistry by inject<PrometheusMeterRegistry>()
    val pool by inject<ConnectionPool>()

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate(CALL_ID_LENGTH, "0123456789abcdef")
    }

    install(CallLogging) {
        callIdMdc("call-id")
    }

    // Health checks for health endpoint
    val healthChecks = HealthCheckRegistry(Dispatchers.Default) {
        val r2dbcCheck = object : HealthCheck {
            override val name = "r2dbc_connections"
            override suspend fun check(): HealthCheckResult {
                val metrics = pool.metrics.orElse(null)
                    ?: return HealthCheckResult.unhealthy("Pool metrics unavailable", null)
                val allocated = metrics.allocatedSize()
                return if (allocated >= 1) HealthCheckResult.healthy("$allocated connections allocated")
                else HealthCheckResult.unhealthy("No connections allocated", null)
            }
        }
        // Alert if we have no open R2DBC pool connections
        register(r2dbcCheck)
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
