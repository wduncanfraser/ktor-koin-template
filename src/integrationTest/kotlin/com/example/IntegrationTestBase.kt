package com.example

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.sql.Connection
import java.sql.DriverManager

abstract class IntegrationTestBase(body: FunSpec.() -> Unit = {}): FunSpec(body) {
    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        postgres.start()
        dbmate.start()
        dbmate.stop()
        valkey.start()

        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
    }

    override suspend fun beforeEach(testCase: TestCase) {
        super.beforeEach(testCase)
        val conn = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        conn.autoCommit = false
        testConnection = conn

        // Wrap connection to suppress commits — all changes are rolled back in afterEach
        val nonCommitting = NonCommittingConnection(conn)
        val config = DefaultConfiguration().apply {
            set(nonCommitting)
            setSQLDialect(SQLDialect.POSTGRES)
            setExecutorProvider { Dispatchers.IO.asExecutor() }
        }
        testDslContext = DSL.using(config)
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        testConnection?.rollback()
        testConnection?.close()
        testConnection = null
        testDslContext = null
        super.afterEach(testCase, result)
    }

    override suspend fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        valkey.stop()
        postgres.stop()
    }

    companion object {
        private var testConnection: Connection? = null
        private var testDslContext: org.jooq.DSLContext? = null

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
            val dslContext = testDslContext
                ?: error("testDslContext not initialized — is beforeEach running?")
            testApplication {
                application {
                    integrationTestModule(
                        dslContext = dslContext,
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
