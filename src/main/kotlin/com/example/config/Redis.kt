package com.example.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder
import io.lettuce.core.metrics.MicrometerOptions
import io.lettuce.core.resource.ClientResources
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

fun Application.redisModule(): Module {
    val redisConfig: RedisConfig = property("redis")
    return redisModule(redisConfig)
}

fun Application.redisModule(redisConfig: RedisConfig) = module {
    single<RedisClient> { buildRedisClient(redisConfig) }
    single<StatefulRedisConnection<String, String>> { get<RedisClient>().connect() }
}

@Serializable
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String? = null,
)

fun Application.buildRedisClient(redisConfig: RedisConfig): RedisClient {
    val prometheusRegistry by inject<PrometheusMeterRegistry>()

    val redisUri = RedisURI.Builder
        .redis(redisConfig.host, redisConfig.port)
        .apply {
            redisConfig.password?.let {
                withPassword(it.toCharArray())
            }
        }
        .build()

    val micrometerOptions = MicrometerOptions.builder()
        .histogram(false)
        .build()

    val clientResources = ClientResources.builder()
        .commandLatencyRecorder(MicrometerCommandLatencyRecorder(prometheusRegistry, micrometerOptions))
        .build()

    return RedisClient.create(clientResources, redisUri).apply {
        options = ClientOptions.builder()
            .autoReconnect(true)
            .pingBeforeActivateConnection(true)
            .build()
    }
}
