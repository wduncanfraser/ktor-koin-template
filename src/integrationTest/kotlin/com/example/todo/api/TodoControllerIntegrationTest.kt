package com.example.todo.api

import com.example.IntegrationTestBase
import com.example.generated.api.models.CreateTodoListRequestContract
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoListResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import com.example.todolist.api.TodoListControllerIntegrationTest.Companion.todoListsUrl
import com.example.todolist.api.TodoListControllerIntegrationTest.Companion.todoUrl
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TodoControllerIntegrationTest : IntegrationTestBase({
    context("GET /api/v1/todos") {
        test("returns empty list when no todos exist") {
            val client = createAuthenticatedTestClient()

            val response = client.get(TODOS_URL)

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 0
        }

        test("returns todos across multiple lists") {
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
                setBody(CreateTodoRequestContract(name = "Todo in A"))
            }
            client.post(todoUrl(listB.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Todo in B"))
            }

            val response = client.get(TODOS_URL)

            response.status shouldBe HttpStatusCode.OK
            response.body<ListTodosResponseContract>().data shouldHaveSize 2
        }

        test("response includes todoListId") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "My Todo"))
            }

            val response = client.get(TODOS_URL)

            response.status shouldBe HttpStatusCode.OK
            val todo = response.body<ListTodosResponseContract>().data[0]
            assertSoftly(todo) {
                todoListId shouldBe list.id
                id shouldNotBe null
            }
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

            client.put("${todoUrl(list.id)}/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Task", completed = true))
            }
            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Incomplete"))
            }

            val response = client.get(TODOS_URL) {
                parameter("completed", "true")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 1
            body.data[0].name shouldBe "Task"
        }

        test("completed=false returns only incomplete todos") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            val created = client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Done"))
            }.body<TodoResponseContract>()

            client.put("${todoUrl(list.id)}/${created.id}") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTodoRequestContract(name = "Done", completed = true))
            }
            client.post(todoUrl(list.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "Not done"))
            }

            val response = client.get(TODOS_URL) {
                parameter("completed", "false")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ListTodosResponseContract>()
            body.data shouldHaveSize 1
            body.data[0].name shouldBe "Not done"
        }

        test("pageSize=1 returns one item with correct pagination metadata") {
            val client = createAuthenticatedTestClient()

            val list = client.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "My List"))
            }.body<TodoListResponseContract>()

            repeat(3) { i ->
                client.post(todoUrl(list.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "Todo $i"))
                }
            }

            val response = client.get(TODOS_URL) {
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

        test("returns 302 for login redirect without a session cookie") {
            val client = createTestClient { followRedirects = false }

            val response = client.get(TODOS_URL)

            response.status shouldBe HttpStatusCode.Found
            response.headers[HttpHeaders.Location] shouldBe "/login"
        }

        test("only returns todos belonging to the authenticated user") {
            val clientA = createAuthenticatedTestClient()
            val clientB = createAuthenticatedTestClient(secondTestSession())

            val listA = clientA.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User A list"))
            }.body<TodoListResponseContract>()
            val listB = clientB.post(todoListsUrl()) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoListRequestContract(name = "User B list"))
            }.body<TodoListResponseContract>()

            repeat(2) { i ->
                clientA.post(todoUrl(listA.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTodoRequestContract(name = "User A todo $i"))
                }
            }
            clientB.post(todoUrl(listB.id)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTodoRequestContract(name = "User B todo"))
            }

            val responseA = clientA.get(TODOS_URL)
            val responseB = clientB.get(TODOS_URL)

            responseA.body<ListTodosResponseContract>().data shouldHaveSize 2
            responseB.body<ListTodosResponseContract>().data shouldHaveSize 1
        }
    }
}) {
    companion object {
        private const val TODOS_URL = "/api/v1/todos"
    }
}
