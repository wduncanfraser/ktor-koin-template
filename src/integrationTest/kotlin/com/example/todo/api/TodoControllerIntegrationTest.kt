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
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.util.UUID

class TodoControllerIntegrationTest : IntegrationTestBase({
    context("POST /api/v1/todos") {
        test("creates a todo") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Test todo"))
            }

            response.status shouldBe HttpStatusCode.Created
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

        test("returns 422 for empty name") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = ""))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val problem = response.body<ProblemDetailsContract>()
            problem.status shouldBe HttpStatusCode.UnprocessableEntity.value
        }

        test("returns 422 for name exceeding max length") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "a".repeat(256)))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val problem = response.body<ProblemDetailsContract>()
            problem.status shouldBe HttpStatusCode.UnprocessableEntity.value
        }

        test("returns 302 for login redirect without a session cookie") {
            val client = createTestClient { followRedirects = false }

            val response = client.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Test todo"))
            }

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    context("GET /api/v1/todos") {
        test("returns empty list when no todos exist") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todosUrl())

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 0
        }

        test("returns previously created todos") {
            val client = createAuthenticatedTestClient()

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

        test("completed=true returns only completed todos") {
            val client = createAuthenticatedTestClient()

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

        test("completed=false returns only incomplete todos") {
            val client = createAuthenticatedTestClient()

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

        test("pageSize=1 returns one item with correct pagination metadata") {
            val client = createAuthenticatedTestClient()

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

        test("returns 400 when page is not an integer") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todosUrl()) {
                parameter("page", "xyz")
            }

            response.status shouldBe HttpStatusCode.BadRequest
            val problem = response.body<ProblemDetailsContract>()
            problem.status shouldBe HttpStatusCode.BadRequest.value
        }
    }

    context("GET /api/v1/todos/{id}") {
        test("returns a todo by id") {
            val client = createAuthenticatedTestClient()

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

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()
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

    context("PUT /api/v1/todos/{id}") {
        test("updates name and marks complete") {
            val client = createAuthenticatedTestClient()

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

        test("returns 422 for empty name") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Original"))
            }.body<TodoResponseContract>()

            val response = client.put(todoUrl(created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "", completed = false))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val problem = response.body<ProblemDetailsContract>()
            problem.status shouldBe HttpStatusCode.UnprocessableEntity.value
        }

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()
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

    context("DELETE /api/v1/todos/{id}") {
        test("removes a todo") {
            val client = createAuthenticatedTestClient()

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

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()
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
    context("user isolation") {
        test("listTodos only returns todos belonging to the authenticated user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            clientA.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A - First"))
            }
            clientA.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A - Second"))
            }
            clientB.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User B - First"))
            }

            val responseA = clientA.get(todosUrl())
            val responseB = clientB.get(todosUrl())

            responseA.status shouldBe HttpStatusCode.OK
            responseB.status shouldBe HttpStatusCode.OK
            responseA.body<ListTodosResponseContract>().data shouldHaveSize 2
            responseB.body<ListTodosResponseContract>().data shouldHaveSize 1
        }

        test("getTodo returns 404 for a todo belonging to another user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val created = clientA.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A todo"))
            }.body<TodoResponseContract>()

            val response = clientB.get(todoUrl(created.id))

            response.status shouldBe HttpStatusCode.NotFound
        }

        test("updateTodo returns 404 for a todo belonging to another user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val created = clientA.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A todo"))
            }.body<TodoResponseContract>()

            val response = clientB.put(todoUrl(created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Updated", completed = true))
            }

            response.status shouldBe HttpStatusCode.NotFound
        }

        test("deleteTodo returns 404 for a todo belonging to another user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val created = clientA.post(todosUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A todo"))
            }.body<TodoResponseContract>()

            val deleteResponse = clientB.delete(todoUrl(created.id))
            deleteResponse.status shouldBe HttpStatusCode.NotFound

            val getResponse = clientA.get(todoUrl(created.id))
            getResponse.status shouldBe HttpStatusCode.OK
        }
    }
}) {
    companion object {
        const val TODOS_URL = "/api/v1/todos"
        fun todosUrl() = TODOS_URL
        fun todoUrl(id: Any) = "$TODOS_URL/$id"
    }
}
