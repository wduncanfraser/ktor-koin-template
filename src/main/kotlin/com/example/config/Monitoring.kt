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
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
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
        // Alert if we have no open R2DBC pool connections
        register(ConnectionPoolMinAllocated(pool, 1))
        // Ensure threads aren't deadlocked
        register(ThreadDeadlockHealthCheck())
    }

    install(Cohort) {
        healthcheck("/health", healthChecks)
    }

    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }

    // Bind our healthchecks to prometheus so we get metrics on failing checks
    CohortMetrics(healthChecks).bindTo(prometheusRegistry)
    // Bind R2DBC connection pool gauges to Prometheus
    pool.bindTo(prometheusRegistry)

    routing {
        get("/metrics") {
            call.respond(prometheusRegistry.scrape())
        }
    }
}

/**
 * Function to register a set of R2DBC [ConnectionPool] metrics to a [PrometheusMeterRegistry]
 */
fun ConnectionPool.bindTo(registry: PrometheusMeterRegistry) {
    metrics.ifPresent { metrics ->
        val tags = listOf(Tag.of("name", this.metadata.name))

        Gauge.builder("r2dbc.pool.connections.acquired", metrics) { it.acquiredSize().toDouble() }
            .description("Connections currently acquired from the pool")
            .tags(tags)
            .register(registry)
        Gauge.builder("r2dbc.pool.connections.allocated", metrics) { it.allocatedSize().toDouble() }
            .description("Total connections currently allocated in the pool")
            .tags(tags)
            .register(registry)
        Gauge.builder("r2dbc.pool.connections.idle", metrics) { it.idleSize().toDouble() }
            .description("Connections currently idle in the pool")
            .tags(tags)
            .register(registry)
        Gauge.builder("r2dbc.pool.connections.pending", metrics) { it.pendingAcquireSize().toDouble() }
            .description("Pending connection acquisition requests")
            .tags(tags)
            .register(registry)
        Gauge.builder("r2dbc.pool.connections.max.allocated", metrics) { it.maxAllocatedSize.toDouble() }
            .description("Maximum connections allowed in the pool")
            .tags(tags)
            .register(registry)
    }
}

/**
 * Health check for the amount of allocated connections in an R2DBC [ConnectionPool]
 *
 * The check is considered healthy if the allocated connection count - idle and active - is >= [minAllocated].
 */
class ConnectionPoolMinAllocated(
    private val pool: ConnectionPool,
    private val minAllocated: Int,
    override val name: String = "r2dbc_connections",
) : HealthCheck {
    override suspend fun check(): HealthCheckResult {
        val metrics = pool.metrics.orElse(null)
            ?: return HealthCheckResult.unhealthy("Pool metrics unavailable", null)
        val allocated = metrics.allocatedSize()
        val msg = "$allocated connections allocated to pool ${pool.metadata.name} [minAllocated: $minAllocated]"
        return if (allocated >= minAllocated) {
            HealthCheckResult.healthy(msg)
        } else {
            HealthCheckResult.unhealthy(msg, null)
        }
    }
}
