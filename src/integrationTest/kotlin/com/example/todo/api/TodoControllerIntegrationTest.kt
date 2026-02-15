package com.example.todo.api

import com.example.IntegrationTestBase
import com.example.generated.api.models.CreateTodoRequestContract
import com.example.generated.api.models.ListTodosResponseContract
import com.example.generated.api.models.TodoResponseContract
import com.example.generated.api.models.UpdateTodoRequestContract
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

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
})
