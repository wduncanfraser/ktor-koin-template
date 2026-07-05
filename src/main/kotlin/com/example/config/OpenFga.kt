package com.example.config

import com.example.core.authorization.AuthorizationService
import com.example.core.authorization.OpenFgaAuthorizationService
import io.ktor.server.application.Application
import io.ktor.server.config.property
import kotlinx.serialization.Serializable
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.openFgaModule(): Module {
    val config: OpenFgaConfig = property("openfga")
    return openFgaModule(config)
}

fun openFgaModule(config: OpenFgaConfig) = module {
    single<AuthorizationService> { OpenFgaAuthorizationService(config) }
}

@Serializable
data class OpenFgaConfig(
    val apiUrl: String,
    val storeName: String,
)
