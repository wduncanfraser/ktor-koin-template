package com.example.todo.api

import com.example.IntegrationTestBase
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.ProblemDetailsContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID

class TodoControllerIntegrationTest : IntegrationTestBase({
    test("POST /api/v1/todos creates a todo") {
        withTestApplication {
            val client = createTestClient()

            val response = client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Test todo"))
            }

            response.status shouldBe HttpStatusCode.OK
            val todo = response.body<TodoResponseContract>()
            assertSoftly(todo) {
                name shouldBe "Test todo"
                completed shouldBe false
                completedAt shouldBe null
                id shouldNotBe null
                createdAt shouldNotBe null
                updatedAt shouldNotBe null
            }
        }
    }

    test("GET /api/v1/todos returns empty list when no todos exist") {
        withTestApplication {
            val client = createTestClient()

            val response = client.get("/api/v1/todos")

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 0
        }
    }

    test("GET /api/v1/todos returns previously created todos") {
        withTestApplication {
            val client = createTestClient()

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
        withTestApplication {
            val client = createTestClient()

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
            assertSoftly(updated) {
                id shouldBe created.id
                name shouldBe "Updated"
                completed shouldBe true
                completedAt shouldNotBe null
            }
        }
    }

    test("DELETE /api/v1/todos/{id} removes a todo") {
        withTestApplication {
            val client = createTestClient()

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

    test("GET /api/v1/todos?completed=true returns only completed todos") {
        withTestApplication {
            val client = createTestClient()

            val created = client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Task"))
            }.body<TodoResponseContract>()
            client.put("/api/v1/todos/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Task", completed = true))
            }
            client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Incomplete task"))
            }

            val response = client.get("/api/v1/todos") {
                parameter("completed", "true")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 1
            body.data[0].name shouldBe "Task"
        }
    }

    test("GET /api/v1/todos?completed=false returns only incomplete todos") {
        withTestApplication {
            val client = createTestClient()

            val created = client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Completed task"))
            }.body<TodoResponseContract>()
            client.put("/api/v1/todos/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Completed task", completed = true))
            }
            client.post("/api/v1/todos") {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Pending task"))
            }

            val response = client.get("/api/v1/todos") {
                parameter("completed", "false")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 1
            body.data[0].name shouldBe "Pending task"
        }
    }

    test("GET /api/v1/todos?pageSize=1 returns one item with correct pagination metadata") {
        withTestApplication {
            val client = createTestClient()

            repeat(3) { i ->
                client.post("/api/v1/todos") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Todo $i"))
                }
            }

            val response = client.get("/api/v1/todos") {
                parameter("pageSize", "1")
                parameter("page", "1")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            assertSoftly(body) {
                data shouldHaveSize 1
                pagination.pageSize shouldBe 1
                pagination.page shouldBe 1
                pagination.totalRows shouldBe 3
                pagination.totalPages shouldBe 3
            }
        }
    }

    test("PUT /api/v1/todos/{id} returns 404 when todo does not exist") {
        withTestApplication {
            val client = createTestClient()
            val id = UUID.randomUUID()

            val response = client.put("/api/v1/todos/$id") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Updated", completed = false))
            }

            response.status shouldBe HttpStatusCode.NotFound
            val problem = response.body<ProblemDetailsContract>()
            assertSoftly(problem) {
                type shouldBe "https://example.com/errors/not-found"
                status shouldBe HttpStatusCode.NotFound.value
            }
        }
    }

    test("DELETE /api/v1/todos/{id} returns 404 when todo does not exist") {
        withTestApplication {
            val client = createTestClient()
            val id = UUID.randomUUID()

            val response = client.delete("/api/v1/todos/$id")

            response.status shouldBe HttpStatusCode.NotFound
            val problem = response.body<ProblemDetailsContract>()
            assertSoftly(problem) {
                type shouldBe "https://example.com/errors/not-found"
                status shouldBe HttpStatusCode.NotFound.value
            }
        }
    }
})
