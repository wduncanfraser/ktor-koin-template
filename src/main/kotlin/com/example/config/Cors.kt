package com.example.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.property
import io.ktor.server.plugins.cors.CORSConfig
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.serialization.Serializable

fun Application.configureCors() {
    val corsConfig: CorsConfig = property("cors")
    configureCors(corsConfig)
}

fun Application.configureCors(corsConfig: CorsConfig) {
    val allowedHosts = corsConfig.allowedHosts.split(",").map { it.trim() }
    install(CORS) {
        applyCorsRules(allowedHosts)
    }
}

private fun CORSConfig.applyCorsRules(allowedHosts: List<String>) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowedHosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
}

@Serializable
data class CorsConfig(val allowedHosts: String)
