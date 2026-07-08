package com.example

import com.example.authn.RedisSessionStorage
import com.example.authn.UserSession
import com.example.config.AuthenticationConfig
import com.example.config.CorsConfig
import com.example.config.DatabaseConfig
import com.example.config.OAuthConfig
import com.example.config.OpenFgaConfig
import com.example.config.RedisConfig
import dev.openfga.sdk.api.client.OpenFgaClient
import dev.openfga.sdk.api.client.model.ClientReadRequest
import dev.openfga.sdk.api.configuration.ClientConfiguration
import dev.openfga.sdk.api.configuration.ClientReadOptions
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
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
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
import kotlin.time.Duration.Companion.days

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
                    url = "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(POSTGRESQL_PORT)}" +
                        "/${postgres.databaseName}",
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
                val openFgaConfig = OpenFgaConfig(
                    apiUrl = "http://${openfga.host}:${openfga.getMappedPort(OPENFGA_PORT)}",
                    storeName = "todo",
                )
                integrationTestModule(
                    databaseConfig = databaseConfig,
                    redisConfig = redisConfig,
                    openFgaConfig = openFgaConfig,
                    authConfig = authConfig,
                    corsConfig = CorsConfig(allowedHosts = "localhost:5173"),
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
        val transformer = SessionTransportTransformerMessageAuthentication(TEST_SESSION_SIGNING_KEY.hexToByteArray())
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

    /** Every stored tuple where [objectRef] (e.g. `"todo_list:<id>"`) is the object, as `"user|relation|object"`. */
    suspend fun fgaTuplesWithObject(objectRef: String): List<String> =
        readFga(ClientReadRequest()._object(objectRef))

    /** Every stored tuple linking a `[childType]:` object to [userRef] via [relation] (e.g. parent links). */
    suspend fun fgaTuplesWithUser(childType: String, userRef: String, relation: String): List<String> =
        readFga(ClientReadRequest()._object("$childType:").user(userRef).relation(relation))

    private suspend fun readFga(request: ClientReadRequest): List<String> {
        val client = openFgaClient()
        val results = mutableListOf<String>()
        var token: String? = null
        do {
            val options = ClientReadOptions().apply { token?.let { continuationToken(it) } }
            val response = client.read(request, options).await()
            response.tuples.forEach { results.add("${it.key.user}|${it.key.relation}|${it.key.getObject()}") }
            token = response.continuationToken.takeIf { it.isNotBlank() }
        } while (token != null)
        return results
    }

    private suspend fun openFgaClient(): OpenFgaClient {
        fgaClient?.let { return it }
        val config = ClientConfiguration().apiUrl("http://${openfga.host}:${openfga.getMappedPort(OPENFGA_PORT)}")
        val client = OpenFgaClient(config)
        val storeId = client.listStores().await().stores.firstOrNull { it.name == "todo" }?.id
            ?: error("OpenFGA store 'todo' not found")
        config.storeId(storeId)
        return client.also { fgaClient = it }
    }

    private var fgaClient: OpenFgaClient? = null

    companion object {
        private val DATABASE_TABLES = listOf("todo", "todo_list")
        private const val REDIS_PORT = 6379
        private const val OPENFGA_PORT = 8080
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

        val openfga = GenericContainer("openfga/openfga:latest").apply {
            withNetwork(testNetwork)
            withNetworkAliases("openfga")
            withCommand("run")
            withExposedPorts(OPENFGA_PORT)
            waitingFor(Wait.forHttp("/healthz").forPort(OPENFGA_PORT))
        }

        val fgaProvision = GenericContainer("openfga/cli:latest").apply {
            withNetwork(testNetwork)
            withCopyToContainer(
                MountableFile.forHostPath("${System.getProperty("user.dir")}/fga"),
                "/model",
            )
            withCommand(
                "store", "create",
                "--api-url", "http://openfga:8080",
                "--name", "todo",
                "--model", "/model/authorization-model.fga",
            )
            withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(30)))
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
            expiration = Clock.System.now().plus(7.days),
        )

        fun secondTestSession() = UserSession(
            userId = "test-user-id-2",
            accessToken = "test-access-token-2",
            refreshToken = null,
            expiration = Clock.System.now().plus(7.days),
        )
    }
}
