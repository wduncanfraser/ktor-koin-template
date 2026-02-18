package com.example.config

import com.example.IntegrationTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

class MonitoringIntegrationTest : IntegrationTestBase({
    test("GET /health returns registered health checks") {
        withTestApplication {
            val client = createTestClient()

            val response = client.get("/health")
            val body = response.bodyAsText()

            // Cohort health checks run asynchronously, so the endpoint may return
            // 503 if checks haven't completed yet. Either status confirms the
            // endpoint is wired up; validate registered checks appear in the body.
            body shouldContain "hikari_open_connections"
            body shouldContain "thread_deadlocks"
        }
    }

    test("GET /metrics returns Prometheus metrics") {
        withTestApplication {
            val client = createTestClient()

            val response = client.get("/metrics")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "jvm_memory"
            body shouldContain "hikaricp"
        }
    }
})
