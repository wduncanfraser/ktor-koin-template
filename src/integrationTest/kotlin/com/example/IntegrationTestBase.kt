package com.example

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile

abstract class IntegrationTestBase(body: FunSpec.() -> Unit = {}): FunSpec(body) {
    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        postgres.start()
        dbmate.start()
        dbmate.stop()
        valkey.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        valkey.stop()
        postgres.stop()
    }

    companion object {
        val testNetwork: Network = Network.newNetwork()

        val postgres = PostgreSQLContainer("postgres:17-alpine").apply {
            withNetwork(testNetwork)
            withNetworkAliases("db")
            withDatabaseName("todo")
            withUsername("todo")
            withPassword("todo")
        }

        val valkey = GenericContainer("valkey/valkey:8.1-alpine").apply {
            withExposedPorts(6379)
            waitingFor(Wait.forListeningPort())
        }

        // Run dbmate migrations using the internal docker network address
        val dbmate = GenericContainer("ghcr.io/amacneil/dbmate:2.30").apply {
            withNetwork(testNetwork)
            withEnv("DATABASE_URL", "postgresql://db:5432/todo?sslmode=disable")
            withEnv("PGUSER", postgres.username)
            withEnv("PGPASSWORD", postgres.password)
            withCopyToContainer(
                MountableFile.forHostPath("${System.getProperty("user.dir")}/db"),
                "/db",
            )
            withCommand("--wait", "--wait-timeout", "10s", "migrate", "--strict")
            waitingFor(Wait.forLogMessage(".*Applied.*", 1))
        }

        fun withTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
            testApplication {
                application {
                    integrationTestModule(
                        jdbcUrl = postgres.jdbcUrl,
                        dbUser = postgres.username,
                        dbPassword = postgres.password,
                        redisHost = valkey.host,
                        redisPort = valkey.getMappedPort(6379),
                    )
                }

                block()
            }
        }

        fun ApplicationTestBuilder.createTestClient(
            configure: HttpClientConfig<*>.() -> Unit = {}
        ): HttpClient {
            return createClient {
                install(ContentNegotiation) { json() }

                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }

                // Apply additional configuration
                configure()
            }
        }
    }
}
