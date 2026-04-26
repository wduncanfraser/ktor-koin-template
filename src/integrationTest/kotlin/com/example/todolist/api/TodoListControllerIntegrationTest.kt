package com.example.todolist.api

import com.example.IntegrationTestBase
import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodoListsResponseContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.ProblemDetailsContract
import com.example.generated.api.models.TodoListResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoListRequestContract
import com.example.generated.api.models.UpdateTodoRequestContract
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.util.UUID

class TodoListControllerIntegrationTest : IntegrationTestBase({
    context("POST /api/v1/todo-lists") {
        test("creates a todo list") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }

            response.status shouldBe HttpStatusCode.Created
            val list = response.body<TodoListResponseContract>()
            assertSoftly(list) {
                name shouldBe "My List"
                description shouldBe null
                createdBy shouldBe "test-user-id"
                id shouldNotBe null
                createdAt shouldNotBe null
                updatedAt shouldNotBe null
            }
        }

        test("creates a todo list with description") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List", description = "A description"))
            }

            response.status shouldBe HttpStatusCode.Created
            val list = response.body<TodoListResponseContract>()
            list.description shouldBe "A description"
        }

        test("returns 422 for empty name") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = ""))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val problem = response.body<ProblemDetailsContract>()
            assertSoftly(problem) {
                status shouldBe HttpStatusCode.UnprocessableEntity.value
                errors?.get("name") shouldNotBe null
            }
        }

        test("returns 302 for login redirect without a session cookie") {
            val client = createTestClient { followRedirects = false }

            val response = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }
    }

    context("GET /api/v1/todo-lists") {
        test("returns empty list when no todo lists exist") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todoListsUrl())

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodoListsResponseContract>()
            body.data shouldHaveSize 0
        }

        test("returns previously created todo lists") {
            val client = createAuthenticatedTestClient()

            client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "First"))
            }
            client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Second"))
            }

            val response = client.get(todoListsUrl())

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodoListsResponseContract>()
            body.data shouldHaveSize 2
        }

        test("pageSize=1 returns one item with correct pagination metadata") {
            val client = createAuthenticatedTestClient()

            repeat(3) { i ->
                client.post(todoListsUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoListRequestContract(name = "List $i"))
                }
            }

            val response = client.get(todoListsUrl()) {
                parameter("pageSize", "1")
                parameter("page", "1")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodoListsResponseContract>()
            assertSoftly(body) {
                data shouldHaveSize 1
                pagination.pageSize shouldBe 1
                pagination.page shouldBe 1
                pagination.totalRows shouldBe 3
                pagination.totalPages shouldBe 3
            }
        }
    }

    context("GET /api/v1/todo-lists/{list-id}") {
        test("returns a todo list by id") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.get(todoListUrl(created.id))

            response.status shouldBe HttpStatusCode.OK
            val list = response.body<TodoListResponseContract>()
            assertSoftly(list) {
                id shouldBe created.id
                name shouldBe "My List"
            }
        }

        test("returns 404 when todo list does not exist") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todoListUrl(UUID.randomUUID()))

            response.status shouldBe HttpStatusCode.NotFound
            val problem = response.body<ProblemDetailsContract>()
            assertSoftly(problem) {
                type shouldBe "https://example.com/errors/not-found"
                status shouldBe HttpStatusCode.NotFound.value
            }
        }

        test("returns 404 for another user's todo list") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val created = clientA.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User A list"))
            }.body<TodoListResponseContract>()

            val response = clientB.get(todoListUrl(created.id))

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("PUT /api/v1/todo-lists/{list-id}") {
        test("updates name") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Original"))
            }.body<TodoListResponseContract>()

            val response = client.put(todoListUrl(created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoListRequestContract(name = "Updated"))
            }

            response.status shouldBe HttpStatusCode.OK
            val updated = response.body<TodoListResponseContract>()
            assertSoftly(updated) {
                id shouldBe created.id
                name shouldBe "Updated"
            }
        }

        test("clears description when set to null") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List", description = "A description"))
            }.body<TodoListResponseContract>()

            val response = client.put(todoListUrl(created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoListRequestContract(name = "My List", description = null))
            }

            response.status shouldBe HttpStatusCode.OK
            response.body<TodoListResponseContract>().description shouldBe null
        }

        test("returns 422 for empty name") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Original"))
            }.body<TodoListResponseContract>()

            val response = client.put(todoListUrl(created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoListRequestContract(name = ""))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val problem = response.body<ProblemDetailsContract>()
            assertSoftly(problem) {
                status shouldBe HttpStatusCode.UnprocessableEntity.value
                errors?.get("name") shouldNotBe null
            }
        }

        test("returns 404 when todo list does not exist") {
            val client = createAuthenticatedTestClient()

            val response = client.put(todoListUrl(UUID.randomUUID())) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoListRequestContract(name = "Updated"))
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("DELETE /api/v1/todo-lists/{list-id}") {
        test("deletes a todo list") {
            val client = createAuthenticatedTestClient()

            val created = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "To delete"))
            }.body<TodoListResponseContract>()

            val deleteResponse = client.delete(todoListUrl(created.id))
            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val getResponse = client.get(todoListUrl(created.id))
            getResponse.status shouldBe HttpStatusCode.NotFound
        }

        test("cascade deletes todos within the list") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "List with todos"))
            }.body<TodoListResponseContract>()

            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Todo in list"))
            }

            val deleteResponse = client.delete(todoListUrl(list.id))
            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val listResponse = client.get(todoListsUrl())
            listResponse.body<ListTodoListsResponseContract>().data shouldHaveSize 0
        }

        test("returns 404 when todo list does not exist") {
            val client = createAuthenticatedTestClient()

            val response = client.delete(todoListUrl(UUID.randomUUID()))

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("POST /api/v1/todo-lists/{list-id}/todos") {
        test("creates a todo in a list") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "My Todo"))
            }

            response.status shouldBe HttpStatusCode.Created
            val todo = response.body<TodoResponseContract>()
            assertSoftly(todo) {
                name shouldBe "My Todo"
                completed shouldBe false
                completedAt shouldBe null
                createdBy shouldBe "test-user-id"
                id shouldNotBe null
                createdAt shouldNotBe null
                updatedAt shouldNotBe null
            }
        }

        test("returns 422 for empty todo name") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = ""))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

        test("returns 404 for unknown list") {
            val client = createAuthenticatedTestClient()

            val response = client.post(todoUrl(UUID.randomUUID())) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "My Todo"))
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("GET /api/v1/todo-lists/{list-id}/todos") {
        test("returns empty list when no todos exist in list") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "Empty List"))
            }.body<TodoListResponseContract>()

            val response = client.get(todoUrl(list.id))

            response.status shouldBe HttpStatusCode.OK
            response.body<ListTodosResponseContract>().data shouldHaveSize 0
        }

        test("returns todos in the list") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "First"))
            }
            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Second"))
            }

            val response = client.get(todoUrl(list.id))

            response.status shouldBe HttpStatusCode.OK
            response.body<ListTodosResponseContract>().data shouldHaveSize 2
        }

        test("completed=true returns only completed todos") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Task"))
            }.body<TodoResponseContract>()

            client.put(todoItemUrl(list.id, created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Task", completed = true))
            }
            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Incomplete task"))
            }

            val response = client.get(todoUrl(list.id)) {
                parameter("completed", "true")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 1
            body.data[0].name shouldBe "Task"
        }

        test("returns 404 for unknown list") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todoUrl(UUID.randomUUID()))

            response.status shouldBe HttpStatusCode.NotFound
        }

        test("todos are isolated from other lists") {
            val client = createAuthenticatedTestClient()

            val listA = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "List A"))
            }.body<TodoListResponseContract>()
            val listB = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "List B"))
            }.body<TodoListResponseContract>()

            client.post(todoUrl(listA.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "List A todo"))
            }

            val response = client.get(todoUrl(listB.id))

            response.status shouldBe HttpStatusCode.OK
            response.body<ListTodosResponseContract>().data shouldHaveSize 0
        }
    }

    context("GET /api/v1/todo-lists/{list-id}/todos/{todo-id}") {
        test("returns a todo by id") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "My Todo"))
            }.body<TodoResponseContract>()

            val response = client.get(todoItemUrl(list.id, created.id))

            response.status shouldBe HttpStatusCode.OK
            val todo = response.body<TodoResponseContract>()
            assertSoftly(todo) {
                id shouldBe created.id
                name shouldBe "My Todo"
                completed shouldBe false
            }
        }

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.get(todoItemUrl(list.id, UUID.randomUUID().toString()))

            response.status shouldBe HttpStatusCode.NotFound
        }

        test("returns 404 for unknown list") {
            val client = createAuthenticatedTestClient()

            val response = client.get(todoItemUrl(UUID.randomUUID().toString(), UUID.randomUUID().toString()))

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("PUT /api/v1/todo-lists/{list-id}/todos/{todo-id}") {
        test("updates name and marks complete") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Original"))
            }.body<TodoResponseContract>()

            val response = client.put(todoItemUrl(list.id, created.id)) {
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

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Original"))
            }.body<TodoResponseContract>()

            val response = client.put(todoItemUrl(list.id, created.id)) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "", completed = false))
            }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
        }

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.put(todoItemUrl(list.id, UUID.randomUUID().toString())) {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Updated", completed = false))
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("DELETE /api/v1/todo-lists/{list-id}/todos/{todo-id}") {
        test("deletes a todo") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "To delete"))
            }.body<TodoResponseContract>()

            val deleteResponse = client.delete(todoItemUrl(list.id, created.id))
            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val listResponse = client.get(todoUrl(list.id))
            listResponse.body<ListTodosResponseContract>().data shouldHaveSize 0
        }

        test("returns 404 when todo does not exist") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val response = client.delete(todoItemUrl(list.id, UUID.randomUUID().toString()))

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    context("user isolation") {
        test("listTodoLists only returns lists belonging to the authenticated user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            clientA.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User A - First"))
            }
            clientA.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User A - Second"))
            }
            clientB.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User B - First"))
            }

            val responseA = clientA.get(todoListsUrl())
            val responseB = clientB.get(todoListsUrl())

            responseA.status shouldBe HttpStatusCode.OK
            responseB.status shouldBe HttpStatusCode.OK
            responseA.body<ListTodoListsResponseContract>().data shouldHaveSize 2
            responseB.body<ListTodoListsResponseContract>().data shouldHaveSize 1
        }

        test("other user cannot access another user's list or its todos") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val list = clientA.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User A list"))
            }.body<TodoListResponseContract>()

            val todo = clientA.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User A todo"))
            }.body<TodoResponseContract>()

            clientB.get(todoListUrl(list.id)).status shouldBe HttpStatusCode.NotFound
            clientB.get(todoUrl(list.id)).status shouldBe HttpStatusCode.NotFound
            clientB.get(todoItemUrl(list.id, todo.id)).status shouldBe HttpStatusCode.NotFound
            clientB.delete(todoListUrl(list.id)).status shouldBe HttpStatusCode.NotFound
            clientB.delete(todoItemUrl(list.id, todo.id)).status shouldBe HttpStatusCode.NotFound
        }
    }

}) {
    companion object {
        private const val BASE_URL = "/api/v1/todo-lists"

        fun todoListsUrl() = BASE_URL
        fun todoListUrl(id: Any) = "$BASE_URL/$id"
        fun todoUrl(listId: Any) = "$BASE_URL/$listId/todos"
        fun todoItemUrl(listId: Any, todoId: Any) = "$BASE_URL/$listId/todos/$todoId"
    }
}
