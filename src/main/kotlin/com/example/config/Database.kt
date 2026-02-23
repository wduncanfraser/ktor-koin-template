package com.example.config

import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckResult
import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

/**
 * Method to run a noop query in jooq. This triggers all jooq class loading
 */
fun Application.warmupDatabase() {
    val ctx by inject<DSLContext>()
    runBlocking { ctx.selectOne().awaitSingle() }
}

fun Application.databaseModule(): Module {
    val databaseConfig: DatabaseConfig = property("database")
    return databaseModule(databaseConfig)
}

fun databaseModule(databaseConfig: DatabaseConfig) = module {
    single<ConnectionPool> {
        val options = ConnectionFactoryOptions.builder()
            .from(ConnectionFactoryOptions.parse(databaseConfig.url))
            .option(ConnectionFactoryOptions.USER, databaseConfig.user)
            .option(ConnectionFactoryOptions.PASSWORD, databaseConfig.password)
            .build()
        val connectionFactory = ConnectionFactories.get(options)
        ConnectionPool(
            ConnectionPoolConfiguration.builder(connectionFactory)
                .initialSize(databaseConfig.poolSize)
                .maxSize(databaseConfig.poolSize)
                .build()
        )
    }
    single<DSLContext> {
        // Disable logo and tips before configuring jooq
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
        val config = DefaultConfiguration().apply {
            setConnectionFactory(get<ConnectionPool>())
            setSQLDialect(SQLDialect.POSTGRES)
        }
        DSL.using(config)
    }
}

@Serializable
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
)

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
