package com.example.config

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import com.sksamuel.cohort.logback.LogbackManager
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.api.StatefulRedisConnection
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
    val redisConnection by inject<StatefulRedisConnection<String, String>>()

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate(CALL_ID_LENGTH, "0123456789abcdef")
    }

    install(CallLogging) {
        callIdMdc("call-id")
    }

    // Health checks for health endpoint
    val healthChecks = HealthCheckRegistry(Dispatchers.Default) {
        // Alert if we have no open R2DBC pool connections
        register(ConnectionPoolMinAllocated(pool, 1))
        // Ensure redis is connecting successfully
        register(RedisHealthCheck(redisConnection))
        // Ensure threads aren't deadlocked
        register(ThreadDeadlockHealthCheck())
    }

    install(Cohort) {
        logManager = LogbackManager

        // Health checks
        healthcheck("/health", healthChecks)
    }

    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }

    // Bind our health checks to prometheus so we get metrics on failing checks
    CohortMetrics(healthChecks).bindTo(prometheusRegistry)
    // Bind R2DBC connection pool gauges to Prometheus
    pool.bindTo(prometheusRegistry)

    routing {
        get("/metrics") {
            call.respond(prometheusRegistry.scrape())
        }
    }
}
