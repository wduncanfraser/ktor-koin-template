package com.example.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.redisModule(): Module {
    val redisConfig: RedisConfig = property("redis")
    return redisModule(redisConfig)
}

fun redisModule(redisConfig: RedisConfig) = module {
    single<RedisClient> { buildRedisClient(redisConfig) }
    single<StatefulRedisConnection<String, String>> { get<RedisClient>().connect() }
}

@Serializable
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String? = null,
)

fun buildRedisClient(redisConfig: RedisConfig): RedisClient {
    val redisUri = RedisURI.Builder
        .redis(redisConfig.host, redisConfig.port)
        .apply {
            redisConfig.password?.let {
                withPassword(it.toCharArray())
            }
        }
        .build()

    return RedisClient.create(redisUri).apply {
        options = ClientOptions.builder()
            .autoReconnect(true)
            .pingBeforeActivateConnection(true)
            .build()
    }
}
