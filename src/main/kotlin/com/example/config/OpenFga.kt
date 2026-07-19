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
    single<AuthorizationService> { OpenFgaAuthorizationService(config, get()) }
}

/**
 * [storeId], when set, is used directly and is the recommended way to point at a store: OpenFGA
 * store *names* are not unique, so resolving by [storeName] (the convenient default for local/dev
 * and tests, where provisioning creates exactly one `todo` store) picks an arbitrary match if
 * duplicates exist. Prefer setting `OPENFGA_STOREID` in any shared environment.
 */
@Serializable
data class OpenFgaConfig(
    val apiUrl: String,
    val storeName: String,
    val storeId: String? = null,
)
