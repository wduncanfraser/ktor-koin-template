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
    context("POST /api/v1/todos") {
        test("creates a todo") {
            withTestApplication {
                val client = createTestClient()

                val response = client.post(todosUrl()) {
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
    }

    context("GET /api/v1/todos") {
        test("returns empty list when no todos exist") {
            withTestApplication {
                val client = createTestClient()

                val response = client.get(todosUrl())

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ListTodosResponseContract>()
                body.data shouldHaveSize 0
            }
        }

        test("returns previously created todos") {
            withTestApplication {
                val client = createTestClient()

                client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "First"))
                }
                client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Second"))
                }

                val response = client.get(todosUrl())

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ListTodosResponseContract>()
                body.data shouldHaveSize 2
            }
        }

        test("completed=true returns only completed todos") {
            withTestApplication {
                val client = createTestClient()

                val created = client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Task"))
                }.body<TodoResponseContract>()
                client.put(todoUrl(created.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTodoRequestContract(name = "Task", completed = true))
                }
                client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Incomplete task"))
                }

                val response = client.get(todosUrl()) {
                    parameter("completed", "true")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ListTodosResponseContract>()
                body.data shouldHaveSize 1
                body.data[0].name shouldBe "Task"
            }
        }

        test("completed=false returns only incomplete todos") {
            withTestApplication {
                val client = createTestClient()

                val created = client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Completed task"))
                }.body<TodoResponseContract>()
                client.put(todoUrl(created.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTodoRequestContract(name = "Completed task", completed = true))
                }
                client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Pending task"))
                }

                val response = client.get(todosUrl()) {
                    parameter("completed", "false")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<ListTodosResponseContract>()
                body.data shouldHaveSize 1
                body.data[0].name shouldBe "Pending task"
            }
        }

        test("pageSize=1 returns one item with correct pagination metadata") {
            withTestApplication {
                val client = createTestClient()

                repeat(3) { i ->
                    client.post(todosUrl()) {
                        contentType(ContentType.Application.Json)
                        setBody(CreateTodoRequestContract(name = "Todo $i"))
                    }
                }

                val response = client.get(todosUrl()) {
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

        test("returns 400 when page is not an integer") {
            withTestApplication {
                val client = createTestClient()

                val response = client.get(todosUrl()) {
                    parameter("page", "xyz")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val problem = response.body<ProblemDetailsContract>()
                problem.status shouldBe HttpStatusCode.BadRequest.value
            }
        }
    }

    context("GET /api/v1/todos/{id}") {
        test("returns a todo by id") {
            withTestApplication {
                val client = createTestClient()

                val created = client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Test todo"))
                }.body<TodoResponseContract>()

                val response = client.get(todoUrl(created.id))

                response.status shouldBe HttpStatusCode.OK
                val todo = response.body<TodoResponseContract>()
                assertSoftly(todo) {
                    id shouldBe created.id
                    name shouldBe "Test todo"
                    completed shouldBe false
                }
            }
        }

        test("returns 404 when todo does not exist") {
            withTestApplication {
                val client = createTestClient()
                val id = UUID.randomUUID()

                val response = client.get(todoUrl(id))

                response.status shouldBe HttpStatusCode.NotFound
                val problem = response.body<ProblemDetailsContract>()
                assertSoftly(problem) {
                    type shouldBe "https://example.com/errors/not-found"
                    status shouldBe HttpStatusCode.NotFound.value
                }
            }
        }
    }

    context("PUT /api/v1/todos/{id}") {
        test("updates name and marks complete") {
            withTestApplication {
                val client = createTestClient()

                val created = client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Original"))
                }.body<TodoResponseContract>()

                val response = client.put(todoUrl(created.id)) {
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

        test("returns 404 when todo does not exist") {
            withTestApplication {
                val client = createTestClient()
                val id = UUID.randomUUID()

                val response = client.put(todoUrl(id)) {
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
    }

    context("DELETE /api/v1/todos/{id}") {
        test("removes a todo") {
            withTestApplication {
                val client = createTestClient()

                val created = client.post(todosUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "To delete"))
                }.body<TodoResponseContract>()

                val deleteResponse = client.delete(todoUrl(created.id))
                deleteResponse.status shouldBe HttpStatusCode.NoContent

                val listResponse = client.get(todosUrl())
                val body = listResponse.body<ListTodosResponseContract>()
                body.data shouldHaveSize 0
            }
        }

        test("returns 404 when todo does not exist") {
            withTestApplication {
                val client = createTestClient()
                val id = UUID.randomUUID()

                val response = client.delete(todoUrl(id))

                response.status shouldBe HttpStatusCode.NotFound
                val problem = response.body<ProblemDetailsContract>()
                assertSoftly(problem) {
                    type shouldBe "https://example.com/errors/not-found"
                    status shouldBe HttpStatusCode.NotFound.value
                }
            }
        }
    }
}) {
    companion object {
        const val TODOS_URL = "/api/v1/todos"
        fun todosUrl() = TODOS_URL
        fun todoUrl(id: Any) = "$TODOS_URL/$id"
    }
}
