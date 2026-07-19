package com.example

import com.example.config.TRACER_INSTRUMENTATION_SCOPE
import com.example.generated.api.models.CreateTodoListRequestContract
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies distributed tracing produces one connected trace per request — the point being that
 * spans emitted from the reactive DB layer, the Lettuce client, and the OpenFGA calls all nest
 * under the inbound HTTP server span rather than surfacing as orphaned roots. This is the
 * coroutine/reactive context-propagation guarantee that's easy to break, so it's asserted here.
 */
class TracingIntegrationTest : IntegrationTestBase() {
    private val spanExporter = InMemorySpanExporter.create()

    override val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                // Synchronous export so finished spans are readable immediately after the request.
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build(),
        )
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    init {
        test("a request produces one trace spanning the HTTP server, OpenFGA, and the database") {
            val client = createAuthenticatedTestClient()
            // Reset after session setup (which writes to Redis outside any request) so only the
            // request's spans are asserted — also drops startup spans like the DB warmup query.
            spanExporter.reset()

            val response = client.post("/api/v1/todo-lists") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Traced List"))
            }
            response.status shouldBe HttpStatusCode.Created

            // The server span ends once the response is fully sent, which may trail the client call.
            eventually(2.seconds) {
                spanExporter.finishedSpanItems.count { it.kind == SpanKind.SERVER } shouldBe 1
            }

            val spans = spanExporter.finishedSpanItems
            spans.shouldNotBeEmpty()
            val serverSpans = spans.filter { it.kind == SpanKind.SERVER }
            serverSpans shouldHaveSize 1
            val server = serverSpans.single()

            // Reactive boundary: every span landed under the one request trace.
            spans.map { it.traceId }.toSet() shouldBe setOf(server.traceId)

            // The downstream layers each contributed spans.
            val scopes = spans.map { it.instrumentationScopeInfo.name }.toSet()
            scopes shouldContain "io.opentelemetry.r2dbc-1.0"  // database queries
            scopes shouldContain TRACER_INSTRUMENTATION_SCOPE  // hand-rolled OpenFGA spans

            // Proper nesting: no orphaned roots — every other span descends from the server span.
            val byId = spans.associateBy { it.spanId }
            spans.filter { it.spanId != server.spanId }.forEach { span ->
                withClue("${span.name} [${span.instrumentationScopeInfo.name}] should descend from the server span") {
                    span.descendsFrom(server.spanId, byId) shouldBe true
                }
            }
        }

        test("two separate requests get two separate traces (no cross-request context leak)") {
            val client = createAuthenticatedTestClient()
            spanExporter.reset()

            client.post("/api/v1/todo-lists") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "First"))
            }.status shouldBe HttpStatusCode.Created
            client.post("/api/v1/todo-lists") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Second"))
            }.status shouldBe HttpStatusCode.Created

            eventually(2.seconds) {
                spanExporter.finishedSpanItems.count { it.kind == SpanKind.SERVER } shouldBe 2
            }

            val serverTraceIds = spanExporter.finishedSpanItems
                .filter { it.kind == SpanKind.SERVER }
                .map { it.traceId }
            serverTraceIds.toSet() shouldHaveSize 2
        }
    }
}

private fun SpanData.descendsFrom(ancestorSpanId: String, byId: Map<String, SpanData>): Boolean {
    var current: SpanData? = this
    while (current != null && current.spanId != ancestorSpanId) {
        current = byId[current.parentSpanId]
    }
    return current != null
}
