package com.example

import com.example.authn.RedisSessionStorage
import com.example.authn.UserSession
import com.example.config.DatabaseConfig
import com.example.config.RedisConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.hex
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.utility.MountableFile
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

abstract class IntegrationTestBase(body: FunSpec.() -> Unit = {}): FunSpec(body) {
    override suspend fun beforeEach(testCase: TestCase) {
        super.beforeEach(testCase)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().execute("TRUNCATE TABLE ${DATABASE_TABLES.joinToString(", ")}")
        }
    }

    companion object {
        private val DATABASE_TABLES = listOf("todo")
        private const val REDIS_PORT = 6379
        private const val TEST_SESSION_SIGNING_KEY_HEX = "0101010101010101010101010101010101010101010101010101010101010101"
        const val TEST_SESSION_COOKIE_NAME = "test-session-cookie"

        val testNetwork: Network = Network.newNetwork()

        val postgres = PostgreSQLContainer("postgres:17-alpine").apply {
            withNetwork(testNetwork)
            withNetworkAliases("db")
            withDatabaseName("todo")
            withUsername("todo")
            withPassword("todo")
        }

        val valkey = GenericContainer("valkey/valkey:8.1-alpine").apply {
            withExposedPorts(REDIS_PORT)
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
            withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(5)))
        }

        fun defaultTestSession() = UserSession(
            userId = "test-user-id",
            accessToken = "test-access-token",
            refreshToken = null,
            expiration = Clock.System.now().plus(24.hours),
        )

        fun withTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
            testApplication {
                application {
                    val databaseConfig = DatabaseConfig(
                        url = "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(POSTGRESQL_PORT)}/${postgres.databaseName}",
                        user = postgres.username,
                        password = postgres.password,
                        poolSize = 5,
                    )

                    val redisConfig = RedisConfig(
                        host = valkey.host,
                        port = valkey.getMappedPort(REDIS_PORT),
                    )
                    integrationTestModule(
                        databaseConfig = databaseConfig,
                        redisConfig = redisConfig,
                        sessionCookieName = TEST_SESSION_COOKIE_NAME,
                        sessionSigningKey = TEST_SESSION_SIGNING_KEY_HEX,
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

        suspend fun ApplicationTestBuilder.createAuthenticatedTestClient(
            session: UserSession = defaultTestSession(),
            configure: HttpClientConfig<*>.() -> Unit = {}
        ): HttpClient {
            startApplication()
            val redisConnection = application.get<StatefulRedisConnection<String, String>>()
            val storage = RedisSessionStorage(redisConnection)
            val sessionId = UUID.randomUUID().toString()
            storage.write(sessionId, Json.encodeToString(session))
            val transformer = SessionTransportTransformerMessageAuthentication(hex(TEST_SESSION_SIGNING_KEY_HEX))
            val signedCookieValue = transformer.transformWrite(sessionId)
            return createClient {
                install(ContentNegotiation) { json() }

                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }

                defaultRequest {
                    header(HttpHeaders.Cookie, "$TEST_SESSION_COOKIE_NAME=$signedCookieValue")
                }

                configure()
            }
        }
    }
}
