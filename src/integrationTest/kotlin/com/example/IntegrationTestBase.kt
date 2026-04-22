package com.example

import com.example.authn.RedisSessionStorage
import com.example.authn.UserSession
import com.example.config.AuthenticationConfig
import com.example.config.DatabaseConfig
import com.example.config.OAuthConfig
import com.example.config.RedisConfig
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.util.*
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

abstract class IntegrationTestBase(body: IntegrationTestBase.() -> Unit = {}) : FunSpec() {
    private lateinit var testApp: TestApplication

    val application: Application get() = testApp.application

    init {
        body()
    }

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        testApp = TestApplication {
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
                val authConfig = AuthenticationConfig(
                    sessionCookieName = TEST_SESSION_COOKIE_NAME,
                    sessionSigningKey = TEST_SESSION_SIGNING_KEY,
                    oAuth = OAuthConfig(
                        callbackUrl = "",
                        clientId = "",
                        clientSecret = "",
                        postLoginRedirectUrl = ""
                    )
                )
                integrationTestModule(
                    databaseConfig = databaseConfig,
                    redisConfig = redisConfig,
                    authConfig = authConfig,
                )
            }
        }
        testApp.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        if (::testApp.isInitialized) testApp.stop()
    }

    override suspend fun beforeEach(testCase: TestCase) {
        super.beforeEach(testCase)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().execute("TRUNCATE TABLE ${DATABASE_TABLES.joinToString(", ")}")
        }
    }

    fun createTestClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
        return testApp.createClient {
            install(ContentNegotiation) { json() }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            configure()
        }
    }

    suspend fun createAuthenticatedTestClient(
        session: UserSession = defaultTestSession(),
        configure: HttpClientConfig<*>.() -> Unit = {}
    ): HttpClient {
        val redisConnection = testApp.application.get<StatefulRedisConnection<String, String>>()
        val storage = RedisSessionStorage(redisConnection)
        val sessionId = UUID.randomUUID().toString()
        storage.write(sessionId, Json.encodeToString(session))
        val transformer = SessionTransportTransformerMessageAuthentication(hex(TEST_SESSION_SIGNING_KEY))
        val signedCookieValue = transformer.transformWrite(sessionId)
        return testApp.createClient {
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

    companion object {
        private val DATABASE_TABLES = listOf("todo")
        private const val REDIS_PORT = 6379
        private const val TEST_SESSION_SIGNING_KEY = "0101010101010101010101010101010101010101010101010101010101010101"
        const val TEST_SESSION_COOKIE_NAME = "test-session-cookie"

        val testNetwork: Network = Network.newNetwork()

        val postgres = PostgreSQLContainer("postgres:18-alpine").apply {
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

        fun secondTestSession() = UserSession(
            userId = "test-user-id-2",
            accessToken = "test-access-token-2",
            refreshToken = null,
            expiration = Clock.System.now().plus(24.hours),
        )
    }
}
