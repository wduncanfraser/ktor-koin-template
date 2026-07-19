package com.example

import com.example.config.CorsConfig
import com.example.generated.api.models.CreateTodoListRequestContract
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.lettuce.core.api.StatefulRedisConnection
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.koin.ktor.ext.get
import kotlin.time.Duration.Companion.seconds

/**
 * Guards the KTOR-6802 workaround (`-Dio.ktor.internal.disable.sfg=true`, set in build.gradle.kts /
 * Dockerfile). On the real Netty engine, Ktor's SuspendFunctionGun leaks the coroutine ThreadLocal,
 * so a later request handled by a pooled worker thread inherits the previous request's OTel span,
 * gets its own SERVER span suppressed, and is merged into the wrong trace. Two independent
 * authenticated requests must land in two independent traces, each with its own nested spans.
 *
 * This uses the real Netty engine on purpose — the in-memory test engine doesn't reuse the pooled
 * event-loop/R2DBC threads where the leak surfaces. If the workaround regresses (flag removed, or a
 * future Ktor stops honouring it), this test fails with only one SERVER span for two requests.
 *
 * Container-derived config and auth come from [IntegrationTestBase]'s shared factories so this test
 * can't drift from the rest of the suite; it can't extend [IntegrationTestBase] because that harness
 * is built on the in-memory `testApplication` engine, which doesn't reproduce the leak.
 */
class TracingLeakNettyTest : FunSpec({
    val spanExporter = InMemorySpanExporter.create()
    val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    test("two independent requests over Netty land in two independent traces") {
        val server = embeddedServer(Netty, port = 0) {
            integrationTestModule(
                databaseConfig = IntegrationTestBase.databaseConfig(),
                redisConfig = IntegrationTestBase.redisConfig(),
                openFgaConfig = IntegrationTestBase.openFgaConfig(),
                authConfig = IntegrationTestBase.testAuthConfig(),
                corsConfig = CorsConfig(allowedHosts = "localhost:5173"),
                openTelemetry = openTelemetry,
            )
        }
        server.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port

            val redis = server.application.get<StatefulRedisConnection<String, String>>()
            val cookie = IntegrationTestBase.writeSignedSessionCookie(redis)
            val cookieHeader = "${IntegrationTestBase.TEST_SESSION_COOKIE_NAME}=$cookie"

            val client = HttpClient(Apache5) {
                install(ContentNegotiation) { json() }
            }

            spanExporter.reset()
            client.post("http://localhost:$port/api/v1/todo-lists") {
                header(HttpHeaders.Cookie, cookieHeader)
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "First"))
            }.status shouldBe HttpStatusCode.Created
            client.get("http://localhost:$port/api/v1/todo-lists") {
                header(HttpHeaders.Cookie, cookieHeader)
            }.status shouldBe HttpStatusCode.OK

            eventually(3.seconds) {
                spanExporter.finishedSpanItems.count { it.kind == SpanKind.SERVER } shouldBe 2
            }
            val spans = spanExporter.finishedSpanItems
            val serverSpans = spans.filter { it.kind == SpanKind.SERVER }
            // The two requests are two distinct traces, each its own root (no cross-request leak).
            serverSpans.map { it.traceId }.toSet() shouldHaveSize 2
            serverSpans.forEach { it.parentSpanContext.isValid shouldBe false }
            // And nesting still holds: every client span descends from one of the server spans.
            val serverSpanIds = serverSpans.map { it.spanId }.toSet()
            val byId = spans.associateBy { it.spanId }
            spans.filter { it.kind != SpanKind.SERVER }.forEach { span ->
                span.descendsFromAny(serverSpanIds, byId) shouldBe true
            }

            client.close()
        } finally {
            server.stop()
        }
    }
})

private fun SpanData.descendsFromAny(ancestorSpanIds: Set<String>, byId: Map<String, SpanData>): Boolean {
    var current: SpanData? = this
    while (current != null && current.spanId !in ancestorSpanIds) {
        current = byId[current.parentSpanId]
    }
    return current != null
}
