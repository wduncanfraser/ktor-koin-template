package com.example

import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

class TodoControllerIntegrationTest : FunSpec({
    val network = Network.newNetwork()

    val postgres = PostgreSQLContainer("postgres:17-alpine").apply {
        withNetwork(network)
        withNetworkAliases("db")
        withDatabaseName("todo")
        withUsername("todo")
        withPassword("todo")
    }

    val valkey = GenericContainer("valkey/valkey:8.1-alpine").apply {
        withExposedPorts(6379)
        waitingFor(Wait.forListeningPort())
    }

    beforeSpec {
        postgres.start()

        // Run dbmate migrations using the internal docker network address
        val dbmate = GenericContainer("ghcr.io/amacneil/dbmate:2.30").apply {
            withNetwork(network)
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
        dbmate.start()
        dbmate.stop()

        valkey.start()
    }

    afterSpec {
        valkey.stop()
        postgres.stop()
    }

    beforeEach {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().execute("TRUNCATE TABLE todo")
        }
    }

    test("POST /api/v1/todos creates a todo") {
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

            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/apiunit/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Test todo"))
            }

            response.status shouldBe HttpStatusCode.OK
            val todo = response.body<TodoResponseContract>()
            todo.name shouldBe "Test todo"
            todo.completed shouldBe false
            todo.completedAt shouldBe null
            todo.id shouldNotBe null
            todo.createdAt shouldNotBe null
            todo.updatedAt shouldNotBe null
        }
    }

    test("GET /api/v1/todos returns empty list when no todos exist") {
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

            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.get("/api/v1/todos")

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 0
        }
    }

    test("GET /api/v1/todos returns previously created todos") {
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

            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "First"))
            }
            client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Second"))
            }

            val response = client.get("/api/v1/todos")

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 2
        }
    }

    test("PUT /api/v1/todos/{id} updates name and marks complete") {
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

            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val created = client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Original"))
            }.body<TodoResponseContract>()

            val response = client.put("/api/v1/todos/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Updated", completed = true))
            }

            response.status shouldBe HttpStatusCode.OK
            val updated = response.body<TodoResponseContract>()
            updated.id shouldBe created.id
            updated.name shouldBe "Updated"
            updated.completed shouldBe true
            updated.completedAt shouldNotBe null
        }
    }

    test("DELETE /api/v1/todos/{id} removes a todo") {
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

            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val created = client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "To delete"))
            }.body<TodoResponseContract>()

            val deleteResponse = client.delete("/api/v1/todos/${created.id}")
            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val listResponse = client.get("/api/v1/todos")
            val body = listResponse.body<ListTodosResponseContract>()
            body.data shouldHaveSize 0
        }
    }
})
