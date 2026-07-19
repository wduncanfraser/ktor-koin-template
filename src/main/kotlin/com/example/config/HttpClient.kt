package com.example.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val httpClientModule = module {
    single<HttpClient> {
        val openTelemetry = get<OpenTelemetry>()
        HttpClient(Apache5) {
            // Traces outbound calls (e.g. Discord OAuth) and propagates trace context downstream.
            install(KtorClientTelemetry) {
                setOpenTelemetry(openTelemetry)
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
