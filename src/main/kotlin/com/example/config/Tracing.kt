package com.example.config

import com.example.module
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.koin.dsl.module
import org.koin.ktor.ext.inject

/**
 * Instrumentation scope name for spans this application creates by hand (e.g. OpenFGA calls).
 */
const val TRACER_INSTRUMENTATION_SCOPE = "com.example"

/**
 * OpenTelemetry tracing.
 *
 * The [OpenTelemetry] instance is built by the SDK autoconfigure module, so exporter, endpoint,
 * service name, sampler, etc. are all driven by the standard `OTEL_*` environment variables (see
 * README). The library instrumentation ([KtorServerTelemetry], the Ktor client plugin,
 * `R2dbcTelemetry`, `LettuceTelemetry`) and the hand-rolled OpenFGA spans all consume this single
 * injected instance.
 *
 * **Span export is off by default** — this template ships no tracing backend, so spans are created
 * but dropped until you opt in with `OTEL_TRACES_EXPORTER=otlp` and `OTEL_EXPORTER_OTLP_ENDPOINT`
 * pointing at your collector/backend.
 *
 * The result is intentionally *not* registered as the JVM-global instance: it's injected via Koin
 * everywhere it's needed, which also lets integration tests substitute a no-op instance without a
 * global-already-set conflict.
 */
val tracingModule = module {
    single<OpenTelemetry> {
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier {
                // Defaults, overridden by any OTEL_* env var / system property. Export is off by
                // default (no backend shipped); metrics stay on Micrometer/Prometheus, logs on
                // Logback. Set OTEL_TRACES_EXPORTER=otlp + OTEL_EXPORTER_OTLP_ENDPOINT to emit traces.
                mapOf(
                    "otel.service.name" to "ktor-koin-template",
                    "otel.traces.exporter" to "none",
                    "otel.metrics.exporter" to "none",
                    "otel.logs.exporter" to "none",
                )
            }
            .build()
            .openTelemetrySdk
    }
    single<Tracer> { get<OpenTelemetry>().getTracer(TRACER_INSTRUMENTATION_SCOPE) }
}

/**
 * Installs server-side request tracing. Must be installed before other logging/telemetry plugins
 * (see [Application.module]) so their work is captured within the request span.
 */
fun Application.configureTracing() {
    val openTelemetry by inject<OpenTelemetry>()
    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
    }
}
